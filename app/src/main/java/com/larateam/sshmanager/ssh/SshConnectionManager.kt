package com.larateam.sshmanager.ssh

import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.ConnError
import com.larateam.sshmanager.data.model.ConnState
import com.larateam.sshmanager.data.model.ConnectionTarget
import com.larateam.sshmanager.data.model.HostKeyInfo
import com.larateam.sshmanager.data.repo.KnownHostsRepository
import com.larateam.sshmanager.sftp.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Result of opening a shell channel: a channel id + handle, or a classified failure. */
sealed interface ShellOpenResult {
    data class Opened(val channelId: Long, val channel: ShellIo, val hostKey: HostKeyInfo) : ShellOpenResult
    data class Failed(val error: ConnError, val message: String) : ShellOpenResult
}

/** Result of acquiring a (non-FGS) exec hold on a host connection: a hold id, or a failure. */
sealed interface ExecHoldResult {
    data class Ok(val holdId: Long, val hostKey: HostKeyInfo) : ExecHoldResult
    data class Fail(val error: ConnError, val message: String) : ExecHoldResult
}

/**
 * Owns at most ONE live [SshSession] (SSHClient) per connection profile (host), keyed by connection
 * id. Channels — terminal shells now, dashboard exec + SFTP later — are multiplexed over that one
 * client and authentication happens ONCE per connection.
 *
 * The connection is reference-counted by its consumers (each open shell channel; a debug "hold").
 * It stays up while it has >= 1 consumer and is disconnected when the last one releases it.
 *
 * Connection coroutines run on [appScope] (application-lifetime), NOT a viewModelScope — that is what
 * lets sessions survive the UI going away (Phase 4). The foreground service runs while [activeCount]
 * (the number of open channels / sessions) > 0 and is started via [serviceController] on 0 -> 1.
 *
 * The primary constructor is for tests; Hilt uses the @Inject constructor.
 */
@Singleton
class SshConnectionManager(
    private val knownHosts: KnownHostStore,
    serviceController: ForegroundServiceController,
    private val appScope: CoroutineScope,
    private val sessionFactory: (ConnectionTarget) -> SshSession,
) {
    @Inject
    constructor(
        knownHosts: KnownHostsRepository,
        serviceController: ForegroundServiceController,
    ) : this(
        knownHosts = knownHosts,
        serviceController = serviceController,
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        sessionFactory = { target -> SshConnection(target, knownHosts) },
    )

    /**
     * A live connection plus the set of consumer ids keeping it open (channel ids + debug holds).
     * [credentials] is the resolved auth retained IN MEMORY for the logical session so an auto-reconnect
     * re-authenticates with no prompt (§4: never persisted; cleared on the last consumer release).
     * [target] lets a dropped connection re-establish itself.
     */
    private class ConnEntry(
        val session: SshSession,
        val target: ConnectionTarget,
        var credentials: AuthCredentials?,
    ) {
        val consumers = LinkedHashSet<Long>()
    }

    private val connections = ConcurrentHashMap<Long, ConnEntry>()  // connectionId -> entry
    private val channelToConn = ConcurrentHashMap<Long, Long>()      // channelId -> connectionId
    private val channelHandles = ConcurrentHashMap<Long, ShellIo>()  // channelId -> channel handle
    private val connectMutexes = ConcurrentHashMap<Long, Mutex>()
    private val nextChannelId = AtomicLong(1)

    private val tracker = ActiveSessionTracker(onBecameActive = serviceController::start)

    /** Number of active channels/sessions; the foreground service observes this. */
    val activeCount: StateFlow<Int> = tracker.count

    private val _streamOutput = MutableStateFlow("")
    val streamOutput: StateFlow<String> = _streamOutput.asStateFlow()

    @Volatile
    private var streamJob: Job? = null

    private fun mutexFor(connectionId: Long): Mutex = connectMutexes.getOrPut(connectionId) { Mutex() }

    /** Debug-hold ref id for a connection; negative so it never collides with positive channel ids. */
    private fun debugRef(connectionId: Long): Long = -(connectionId + 1)

    fun isConnected(connectionId: Long): Boolean = connections[connectionId]?.session?.isConnected == true

    fun session(connectionId: Long): SshSession? = connections[connectionId]?.session

    private sealed interface Ensure {
        data class Ok(val entry: ConnEntry) : Ensure
        data class Fail(val error: ConnError, val message: String) : Ensure
    }

    /** Reuse the live connection if up; otherwise connect + authenticate ONCE. Caller holds the mutex. */
    private suspend fun ensureConnectedLocked(
        connectionId: Long,
        target: ConnectionTarget,
        credentials: AuthCredentials?,
    ): Ensure {
        connections[connectionId]?.let { existing ->
            if (existing.session.isConnected) return Ensure.Ok(existing) // already authenticated — no re-auth
            // Reconnect a dropped connection: a fresh prompt-cred replaces the retained one; otherwise
            // reuse the retained cred so an auto-reconnect needs no prompt. A copy is passed (sshj clears it).
            if (credentials != null) { existing.credentials?.clear(); existing.credentials = credentials }
            val cred = existing.credentials ?: return Ensure.Fail(ConnError.UNKNOWN, "No credentials to reconnect")
            runCatching { existing.session.connect(cred.copy()) }
            return if (existing.session.isConnected) Ensure.Ok(existing) else failFrom(existing.session)
        }
        if (credentials == null) return Ensure.Fail(ConnError.UNKNOWN, "Connection not established")
        val session = sessionFactory(target)
        runCatching { session.connect(credentials.copy()) } // copy is cleared by sshj; retain the original
        if (!session.isConnected) {
            runCatching { session.disconnect() }
            credentials.clear()
            return failFrom(session)
        }
        val entry = ConnEntry(session, target, credentials)
        connections[connectionId] = entry
        return Ensure.Ok(entry)
    }

    /** Drop + fully clear a connection: disconnect (off-main), remove, and wipe the retained credential (§4). */
    private fun clearAndRemove(connectionId: Long, entry: ConnEntry) {
        connections.remove(connectionId, entry)
        entry.credentials?.clear()
        entry.credentials = null
        appScope.launch { runCatching { entry.session.disconnect() } }
    }

    private fun failFrom(session: SshSession): Ensure.Fail {
        val st = session.state.value as? ConnState.Error
        return Ensure.Fail(st?.error ?: ConnError.UNKNOWN, st?.message ?: "Connect failed")
    }

    /**
     * Ensure the host connection is up (auth once) and open a new shell channel on it. A second tab to
     * an already-connected host opens instantly with no re-authentication (pass [credentials] = null).
     */
    suspend fun openShell(
        connectionId: Long,
        target: ConnectionTarget,
        credentials: AuthCredentials?,
        columns: Int,
        rows: Int,
    ): ShellOpenResult {
        val entry = when (val e = mutexFor(connectionId).withLock { ensureConnectedLocked(connectionId, target, credentials) }) {
            is Ensure.Fail -> return ShellOpenResult.Failed(e.error, e.message)
            is Ensure.Ok -> e.entry
        }
        val hostKey = (entry.session.state.value as? ConnState.Connected)?.hostKey ?: HostKeyInfo("(unknown)", false)
        val channel = runCatching { entry.session.openShell(columns, rows) }.getOrNull()
            ?: return ShellOpenResult.Failed(ConnError.UNKNOWN, "Shell open failed")
        val channelId = nextChannelId.getAndIncrement()
        synchronized(entry.consumers) { entry.consumers.add(channelId) }
        channelToConn[channelId] = connectionId
        channelHandles[channelId] = channel
        tracker.activate(channelId)
        return ShellOpenResult.Opened(channelId, channel, hostKey)
    }

    /** Release one channel. Closes the channel; disconnects the host client only on the LAST consumer. */
    fun releaseChannel(channelId: Long) {
        val connectionId = channelToConn.remove(channelId) ?: return
        tracker.deactivate(channelId)
        val handle = channelHandles.remove(channelId)
        val entry = connections[connectionId]
        val isLast = entry != null && synchronized(entry.consumers) {
            entry.consumers.remove(channelId)
            entry.consumers.isEmpty()
        }
        appScope.launch {
            runCatching { handle?.close() } // close the channel off-main (blocking socket I/O)
            if (isLast && entry != null) clearAndRemove(connectionId, entry) // explicit close → wipe credential
        }
    }

    /**
     * Acquire a NON-FGS consumer hold on a host connection for transient exec use (the dashboard).
     * Reuses an already-authenticated connection (no re-auth); establishes + auths once otherwise.
     * Unlike a shell channel or debug hold, this does NOT touch [tracker] — a dashboard exec is
     * transient and must not keep the foreground service alive on its own. The hold keeps the
     * connection from closing while the dashboard is open; [releaseExecConnection] drops it.
     */
    suspend fun acquireExecConnection(
        connectionId: Long,
        target: ConnectionTarget,
        credentials: AuthCredentials?,
    ): ExecHoldResult {
        val entry = when (val e = mutexFor(connectionId).withLock { ensureConnectedLocked(connectionId, target, credentials) }) {
            is Ensure.Fail -> return ExecHoldResult.Fail(e.error, e.message)
            is Ensure.Ok -> e.entry
        }
        val holdId = nextChannelId.getAndIncrement()
        synchronized(entry.consumers) { entry.consumers.add(holdId) } // ref-count only, NOT an FGS session
        val hostKey = (entry.session.state.value as? ConnState.Connected)?.hostKey ?: HostKeyInfo("(unknown)", false)
        return ExecHoldResult.Ok(holdId, hostKey)
    }

    fun releaseExecConnection(connectionId: Long, holdId: Long) {
        val entry = connections[connectionId] ?: return
        val isLast = synchronized(entry.consumers) {
            entry.consumers.remove(holdId)
            entry.consumers.isEmpty()
        }
        if (isLast) appScope.launch { clearAndRemove(connectionId, entry) } // explicit close → wipe credential
    }

    /** Run a one-shot command on the host's existing connection (capturing stdout/stderr/exit). */
    suspend fun exec(connectionId: Long, command: String): ExecResult? =
        connections[connectionId]?.session?.execCapturing(command)

    // --- SFTP (Phase 8): channels multiplexed over the shared connection -----------------------

    /** Open a new SFTP channel on the host's existing connection (no re-auth). */
    suspend fun openSftp(connectionId: Long): SftpSession? =
        (connections[connectionId]?.session as? SshConnection)?.openSftp()

    private val transfers = ConcurrentHashMap<Long, Job>()
    private val nextTransferId = AtomicLong(1)

    /** Distinct tracker/consumer-id range for transfers (positive, above channel/hold ids). */
    private fun transferKey(id: Long): Long = 5_000_000_000L + id

    fun startUpload(
        connectionId: Long,
        remotePath: String,
        name: String,
        total: Long,
        source: java.io.InputStream,
        onProgress: (TransferProgress) -> Unit,
    ): Long = startTransfer(connectionId, name, isUpload = true, onProgress) { sftp, report ->
        source.use { sftp.upload(remotePath, it) { bytes -> report(bytes, total) } }
    }

    fun startDownload(
        connectionId: Long,
        remotePath: String,
        name: String,
        sink: java.io.OutputStream,
        onProgress: (TransferProgress) -> Unit,
    ): Long = startTransfer(connectionId, name, isUpload = false, onProgress) { sftp, report ->
        sink.use {
            val total = runCatching { sftp.size(remotePath) }.getOrDefault(-1L)
            report(0, total)
            sftp.download(remotePath, it) { bytes -> report(bytes, total) }
        }
    }

    fun cancelTransfer(id: Long) {
        transfers[id]?.cancel()
    }

    /**
     * A transfer runs on [appScope] (survives backgrounding) over its OWN SFTP channel, and counts as
     * BOTH an FGS session (so the OS keeps the process while it runs) AND a connection consumer (so
     * the host connection stays up even if the browser leaves). Both are released on completion/cancel.
     */
    private fun startTransfer(
        connectionId: Long,
        name: String,
        isUpload: Boolean,
        onProgress: (TransferProgress) -> Unit,
        block: suspend (SftpSession, report: (Long, Long) -> Unit) -> Unit,
    ): Long {
        val id = nextTransferId.getAndIncrement()
        val key = transferKey(id)
        tracker.activate(key) // FGS for the transfer's duration
        connections[connectionId]?.let { synchronized(it.consumers) { it.consumers.add(key) } }
        onProgress(TransferProgress(id, name, isUpload, 0, -1))

        val job = appScope.launch {
            var lastBytes = 0L
            var lastTotal = -1L
            val sftp = openSftp(connectionId)
            try {
                if (sftp == null) {
                    onProgress(TransferProgress(id, name, isUpload, error = "Not connected"))
                    return@launch
                }
                block(sftp) { bytes, total ->
                    lastBytes = bytes; lastTotal = total
                    onProgress(TransferProgress(id, name, isUpload, bytes, total))
                }
                onProgress(TransferProgress(id, name, isUpload, lastBytes, lastTotal, done = true))
            } catch (c: CancellationException) {
                onProgress(TransferProgress(id, name, isUpload, lastBytes, lastTotal, cancelled = true))
                throw c
            } catch (e: Throwable) {
                onProgress(TransferProgress(id, name, isUpload, lastBytes, lastTotal, error = e.message ?: "transfer failed"))
            } finally {
                sftp?.close()
                tracker.deactivate(key)
                transfers.remove(id)
                val entry = connections[connectionId]
                val isLast = entry != null && synchronized(entry.consumers) {
                    entry.consumers.remove(key); entry.consumers.isEmpty()
                }
                if (isLast && entry != null) clearAndRemove(connectionId, entry)
            }
        }
        transfers[id] = job
        return id
    }

    /** Debug/dev hold: keep a host connection open (no shell) so exec/stream survive backgrounding. */
    fun connect(connectionId: Long, target: ConnectionTarget, credentials: AuthCredentials) {
        val entry = connections.getOrPut(connectionId) { ConnEntry(sessionFactory(target), target, credentials) }
        val ref = debugRef(connectionId)
        val added = synchronized(entry.consumers) { entry.consumers.add(ref) }
        if (!added) return
        tracker.activate(ref)
        appScope.launch {
            mutexFor(connectionId).withLock {
                if (!entry.session.isConnected) runCatching { entry.session.connect((entry.credentials ?: credentials).copy()) }
            }
        }
        // Release the debug hold once the connection reaches a terminal state (failed or dropped).
        appScope.launch {
            entry.session.state.first { it is ConnState.Connecting || it is ConnState.Connected }
            entry.session.state.first { it is ConnState.Error || it is ConnState.Disconnected }
            releaseDebugHold(connectionId, ref)
        }
    }

    private fun releaseDebugHold(connectionId: Long, ref: Long) {
        tracker.deactivate(ref)
        val entry = connections[connectionId] ?: return
        val empty = synchronized(entry.consumers) {
            entry.consumers.remove(ref)
            entry.consumers.isEmpty()
        }
        if (empty) appScope.launch { clearAndRemove(connectionId, entry) }
    }

    /** Streams a long-running command's stdout on [appScope] (survives backgrounding). */
    fun startStream(connectionId: Long, command: String) {
        val session = connections[connectionId]?.session ?: return
        streamJob?.cancel()
        _streamOutput.value = ""
        streamJob = appScope.launch {
            runCatching {
                session.execStream(command) { line ->
                    _streamOutput.update { (it + line + "\n").takeLast(4_000) }
                }
            }
        }
    }

    fun stopStream() {
        streamJob?.cancel()
        streamJob = null
    }

    /** Debug disconnect: release the debug hold; disconnects the host only if it was the last consumer. */
    suspend fun disconnect(connectionId: Long) {
        stopStream()
        val ref = debugRef(connectionId)
        tracker.deactivate(ref)
        val entry = connections[connectionId] ?: return
        val isLast = synchronized(entry.consumers) {
            entry.consumers.remove(ref)
            entry.consumers.isEmpty()
        }
        if (isLast) clearAndRemove(connectionId, entry)
    }

    suspend fun disconnectAll() {
        stopStream()
        val entries = connections.values.toList()
        connections.clear()
        channelToConn.clear()
        val handles = channelHandles.values.toList()
        channelHandles.clear()
        tracker.clear()
        handles.forEach { runCatching { it.close() } }
        entries.forEach {
            it.credentials?.clear() // explicit teardown → wipe retained credentials (§4)
            it.credentials = null
            runCatching { it.session.disconnect() }
        }
    }
}
