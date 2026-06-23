package com.larateam.sshmanager.ssh

import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.ConnState
import com.larateam.sshmanager.data.model.ConnectionTarget
import com.larateam.sshmanager.data.model.HostKeyInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.io.CharArrayReader
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Owns ONE [SSHClient] and its lifecycle. All sshj work runs on [io] (blocking; never main).
 * Resilient connect: transient failures retry with jittered backoff; permanent ones (bad auth,
 * missing key, changed host key) fail fast. State is exposed as a [StateFlow] for the UI.
 *
 * Designed as a plain component so the Phase 4 foreground service can own it.
 */
class SshConnection(
    private val target: ConnectionTarget,
    private val knownHosts: KnownHostStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMs: Int = 15_000,
    maxAttempts: Int = 5,
    backoff: Backoff = Backoff(),
) : SshSession {
    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    override val state: StateFlow<ConnState> = _state.asStateFlow()

    private val retrier = Retrier(backoff, maxAttempts, SshErrorClassifier::isPermanent)

    @Volatile
    private var client: SSHClient? = null

    @Volatile
    private var lastHostKey: HostKeyInfo? = null

    override val isConnected: Boolean get() = client?.isConnected == true && client?.isAuthenticated == true

    override suspend fun connect(credentials: AuthCredentials) = withContext(io) {
        _state.value = ConnState.Connecting
        try {
            retrier.run(onRetry = { _, _ -> _state.value = ConnState.Connecting }) {
                doConnect(credentials)
            }
            _state.value = ConnState.Connected(lastHostKey ?: HostKeyInfo("(unknown)", false))
        } catch (t: Throwable) {
            val error = SshErrorClassifier.classify(t)
            _state.value = ConnState.Error(error, t.message ?: error.name)
            safeClose()
        } finally {
            credentials.clear()
        }
    }

    override suspend fun exec(command: String): String = withContext(io) {
        val c = client ?: error("Not connected")
        c.startSession().use { session ->
            val cmd = session.exec(command)
            val output = IOUtils.readFully(cmd.inputStream).toString()
            cmd.join(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            output
        }
    }

    /** One exec channel multiplexed over this client; captures stdout, stderr and the exit status. */
    override suspend fun execCapturing(command: String): ExecResult = withContext(io) {
        val c = client ?: error("Not connected")
        c.startSession().use { session ->
            val cmd = session.exec(command)
            val out = IOUtils.readFully(cmd.inputStream).toString()
            val err = IOUtils.readFully(cmd.errorStream).toString()
            cmd.join(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            ExecResult(out, err, cmd.exitStatus ?: -1)
        }
    }

    override suspend fun execStream(command: String, onLine: (String) -> Unit) = withContext(io) {
        val c = client ?: error("Not connected")
        c.startSession().use { session ->
            val cmd = session.exec(command)
            cmd.inputStream.bufferedReader().use { reader ->
                while (isActive) {
                    val line = reader.readLine() ?: break
                    onLine(line)
                }
            }
        }
    }

    /** Opens an SFTP subsystem CHANNEL multiplexed over this client (no re-auth). */
    suspend fun openSftp(): SftpSession = withContext(io) {
        val c = client ?: error("Not connected")
        SftpSession(c.newSFTPClient(), io)
    }

    /** Opens an interactive shell CHANNEL on an xterm-256color PTY, multiplexed over this client. */
    override suspend fun openShell(columns: Int, rows: Int): ShellIo = withContext(io) {
        val c = client ?: error("Not connected")
        val session = c.startSession()
        session.allocatePTY("xterm-256color", columns, rows, 0, 0, emptyMap())
        val shell = session.startShell()
        SshShellChannel(session, shell)
    }

    override suspend fun disconnect() = withContext(io) {
        safeClose()
        _state.value = ConnState.Disconnected
    }

    private fun doConnect(credentials: AuthCredentials) {
        safeClose()
        val verifier = TofuHostKeyVerifier(knownHosts) { fp, first -> lastHostKey = HostKeyInfo(fp, first) }
        // HEARTBEAT keepalive sends SSH_MSG_IGNORE on a cadence; a dead/half-open peer (e.g. a
        // WiFi<->cellular switch) surfaces as a write failure instead of a hung read — without the
        // KEEP_ALIVE provider's false drops when round-trips merely lag on a high-latency link.
        val c = SSHClient(DefaultConfig().apply { keepAliveProvider = KeepAliveProvider.HEARTBEAT })
        c.connectTimeout = connectTimeoutMs
        c.timeout = connectTimeoutMs
        c.addHostKeyVerifier(verifier)
        c.connect(target.host, target.port)
        try {
            when (credentials) {
                is AuthCredentials.Password ->
                    c.authPassword(target.username, passwordFinder(credentials.password))

                is AuthCredentials.PrivateKey ->
                    c.authPublickey(target.username, loadKeyProvider(c, credentials))
            }
        } catch (t: Throwable) {
            runCatching { c.disconnect() }
            throw t
        }
        c.connection.keepAlive.keepAliveInterval = KEEPALIVE_INTERVAL_SEC
        client = c
    }

    private fun loadKeyProvider(client: SSHClient, creds: AuthCredentials.PrivateKey): KeyProvider {
        val chars = decodeAscii(creds.keyBytes)
        try {
            val format = CharArrayReader(chars).use { KeyProviderUtil.detectKeyFileFormat(it, false) }
            val factory = client.transport.config.fileKeyProviderFactories
                .firstOrNull { it.name.equals(format.name, ignoreCase = true) || it.name.equals(format.toString(), ignoreCase = true) }
                ?: throw MissingKeyException("Unsupported private key format: $format")
            val provider = factory.create() as FileKeyProvider
            val pwdf = creds.passphrase?.let { passwordFinder(it) }
            provider.init(CharArrayReader(chars), null, pwdf)
            // sshj reads the key lazily; force an eager parse so the KeyPair is cached BEFORE we
            // wipe the backing char[] in finally (otherwise auth reads a zeroed buffer).
            provider.getPublic()
            provider.getPrivate()
            return provider
        } finally {
            chars.fill(' ')
        }
    }

    private fun passwordFinder(secret: CharArray): PasswordFinder = object : PasswordFinder {
        override fun reqPassword(resource: Resource<*>?): CharArray = secret.copyOf()
        override fun shouldRetry(resource: Resource<*>?): Boolean = false
    }

    private fun decodeAscii(bytes: ByteArray): CharArray {
        val buffer = Charsets.US_ASCII.newDecoder().decode(ByteBuffer.wrap(bytes))
        val chars = CharArray(buffer.remaining())
        buffer.get(chars)
        return chars
    }

    private fun safeClose() {
        runCatching { client?.disconnect() }
        client = null
    }

    private companion object {
        /** sshj keepalive cadence (seconds); a dead peer is detected after a few missed replies. */
        const val KEEPALIVE_INTERVAL_SEC = 5
    }
}
