package com.larateam.sshmanager.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.AuthMethod
import com.larateam.sshmanager.data.model.Connection
import com.larateam.sshmanager.data.model.ConnectionTarget
import com.larateam.sshmanager.data.model.AppSettings
import com.larateam.sshmanager.data.repo.ConnectionRepository
import com.larateam.sshmanager.data.repo.SecretRepository
import com.larateam.sshmanager.data.repo.SettingsRepository
import com.larateam.sshmanager.terminal.TerminalSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalTabsViewModel @Inject constructor(
    private val connectionsRepo: ConnectionRepository,
    private val secrets: SecretRepository,
    private val store: TerminalSessionStore,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun markBatteryPromptDismissed() = viewModelScope.launch { settingsRepo.setBatteryPromptDismissed(true) }

    /** Persist an in-terminal font resize (pinch / volume keys) so it survives and seeds the next session. */
    fun setTerminalFontSize(sp: Int) = viewModelScope.launch { settingsRepo.setTerminalFontSize(sp) }

    val tabs: StateFlow<List<TerminalSessionStore.Tab>> = store.tabs
    val events: SharedFlow<String> = store.events

    private val _connections = MutableStateFlow<List<Connection>>(emptyList())
    val connections: StateFlow<List<Connection>> = _connections.asStateFlow()

    /** When non-null, the UI shows the connection picker. */
    private val _showPicker = MutableStateFlow(false)
    val showPicker: StateFlow<Boolean> = _showPicker.asStateFlow()

    /** Set when a picked PASSWORD connection needs a connect-time password (never stored). */
    private val _pendingPassword = MutableStateFlow<Connection?>(null)
    val pendingPassword: StateFlow<Connection?> = _pendingPassword.asStateFlow()

    /** Set when a picked IN_APP_KEY connection needs the biometric gate before revealing the key. */
    private val _pendingBiometric = MutableStateFlow<Connection?>(null)
    val pendingBiometric: StateFlow<Connection?> = _pendingBiometric.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Non-null while the pending credential prompt is for RECONNECTING this tab (not opening a new one). */
    private var reconnectTabId: Long? = null

    init {
        viewModelScope.launch { connectionsRepo.connections.collect { list -> _connections.update { list } } }
        viewModelScope.launch { store.events.collect { m -> _message.value = m } }
    }

    fun openPicker() { _showPicker.value = true }
    fun dismissPicker() { _showPicker.value = false }
    fun clearMessage() { _message.value = null }

    fun pickConnection(c: Connection) {
        _showPicker.value = false
        // Auth happens ONCE per host: if the connection is already up, open another channel with no
        // credentials and no prompt (the new tab shares the existing authenticated SSHClient).
        if (store.isConnected(c.id)) {
            store.openTab(c.id, ConnectionTarget(c.host, c.port, c.username), credentials = null, title = c.displayName)
            return
        }
        when (c.authMethod) {
            AuthMethod.PASSWORD -> routePasswordOrBiometric(c)
            AuthMethod.IN_APP_KEY -> _pendingBiometric.value = c
            AuthMethod.KEY -> _message.value = "KEY-file auth: external key import is a later phase."
        }
    }

    /** PASSWORD auth: a saved (encrypted) password goes through the biometric gate; otherwise prompt. */
    private fun routePasswordOrBiometric(c: Connection) {
        viewModelScope.launch {
            if (secrets.hasPassword(c.id)) _pendingBiometric.value = c else _pendingPassword.value = c
        }
    }

    fun cancelPrompts() {
        _pendingPassword.value = null
        _pendingBiometric.value = null
        reconnectTabId = null
    }

    /**
     * Reconnect a restored/dropped tab. If the host is already up (another tab revived it), reconnect
     * with no prompt; otherwise re-resolve auth (the retained credential is gone after a process restart).
     */
    fun reconnect(tab: TerminalSessionStore.Tab) {
        if (store.isConnected(tab.connectionId)) { store.reconnect(tab.tabId, null); return }
        val c = _connections.value.firstOrNull { it.id == tab.connectionId } ?: return
        reconnectTabId = tab.tabId
        when (c.authMethod) {
            AuthMethod.PASSWORD -> routePasswordOrBiometric(c)
            AuthMethod.IN_APP_KEY -> _pendingBiometric.value = c
            AuthMethod.KEY -> { reconnectTabId = null; _message.value = "KEY-file auth: external key import is a later phase." }
        }
    }

    /** PASSWORD path. If [save], the password is encrypted and stored ONLY after auth succeeds. */
    fun addWithPassword(c: Connection, password: CharArray, save: Boolean = false) {
        _pendingPassword.value = null
        val onConnected: (() -> Unit)? = if (save) {
            val copy = password.copyOf()
            val action: () -> Unit = { viewModelScope.launch { secrets.storePassword(c.id, copy) } }
            action
        } else {
            null
        }
        applyCredentials(c, AuthCredentials.Password(password), onConnected)
    }

    /** Call AFTER the biometric gate succeeds. Reveals the saved password OR the stored key. */
    fun addWithStoredKeyAfterAuth(c: Connection) {
        _pendingBiometric.value = null
        viewModelScope.launch {
            when (c.authMethod) {
                AuthMethod.PASSWORD -> {
                    val pw = secrets.revealPassword(c.id)
                    if (pw == null) {
                        _message.value = "No saved password for this connection."
                        return@launch
                    }
                    applyCredentials(c, AuthCredentials.Password(pw))
                }
                else -> {
                    val alias = c.keyAlias
                    if (alias.isNullOrBlank()) {
                        _message.value = "No key alias on this connection."
                        return@launch
                    }
                    val keyBytes = secrets.reveal(alias)
                    if (keyBytes == null) {
                        _message.value = "No stored key for alias '$alias'."
                        return@launch
                    }
                    applyCredentials(c, AuthCredentials.PrivateKey(keyBytes))
                }
            }
        }
    }

    /** Route resolved credentials to a reconnect (if a tab is pending) or a fresh tab. */
    private fun applyCredentials(c: Connection, credentials: AuthCredentials, onConnected: (() -> Unit)? = null) {
        val rid = reconnectTabId
        reconnectTabId = null
        if (rid != null) {
            store.reconnect(rid, credentials)
            onConnected?.invoke() // reconnect lacks a success hook; persist opportunistically (rare path)
        } else {
            store.openTab(c.id, ConnectionTarget(c.host, c.port, c.username), credentials, c.displayName, onConnected = onConnected)
        }
    }

    fun closeTab(tabId: Long) = store.closeTab(tabId)

    fun disconnectAll() = store.disconnectAll()

    fun terminalSession(tabId: Long) = store.terminalSession(tabId)
}
