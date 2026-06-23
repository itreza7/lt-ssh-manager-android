package com.larateam.sshmanager.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larateam.sshmanager.dashboard.DashboardProbe
import com.larateam.sshmanager.dashboard.DashboardVitals
import com.larateam.sshmanager.dashboard.TmuxParser
import com.larateam.sshmanager.dashboard.TmuxSession
import com.larateam.sshmanager.dashboard.VitalsParser
import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.AuthMethod
import com.larateam.sshmanager.data.model.Connection
import com.larateam.sshmanager.data.model.ConnectionTarget
import com.larateam.sshmanager.data.model.userMessage
import com.larateam.sshmanager.data.repo.ConnectionRepository
import com.larateam.sshmanager.data.repo.SecretRepository
import com.larateam.sshmanager.data.repo.SessionStateRepository
import com.larateam.sshmanager.session.PersistedView
import com.larateam.sshmanager.session.ViewKind
import com.larateam.sshmanager.ssh.ExecHoldResult
import com.larateam.sshmanager.ssh.SshConnectionManager
import com.larateam.sshmanager.terminal.TerminalSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class DashboardUiState(
    val title: String = "",
    val connecting: Boolean = true,
    val refreshing: Boolean = false,
    val vitals: DashboardVitals? = null,
    val tmux: List<TmuxSession> = emptyList(),
    val asOf: String? = null,
    val error: String? = null,
    val needsPassword: Boolean = false,
    val needsBiometric: Boolean = false,
)

/**
 * Per-connection dashboard. Holds a NON-FGS exec hold on the host's shared connection (reusing an
 * already-authenticated one — no re-auth) and runs the one-shot probe + `tmux ls` over exec channels.
 * Attaching a tmux session opens a Phase 6 terminal tab (a real PTY) on the same shared connection.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val connectionsRepo: ConnectionRepository,
    private val secrets: SecretRepository,
    private val manager: SshConnectionManager,
    private val store: TerminalSessionStore,
    private val sessionRepo: SessionStateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val connectionId: Long = savedStateHandle.get<Long>("connectionId") ?: -1L

    private val _ui = MutableStateFlow(DashboardUiState())
    val ui: StateFlow<DashboardUiState> = _ui.asStateFlow()

    private var connection: Connection? = null
    private var target: ConnectionTarget? = null
    private var holdId: Long? = null
    // A copy held only between a "save password" submit and the connect result, then cleared.
    private var pendingSavePassword: CharArray? = null

    init {
        viewModelScope.launch {
            val conn = connectionsRepo.connections.first().firstOrNull { it.id == connectionId }
            if (conn == null) {
                _ui.update { it.copy(connecting = false, error = "Connection not found") }
                return@launch
            }
            connection = conn
            target = ConnectionTarget(conn.host, conn.port, conn.username)
            _ui.update { it.copy(title = conn.displayName) }
            // Reuse the live connection if a terminal already has it up (no prompt); else authenticate.
            if (manager.isConnected(connectionId)) {
                acquireAndProbe(credentials = null)
            } else when (conn.authMethod) {
                // A saved password is unlocked via the biometric gate (like a stored key); otherwise prompt.
                AuthMethod.PASSWORD ->
                    if (secrets.hasPassword(connectionId)) _ui.update { it.copy(connecting = false, needsBiometric = true) }
                    else _ui.update { it.copy(connecting = false, needsPassword = true) }
                AuthMethod.IN_APP_KEY -> _ui.update { it.copy(connecting = false, needsBiometric = true) }
                AuthMethod.KEY -> _ui.update { it.copy(connecting = false, error = "KEY-file auth: external key import is a later phase.") }
            }
        }
    }

    fun submitPassword(password: CharArray, save: Boolean = false) {
        _ui.update { it.copy(needsPassword = false, connecting = true) }
        pendingSavePassword = if (save) password.copyOf() else null
        acquireAndProbe(AuthCredentials.Password(password))
    }

    /** Called AFTER the biometric gate. Reveals the saved password OR the stored key, then connects. */
    fun submitStoredKeyAfterAuth() {
        _ui.update { it.copy(needsBiometric = false, connecting = true) }
        val conn = connection
        if (conn == null) {
            _ui.update { it.copy(connecting = false, error = "Connection not found") }
            return
        }
        viewModelScope.launch {
            when (conn.authMethod) {
                AuthMethod.PASSWORD -> {
                    val pw = secrets.revealPassword(connectionId)
                    if (pw == null) {
                        _ui.update { it.copy(connecting = false, error = "No saved password for this connection.") }
                        return@launch
                    }
                    acquireAndProbe(AuthCredentials.Password(pw))
                }
                else -> {
                    val alias = conn.keyAlias
                    if (alias.isNullOrBlank()) {
                        _ui.update { it.copy(connecting = false, error = "No key alias on this connection.") }
                        return@launch
                    }
                    val keyBytes = secrets.reveal(alias)
                    if (keyBytes == null) {
                        _ui.update { it.copy(connecting = false, error = "No stored key for alias '$alias'.") }
                        return@launch
                    }
                    acquireAndProbe(AuthCredentials.PrivateKey(keyBytes))
                }
            }
        }
    }

    fun cancelPrompts() = _ui.update { it.copy(needsPassword = false, needsBiometric = false) }

    private fun acquireAndProbe(credentials: AuthCredentials?) {
        val t = target ?: return
        viewModelScope.launch {
            when (val r = manager.acquireExecConnection(connectionId, t, credentials)) {
                is ExecHoldResult.Ok -> {
                    holdId = r.holdId
                    // Persist an opt-in password ONLY now that auth has actually succeeded.
                    pendingSavePassword?.let { pw -> secrets.storePassword(connectionId, pw); pendingSavePassword = null }
                    _ui.update { it.copy(connecting = false) }
                    // Persist this open dashboard view (metadata only, §4) so it's restorable on launch.
                    sessionRepo.upsert(PersistedView(PersistedView.dashboardViewId(connectionId), connectionId, ViewKind.DASHBOARD))
                    runProbe()
                }
                is ExecHoldResult.Fail -> {
                    pendingSavePassword?.fill(' '); pendingSavePassword = null
                    _ui.update { it.copy(connecting = false, error = r.error.userMessage()) }
                }
            }
        }
    }

    fun refresh() {
        if (holdId == null) return
        _ui.update { it.copy(refreshing = true) }
        viewModelScope.launch { runProbe() }
    }

    private suspend fun runProbe() {
        val probe = runCatching { manager.exec(connectionId, DashboardProbe.COMMAND) }.getOrNull()
        val tmux = runCatching { manager.exec(connectionId, "tmux ls 2>/dev/null") }.getOrNull()
        val vitals = probe?.let { VitalsParser.parse(it.stdout) }
        val sessions = if (tmux != null && tmux.exitCode == 0) TmuxParser.parse(tmux.stdout) else emptyList()
        _ui.update {
            it.copy(
                vitals = vitals,
                tmux = sessions,
                asOf = nowHHmm(),
                refreshing = false,
                error = if (vitals == null) "Probe failed" else null,
            )
        }
    }

    /** Open a plain terminal tab on the shared connection (no re-auth). */
    fun openPlainTerminal() {
        val t = target ?: return
        store.openTab(connectionId, t, credentials = null, title = _ui.value.title)
    }

    /** Open a terminal tab attached to a tmux session on the shared connection (re-attachable on reconnect). */
    fun attachTmux(name: String) {
        val t = target ?: return
        store.openTab(connectionId, t, credentials = null, title = "tmux: $name", tmuxName = name)
    }

    override fun onCleared() {
        // Release the (non-FGS) hold; the connection closes if no terminal channel still holds it.
        holdId?.let { manager.releaseExecConnection(connectionId, it) }
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun nowHHmm(): String = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }
}
