package com.larateam.sshmanager.ssh

import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.ConnError
import com.larateam.sshmanager.data.model.ConnState
import com.larateam.sshmanager.data.model.ConnectionTarget
import kotlinx.coroutines.test.runTest
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.server.Environment
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.StringWriter
import java.security.PrivateKey
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.Base64

/**
 * Hermetic SSH integration test: the real [SshConnection]/sshj client against an in-process
 * Apache MINA SSHD server. Exercises auth + TOFU + the changed-key block deterministically,
 * with no external host.
 */
class SshIntegrationTest {

    private class FakeStore : KnownHostStore {
        val pins = mutableMapOf<String, String>()
        override fun pinnedFingerprint(host: String, port: Int) = pins["$host:$port"]
        override fun pin(host: String, port: Int, fingerprintSha256: String) {
            pins["$host:$port"] = fingerprintSha256
        }
    }

    private class CannedCommand(private val response: String) : Command {
        private lateinit var out: OutputStream
        private lateinit var err: OutputStream
        private lateinit var exit: ExitCallback
        override fun setInputStream(input: InputStream) {}
        override fun setOutputStream(output: OutputStream) { out = output }
        override fun setErrorStream(error: OutputStream) { err = error }
        override fun setExitCallback(callback: ExitCallback) { exit = callback }
        override fun start(channel: ChannelSession, env: Environment) {
            out.write(response.toByteArray()); out.flush(); exit.onExit(0)
        }
        override fun destroy(channel: ChannelSession) {}
    }

    private lateinit var sshd: SshServer
    private var port: Int = 0
    private lateinit var clientPublicKey: PublicKey
    private lateinit var clientPrivateKeyPem: ByteArray

    @Before
    fun setUp() {
        SshProvider.ensureRegistered()

        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        clientPublicKey = kp.public
        // PKCS#1 ("BEGIN RSA PRIVATE KEY"): BC's PEMParser yields a key PAIR, so sshj can derive
        // and offer the public key (PKCS#8 "BEGIN PRIVATE KEY" carries no public key for sshj).
        clientPrivateKeyPem = pkcs1Pem(kp.private)

        sshd = SshServer.setUpDefaultServer().apply {
            this.port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider()
            setPasswordAuthenticator { user, password, _ -> user == "demo" && password == "password" }
            setPublickeyAuthenticator { user, key, _ -> user == "keyuser" && KeyUtils.compareKeys(key, clientPublicKey) }
            userAuthFactories = listOf(UserAuthPublicKeyFactory.INSTANCE, UserAuthPasswordFactory.INSTANCE)
            commandFactory = CommandFactory { _, _ -> CannedCommand("Linux mina-test 6.8.0 x86_64 GNU/Linux\n") }
            start()
        }
        port = sshd.port
    }

    @After
    fun tearDown() {
        sshd.stop(true)
    }

    private fun connection(user: String, store: KnownHostStore = FakeStore()) =
        SshConnection(ConnectionTarget("127.0.0.1", port, user), store) to store

    private fun pkcs1Pem(priv: PrivateKey): ByteArray {
        val pkcs1 = PrivateKeyInfo.getInstance(priv.encoded).parsePrivateKey().toASN1Primitive().encoded
        val writer = StringWriter()
        PemWriter(writer).use { it.writeObject(PemObject("RSA PRIVATE KEY", pkcs1)) }
        return writer.toString().toByteArray()
    }

    @Test
    fun password_auth_connects_pins_host_and_execs() = runTest {
        val (conn, store) = connection("demo")
        conn.connect(AuthCredentials.Password("password".toCharArray()))

        val state = conn.state.value
        assertTrue("expected Connected, was $state", state is ConnState.Connected)
        assertTrue((state as ConnState.Connected).hostKey.firstContact)
        assertTrue(store.pinnedFingerprint("127.0.0.1", port)!!.startsWith("SHA256:"))

        val output = conn.exec("uname -a")
        assertTrue("got: $output", output.contains("Linux"))
        conn.disconnect()
    }

    @Test
    fun publickey_auth_connects() = runTest {
        val (conn, _) = connection("keyuser")
        conn.connect(AuthCredentials.PrivateKey(clientPrivateKeyPem.copyOf()))
        val st = conn.state.value
        assertTrue("expected Connected, was $st", st is ConnState.Connected)
        conn.disconnect()
    }

    @Test
    fun wrong_password_fails_fast_as_auth_error() = runTest {
        val (conn, _) = connection("demo")
        conn.connect(AuthCredentials.Password("wrong".toCharArray()))
        val state = conn.state.value
        assertTrue(state is ConnState.Error)
        assertEquals(ConnError.AUTH_FAILED, (state as ConnState.Error).error)
    }

    @Test
    fun changed_host_key_blocks_connection() = runTest {
        // Pre-pin a different fingerprint for this host:port -> the real key must be rejected.
        val store = FakeStore().apply { pins["127.0.0.1:$port"] = "SHA256:bogusPinnedValueThatWillNotMatch" }
        val conn = SshConnection(ConnectionTarget("127.0.0.1", port, "demo"), store)
        conn.connect(AuthCredentials.Password("password".toCharArray()))

        val state = conn.state.value
        assertTrue("expected Error, was $state", state is ConnState.Error)
        assertEquals(ConnError.HOST_KEY_CHANGED, (state as ConnState.Error).error)
        // The pin must NOT have been overwritten.
        assertEquals("SHA256:bogusPinnedValueThatWillNotMatch", store.pinnedFingerprint("127.0.0.1", port))
    }
}
