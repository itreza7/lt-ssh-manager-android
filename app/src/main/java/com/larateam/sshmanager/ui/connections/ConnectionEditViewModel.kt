package com.larateam.sshmanager.ui.connections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larateam.sshmanager.data.model.AuthMethod
import com.larateam.sshmanager.data.model.Connection
import com.larateam.sshmanager.data.repo.ConnectionRepository
import com.larateam.sshmanager.data.repo.SecretRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionFormState(
    val id: Long = 0L,
    val name: String = "",
    val host: String = "",
    val port: String = Connection.DEFAULT_PORT.toString(),
    val username: String = "",
    val authMethod: AuthMethod = AuthMethod.KEY,
    val keyAlias: String = "",
    // Transient private-key paste for IN_APP_KEY import. Encrypted on save, never persisted plaintext.
    val keyMaterial: String = "",
    val hasStoredKey: Boolean = false,
    // Opt-in saved password (PASSWORD auth). Transient here; encrypted on save, never plaintext on disk.
    val password: String = "",
    val hasSavedPassword: Boolean = false,
    // Set only after the biometric gate succeeds; shown in a dialog, never written back to a field.
    val revealedKey: String? = null,
    val hostError: String? = null,
    val usernameError: String? = null,
    val portError: String? = null,
    val keyAliasError: String? = null,
    val isEditing: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class ConnectionEditViewModel @Inject constructor(
    private val repository: ConnectionRepository,
    private val secrets: SecretRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val connectionId: Long = savedStateHandle.get<Long>(ARG_CONNECTION_ID) ?: NEW_ID

    private val _form = MutableStateFlow(ConnectionFormState())
    val form: StateFlow<ConnectionFormState> = _form.asStateFlow()

    init {
        if (connectionId != NEW_ID) {
            viewModelScope.launch {
                repository.getConnection(connectionId)?.let { c ->
                    val stored = c.keyAlias?.takeIf { it.isNotBlank() }?.let { secrets.has(it) } ?: false
                    val savedPw = secrets.hasPassword(c.id)
                    _form.value = ConnectionFormState(
                        id = c.id,
                        name = c.name,
                        host = c.host,
                        port = c.port.toString(),
                        username = c.username,
                        authMethod = c.authMethod,
                        keyAlias = c.keyAlias.orEmpty(),
                        hasStoredKey = stored,
                        hasSavedPassword = savedPw,
                        isEditing = true,
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = _form.update { it.copy(name = value) }
    fun onHostChange(value: String) = _form.update { it.copy(host = value, hostError = null) }
    fun onUsernameChange(value: String) = _form.update { it.copy(username = value, usernameError = null) }
    fun onPortChange(value: String) =
        _form.update { it.copy(port = value.filter(Char::isDigit).take(5), portError = null) }
    fun onAuthMethodChange(value: AuthMethod) = _form.update { it.copy(authMethod = value) }
    fun onKeyAliasChange(value: String) = _form.update { it.copy(keyAlias = value, keyAliasError = null) }
    fun onKeyMaterialChange(value: String) = _form.update { it.copy(keyMaterial = value) }
    fun onPasswordChange(value: String) = _form.update { it.copy(password = value) }

    /** Forget a previously saved (encrypted) password for this connection. */
    fun removeSavedPassword() {
        val id = _form.value.id
        if (id == 0L) return
        viewModelScope.launch {
            secrets.deletePassword(id)
            _form.update { it.copy(hasSavedPassword = false, password = "") }
        }
    }

    fun save() {
        val current = _form.value
        val host = current.host.trim()
        val username = current.username.trim()
        val portNum = current.port.toIntOrNull()
        val importingKey = current.authMethod == AuthMethod.IN_APP_KEY && current.keyMaterial.isNotBlank()

        val hostError = if (host.isEmpty()) "Host is required" else null
        val usernameError = if (username.isEmpty()) "Username is required" else null
        val portError = when {
            portNum == null -> "Port is required"
            portNum !in Connection.MIN_PORT..Connection.MAX_PORT ->
                "Port must be ${Connection.MIN_PORT}–${Connection.MAX_PORT}"
            else -> null
        }
        val keyAliasError =
            if (importingKey && current.keyAlias.isBlank()) "Key name is required to store a key" else null

        if (hostError != null || usernameError != null || portError != null || keyAliasError != null) {
            _form.update {
                it.copy(
                    hostError = hostError,
                    usernameError = usernameError,
                    portError = portError,
                    keyAliasError = keyAliasError,
                )
            }
            return
        }

        // Only non-secret metadata is built here. The keyAlias is a reference, never key bytes.
        val connection = Connection(
            id = current.id,
            name = current.name.trim(),
            host = host,
            port = portNum!!,
            username = username,
            authMethod = current.authMethod,
            keyAlias = current.keyAlias.trim().ifBlank { null },
        )
        val savingPassword = current.authMethod == AuthMethod.PASSWORD && current.password.isNotBlank()
        viewModelScope.launch {
            val savedId = repository.save(connection)
            if (importingKey) {
                // Encrypt + persist ONLY ciphertext+IV, then drop the plaintext from form state.
                secrets.store(current.keyAlias.trim(), current.keyMaterial.encodeToByteArray())
            }
            if (savingPassword) {
                // Opt-in saved password: encrypt under the connection id; only ciphertext+IV hit disk.
                secrets.storePassword(savedId, current.password.toCharArray())
            }
            _form.update { it.copy(keyMaterial = "", password = "", saved = true) }
        }
    }

    /** Call ONLY after the biometric gate succeeds. Decrypts the stored key for display. */
    fun revealAfterAuth() {
        val ref = _form.value.keyAlias.trim()
        if (ref.isEmpty()) return
        viewModelScope.launch {
            val bytes = secrets.reveal(ref)
            _form.update { it.copy(revealedKey = bytes?.decodeToString() ?: "(no stored key)") }
        }
    }

    fun clearRevealed() = _form.update { it.copy(revealedKey = null) }

    companion object {
        const val ARG_CONNECTION_ID = "connectionId"
        const val NEW_ID = -1L
    }
}
