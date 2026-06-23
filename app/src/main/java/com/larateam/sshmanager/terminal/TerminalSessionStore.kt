package com.larateam.sshmanager.terminal

import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.ConnState
import com.larateam.sshmanager.data.model.ConnectionTarget
import com.larateam.sshmanager.data.repo.ConnectionRepository
import com.larateam.sshmanager.data.repo.SessionStateRepository
import com.larateam.sshmanager.session.PersistedView
import com.larateam.sshmanager.session.ReconnectPolicy
import com.larateam.sshmanager.session.ViewKind
import com.larateam.sshmanager.ssh.ShellIo
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * THE owner of terminal tab sessions — sits ABOVE the pager (Phase 6 central rule). Each tab's
 * [TerminalSession] + emulator + reader thread live here, not in a pager page's composition.
 *
 * Phase 11: tab METADATA (connection, SHELL vs TMUX, tmux session name) is persisted (no credentials,
 * §4) and restored LAZILY on launch as Disconnected tabs. A dropped connection (detected via sshj
 * keepalive → channel EOF) is distinguished from an explicit shell `exit`, and is auto-reconnected on
 * the next network-available event with no re-prompt (the manager retains the credential in memory).
 */
@Singleton
class TerminalSessionStore internal constructor(
    private val gateway: TerminalGateway,
    private val terminalFactory: (ShellIo, SshTerminalSessionClient) -> TerminalSession,
    private val mainDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    // Nullable so the instrumented store test can construct without the persistence graph.
    private val sessionRepo: SessionStateRepository? = null,
    private val connectionsRepo: ConnectionRepository? = null,
) {
    @Inject
    constructor(
        gateway: TerminalGateway,
        sessionRepo: SessionStateRepository,
        connectionsRepo: ConnectionRepository,
    ) : this(
        gateway = gateway,
        terminalFactory = { channel, client ->
            TerminalSession(channel.inputStream, channel.outputStream, TRANSCRIPT_ROWS, client) { c, r, w, h ->
                channel.resize(c, r, w, h)
            }
        },
        mainDispatcher = Dispatchers.Main,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        sessionRepo = sessionRepo,
        connectionsRepo = connectionsRepo,
    )

    /**
     * One terminal tab = a CHANNEL on a (possibly shared) host connection. [terminalSession] /
     * [channelId] are null while connecting, on connect failure, or for a restored-but-not-yet-
     * reconnected tab. [wasConnected] gates auto-reconnect: only tabs that were live (then dropped)
     * auto-reconnect on a network change — freshly-restored tabs wait for an explicit tap (lazy).
     */
    data class Tab(
        val tabId: Long,
        val connectionId: Long,
        val title: String,
        val terminalSession: TerminalSession?,
        val channelId: Long?,
        val client: SshTerminalSessionClient,
        val state: StateFlow<ConnState>,
        val kind: ViewKind = ViewKind.SHELL,
        val tmuxName: String? = null,
        val target: ConnectionTarget? = null,
        val wasConnected: Boolean = false,
    )

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    /** Active-session count (drives the foreground-service notification "N active sessions"). */
    val activeCount: StateFlow<Int> get() = gateway.activeCount

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** One-shot user-facing messages (e.g. connect failures). */
    val events: SharedFlow<String> = _events

    private val states = HashMap<Long, MutableStateFlow<ConnState>>()
    private val nextTabId = AtomicLong(1_000_000_000L) // distinct range from Room connection ids

    init {
        if (sessionRepo != null && connectionsRepo != null) scope.launch { restore() }
    }

    fun isConnected(connectionId: Long): Boolean = gateway.isConnected(connectionId)

    /**
     * Open a new tab for [connectionId]. Pass [credentials] = null for an already-connected host.
     * [tmuxName] non-null marks a tmux-attach tab (kind TMUX) — the store runs an attach-or-create on
     * open AND on every reconnect, so the same named session is re-attached if alive.
     */
    fun openTab(
        connectionId: Long,
        target: ConnectionTarget,
        credentials: AuthCredentials?,
        title: String,
        tmuxName: String? = null,
        onConnected: (() -> Unit)? = null,
    ) {
        val tabId = nextTabId.getAndIncrement()
        val kind = if (tmuxName != null) ViewKind.TMUX else ViewKind.SHELL
        val stateFlow = MutableStateFlow<ConnState>(ConnState.Connecting)
        val client = SshTerminalSessionClient(onFinished = { onShellFinished(tabId) })
        synchronized(states) { states[tabId] = stateFlow }
        _tabs.update {
            it + Tab(tabId, connectionId, title, null, null, client, stateFlow.asStateFlow(), kind, tmuxName, target)
        }
        persist(PersistedView(tabId, connectionId, kind, tmuxName))
        // tmuxName MUST flow through so the attach-or-create command is sent on first open (not only on
        // reconnect) — otherwise a dashboard "attach" lands in a plain shell instead of the tmux session.
        scope.launch { connectChannel(tabId, connectionId, target, credentials, stateFlow, client, tmuxName, onConnected = onConnected) }
    }

    /**
     * Reconnect a restored or dropped tab. [credentials] = null reuses the manager's retained credential
     * (seamless network-drop recovery); a non-null credential is the freshly re-resolved one after a
     * launch (the retained copy is gone with the process). TOFU still applies — a changed host key fails.
     */
    fun reconnect(tabId: Long, credentials: AuthCredentials?) {
        val tab = _tabs.value.firstOrNull { it.tabId == tabId } ?: return
        val target = tab.target ?: return
        val stateFlow = synchronized(states) { states[tabId] } ?: return
        if (stateFlow.value is ConnState.Connecting) return
        val oldChannelId = tab.channelId
        val client = SshTerminalSessionClient(onFinished = { onShellFinished(tabId) })
        stateFlow.value = ConnState.Connecting
        _tabs.update { list -> list.map { if (it.tabId == tabId) it.copy(client = client) else it } }
        scope.launch {
            connectChannel(tabId, tab.connectionId, target, credentials, stateFlow, client, tab.tmuxName, oldChannelId)
        }
    }

    /** Auto-reconnect tabs that were live and then DROPPED (network change). Restored-only tabs wait. */
    fun reconnectDropped() {
        scope.launch {
            for (tab in _tabs.value) {
                val st = synchronized(states) { states[tab.tabId] }?.value
                if (tab.wasConnected && st !is ConnState.Connected && st !is ConnState.Connecting &&
                    !gateway.isConnected(tab.connectionId)
                ) {
                    reconnect(tab.tabId, credentials = null) // retained credential → no prompt
                }
            }
        }
    }

    private suspend fun connectChannel(
        tabId: Long,
        connectionId: Long,
        target: ConnectionTarget,
        credentials: AuthCredentials?,
        stateFlow: MutableStateFlow<ConnState>,
        client: SshTerminalSessionClient,
        tmuxName: String? = null,
        oldChannelId: Long? = null,
        onConnected: (() -> Unit)? = null,
    ) {
        when (val result = gateway.open(connectionId, target, credentials, INITIAL_COLS, INITIAL_ROWS)) {
            is GatewayResult.Fail -> {
                stateFlow.value = ConnState.Error(result.error, result.message)
                _events.tryEmit("${titleOf(tabId)}: ${result.message}")
            }
            is GatewayResult.Ok -> {
                val terminal = terminalFactory(result.channel, client)
                withContext(mainDispatcher) {
                    terminal.initializeEmulator(INITIAL_COLS, INITIAL_ROWS, INITIAL_CELL_W, INITIAL_CELL_H)
                }
                if (tmuxName != null) {
                    val cmd = ReconnectPolicy.tmuxReconnectCommand(tmuxName, alive = true) +
                        " || " + ReconnectPolicy.tmuxReconnectCommand(tmuxName, alive = false)
                    val bytes = (cmd + "\n").toByteArray()
                    terminal.write(bytes, 0, bytes.size)
                }
                // New channel now holds the connection → safe to release the old (dead) one.
                oldChannelId?.let { gateway.release(it) }
                _tabs.update { list ->
                    list.map {
                        if (it.tabId == tabId) it.copy(terminalSession = terminal, channelId = result.channelId, wasConnected = true)
                        else it
                    }
                }
                stateFlow.value = ConnState.Connected(result.hostKey)
                onConnected?.invoke() // e.g. persist an opt-in password only AFTER auth actually succeeded
            }
        }
    }

    private fun titleOf(tabId: Long): String = _tabs.value.firstOrNull { it.tabId == tabId }?.title ?: "terminal"

    /**
     * The shell channel ended. If the host connection is still up, the shell itself exited (`exit`) —
     * release the channel. If the connection is DOWN, this is a network drop — keep the channel/consumer
     * and the retained credential so [reconnectDropped] can auto-recover; just mark Disconnected.
     */
    private fun onShellFinished(tabId: Long) {
        val tab = _tabs.value.firstOrNull { it.tabId == tabId } ?: return
        scope.launch {
            delay(150) // let a transport-close settle into isConnected before classifying
            val alive = gateway.isConnected(tab.connectionId)
            synchronized(states) { states[tabId] }?.value = ConnState.Disconnected
            if (alive) tab.channelId?.let { gateway.release(it) } // shell exited; connection stays for others
            // else: dropped — keep everything for auto-reconnect.
        }
    }

    fun terminalSession(tabId: Long): TerminalSession? =
        _tabs.value.firstOrNull { it.tabId == tabId }?.terminalSession

    /** Explicit user close: forget the persisted view + release the channel (clears retained creds if last). */
    fun closeTab(tabId: Long) {
        val tab = _tabs.value.firstOrNull { it.tabId == tabId } ?: return
        _tabs.update { list -> list.filterNot { it.tabId == tabId } }
        synchronized(states) { states.remove(tabId) }
        sessionRepo?.let { repo -> scope.launch { repo.remove(tabId) } }
        val terminal = tab.terminalSession
        val channelId = tab.channelId
        scope.launch {
            withContext(mainDispatcher) { terminal?.finishIfRunning() }
            channelId?.let { gateway.release(it) }
        }
    }

    fun disconnectAll() {
        val all = _tabs.value
        _tabs.value = emptyList()
        synchronized(states) { states.clear() }
        scope.launch {
            sessionRepo?.let { repo -> all.forEach { repo.remove(it.tabId) } }
            for (tab in all) {
                withContext(mainDispatcher) { tab.terminalSession?.finishIfRunning() }
                tab.channelId?.let { gateway.release(it) }
            }
        }
    }

    /** Restore persisted terminal tabs as Disconnected (lazy — no auto-connect, §11). */
    private suspend fun restore() {
        val sessions = sessionRepo?.load() ?: return
        val terminalViews = sessions.views.filter { it.kind == ViewKind.SHELL || it.kind == ViewKind.TMUX }
        if (terminalViews.isEmpty()) return
        val connsById = connectionsRepo?.connections?.first()?.associateBy { it.id } ?: return
        val restored = terminalViews.mapNotNull { v ->
            val conn = connsById[v.connectionId] ?: return@mapNotNull null
            val stateFlow = MutableStateFlow<ConnState>(ConnState.Disconnected)
            synchronized(states) { states[v.viewId] = stateFlow }
            val client = SshTerminalSessionClient(onFinished = { onShellFinished(v.viewId) })
            val title = if (v.kind == ViewKind.TMUX) "tmux: ${v.arg}" else conn.displayName
            Tab(
                tabId = v.viewId, connectionId = v.connectionId, title = title,
                terminalSession = null, channelId = null, client = client, state = stateFlow.asStateFlow(),
                kind = v.kind, tmuxName = v.arg,
                target = ConnectionTarget(conn.host, conn.port, conn.username), wasConnected = false,
            )
        }
        // Avoid tabId collisions: bump the generator above any restored id.
        restored.maxOfOrNull { it.tabId }?.let { max -> if (nextTabId.get() <= max) nextTabId.set(max + 1) }
        _tabs.update { live -> restored.filter { r -> live.none { it.tabId == r.tabId } } + live }
    }

    private fun persist(view: PersistedView) {
        sessionRepo?.let { repo -> scope.launch { repo.upsert(view) } }
    }

    companion object {
        const val INITIAL_COLS = 80
        const val INITIAL_ROWS = 24
        const val INITIAL_CELL_W = 10
        const val INITIAL_CELL_H = 20
        const val TRANSCRIPT_ROWS = 4000
    }
}
