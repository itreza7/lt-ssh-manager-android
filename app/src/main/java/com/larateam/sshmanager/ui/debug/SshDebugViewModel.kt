package com.larateam.sshmanager.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.AuthMethod
import com.larateam.sshmanager.data.model.ConnState
import com.larateam.sshmanager.data.model.Connection
import com.larateam.sshmanager.data.model.ConnectionTarget
import com.larateam.sshmanager.data.repo.ConnectionRepository
import com.larateam.sshmanager.data.repo.KnownHostsRepository
import com.larateam.sshmanager.data.repo.SecretRepository
import com.larateam.sshmanager.ssh.SshConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebugUiState(
    val connections: List<Connection> = emptyList(),
    val selectedId: Long? = null,
    val conn: ConnState = ConnState.Disconnected,
    val activeCount: Int = 0,
    /** Set when a PASSWORD connection needs a connect-time password (never stored). */
    val needsPassword: Boolean = false,
    /** Set when an IN_APP_KEY connection needs the biometric gate before revealing the key. */
    val needsBiometric: Boolean = false,
    val output: String = "",
    val stream: String = "",
    val log: List<String> = emptyList(),
) {
    val selected: Connection? get() = connections.firstOrNull { it.id == selectedId }
}

@HiltViewModel
class SshDebugViewModel @Inject constructor(
    private val connections: ConnectionRepository,
    private val secrets: SecretRepository,
    private val knownHosts: KnownHostsRepository,
    private val manager: SshConnectionManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(DebugUiState())
    val ui: StateFlow<DebugUiState> = _ui.asStateFlow()

    private var stateJob: Job? = null

    init {
        viewModelScope.launch {
            connections.connections.collect { list -> _ui.update { it.copy(connections = list) } }
        }
        // Display-only mirrors (UI-scoped). The connection itself lives on the manager's app scope.
        viewModelScope.launch { manager.streamOutput.collect { s -> _ui.update { it.copy(stream = s) } } }
        viewModelScope.launch { manager.activeCount.collect { n -> _ui.update { it.copy(activeCount = n) } } }
    }

    fun select(id: Long) {
        _ui.update { it.copy(selectedId = id, conn = ConnState.Disconnected, output = "", stream = "") }
        observeState(id)
    }

    private fun log(line: String) = _ui.update { it.copy(log = (it.log + line).takeLast(20)) }

    fun onConnectClicked() {
        val c = _ui.value.selected ?: return
        when (c.authMethod) {
            AuthMethod.PASSWORD -> _ui.update { it.copy(needsPassword = true) }
            AuthMethod.IN_APP_KEY -> _ui.update { it.copy(needsBiometric = true) }
            AuthMethod.KEY -> log("KEY-file auth: external key import is a later phase.")
        }
    }

    fun cancelPrompts() = _ui.update { it.copy(needsPassword = false, needsBiometric = false) }

    /** PASSWORD path: the password is used transiently and never persisted. */
    fun connectWithPassword(password: CharArray) {
        _ui.update { it.copy(needsPassword = false) }
        connect(AuthCredentials.Password(password))
    }

    /** IN_APP_KEY path: call AFTER the biometric gate succeeds. Decrypts the stored key. */
    fun connectWithStoredKeyAfterAuth() {
        _ui.update { it.copy(needsBiometric = false) }
        val c = _ui.value.selected ?: return
        val alias = c.keyAlias
        if (alias.isNullOrBlank()) {
            log("No key alias on this connection.")
            return
        }
        viewModelScope.launch {
            val keyBytes = secrets.reveal(alias)
            if (keyBytes == null) {
                log("No stored key for alias '$alias'.")
                return@launch
            }
            connect(AuthCredentials.PrivateKey(keyBytes))
        }
    }

    private fun connect(credentials: AuthCredentials) {
        val c = _ui.value.selected ?: return
        log("Connecting to ${c.endpoint} ...")
        // Runs on the manager's application scope — survives this ViewModel/UI going away.
        manager.connect(c.id, ConnectionTarget(c.host, c.port, c.username), credentials)
        observeState(c.id)
    }

    /** Display-only state mirror (viewModelScope). NOT the connection's own coroutine. */
    private fun observeState(id: Long) {
        stateJob?.cancel()
        val session = manager.session(id) ?: return
        stateJob = viewModelScope.launch {
            session.state.collect { s ->
                _ui.update { it.copy(conn = s) }
                when (s) {
                    is ConnState.Connected ->
                        log((if (s.hostKey.firstContact) "Trusted NEW host key " else "Host key verified ") + s.hostKey.fingerprintSha256)
                    is ConnState.Error -> log("Error: ${s.error} — ${s.message}")
                    else -> {}
                }
            }
        }
    }

    fun runUname() {
        val c = _ui.value.selected ?: return
        val session = manager.session(c.id) ?: return
        viewModelScope.launch {
            runCatching { session.exec("uname -a") }
                .onSuccess { out -> _ui.update { it.copy(output = out) }; log("ran: uname -a") }
                .onFailure { log("exec failed: ${it.message}") }
        }
    }

    /** Starts a long-running streaming command on the manager's app scope (survives backgrounding). */
    fun startStream() {
        val c = _ui.value.selected ?: return
        manager.startStream(c.id, "tail -f /var/log/syslog")
        log("streaming: tail -f …")
    }

    fun disconnect() {
        val c = _ui.value.selected ?: return
        viewModelScope.launch {
            manager.disconnect(c.id)
            log("Disconnected.")
        }
    }

    fun simulateKeyChange() {
        val c = _ui.value.selected ?: return
        viewModelScope.launch {
            knownHosts.tamperFingerprint(c.host, c.port)
            log("Tampered pinned key for ${c.host}:${c.port} — reconnect should be BLOCKED.")
        }
    }

    fun forgetHostKey() {
        val c = _ui.value.selected ?: return
        viewModelScope.launch {
            knownHosts.forget(c.host, c.port)
            log("Forgot pinned key for ${c.host}:${c.port}.")
        }
    }
}
