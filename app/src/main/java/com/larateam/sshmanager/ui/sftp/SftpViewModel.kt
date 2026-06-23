package com.larateam.sshmanager.ui.sftp

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.AuthMethod
import com.larateam.sshmanager.data.model.Connection
import com.larateam.sshmanager.data.model.ConnectionTarget
import com.larateam.sshmanager.data.repo.ConnectionRepository
import com.larateam.sshmanager.data.repo.SecretRepository
import com.larateam.sshmanager.data.repo.SessionStateRepository
import com.larateam.sshmanager.session.PersistedView
import com.larateam.sshmanager.session.ViewKind
import com.larateam.sshmanager.data.model.userMessage
import com.larateam.sshmanager.sftp.SftpEntry
import com.larateam.sshmanager.sftp.SftpException
import com.larateam.sshmanager.sftp.SftpPath
import com.larateam.sshmanager.sftp.TransferProgress
import com.larateam.sshmanager.ssh.ExecHoldResult
import com.larateam.sshmanager.ssh.SftpSession
import com.larateam.sshmanager.ssh.SshConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

enum class SortMode { NAME, SIZE, DATE }

data class SftpUiState(
    val title: String = "",
    val path: String = "",
    val entries: List<SftpEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val sort: SortMode = SortMode.NAME,
    val showHidden: Boolean = false,
    val transfers: List<TransferProgress> = emptyList(),
    val needsPassword: Boolean = false,
    val needsBiometric: Boolean = false,
)

@HiltViewModel
class SftpViewModel @Inject constructor(
    private val connectionsRepo: ConnectionRepository,
    private val secrets: SecretRepository,
    private val manager: SshConnectionManager,
    private val sessionRepo: SessionStateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val connectionId: Long = savedStateHandle.get<Long>("connectionId") ?: -1L

    private val _ui = MutableStateFlow(SftpUiState())
    val ui: StateFlow<SftpUiState> = _ui.asStateFlow()

    private var connection: Connection? = null
    private var target: ConnectionTarget? = null
    private var holdId: Long? = null
    private var sftp: SftpSession? = null
    // A copy held only between a "save password" submit and the connect result, then cleared.
    private var pendingSavePassword: CharArray? = null
    private var rawEntries: List<SftpEntry> = emptyList()
    private val transferMap = LinkedHashMap<Long, TransferProgress>()

    init {
        viewModelScope.launch {
            val conn = connectionsRepo.connections.first().firstOrNull { it.id == connectionId }
            if (conn == null) {
                _ui.update { it.copy(loading = false, error = "Connection not found") }
                return@launch
            }
            connection = conn
            target = ConnectionTarget(conn.host, conn.port, conn.username)
            _ui.update { it.copy(title = conn.displayName) }
            if (manager.isConnected(connectionId)) {
                acquireAndOpen(credentials = null)
            } else when (conn.authMethod) {
                // A saved password is unlocked via the biometric gate (like a stored key); otherwise prompt.
                AuthMethod.PASSWORD ->
                    if (secrets.hasPassword(connectionId)) _ui.update { it.copy(loading = false, needsBiometric = true) }
                    else _ui.update { it.copy(loading = false, needsPassword = true) }
                AuthMethod.IN_APP_KEY -> _ui.update { it.copy(loading = false, needsBiometric = true) }
                AuthMethod.KEY -> _ui.update { it.copy(loading = false, error = "KEY-file auth: external key import is a later phase.") }
            }
        }
    }

    fun submitPassword(password: CharArray, save: Boolean = false) {
        _ui.update { it.copy(needsPassword = false, loading = true) }
        pendingSavePassword = if (save) password.copyOf() else null
        acquireAndOpen(AuthCredentials.Password(password))
    }

    /** Called AFTER the biometric gate. Reveals the saved password OR the stored key, then connects. */
    fun submitStoredKeyAfterAuth() {
        _ui.update { it.copy(needsBiometric = false, loading = true) }
        val conn = connection
        if (conn == null) {
            _ui.update { it.copy(loading = false, error = "Connection not found") }
            return
        }
        viewModelScope.launch {
            when (conn.authMethod) {
                AuthMethod.PASSWORD -> {
                    val pw = secrets.revealPassword(connectionId)
                    if (pw == null) {
                        _ui.update { it.copy(loading = false, error = "No saved password for this connection.") }
                        return@launch
                    }
                    acquireAndOpen(AuthCredentials.Password(pw))
                }
                else -> {
                    val alias = conn.keyAlias
                    if (alias.isNullOrBlank()) {
                        _ui.update { it.copy(loading = false, error = "No key alias on this connection.") }
                        return@launch
                    }
                    val keyBytes = secrets.reveal(alias)
                    if (keyBytes == null) {
                        _ui.update { it.copy(loading = false, error = "No stored key for alias '$alias'.") }
                        return@launch
                    }
                    acquireAndOpen(AuthCredentials.PrivateKey(keyBytes))
                }
            }
        }
    }

    fun cancelPrompts() = _ui.update { it.copy(needsPassword = false, needsBiometric = false) }

    private fun acquireAndOpen(credentials: AuthCredentials?) {
        val t = target ?: return
        viewModelScope.launch {
            when (val r = manager.acquireExecConnection(connectionId, t, credentials)) {
                is ExecHoldResult.Ok -> {
                    holdId = r.holdId
                    // Persist an opt-in password ONLY now that auth has actually succeeded.
                    pendingSavePassword?.let { pw -> secrets.storePassword(connectionId, pw); pendingSavePassword = null }
                    val session = manager.openSftp(connectionId)
                    if (session == null) {
                        _ui.update { it.copy(loading = false, error = "Could not open SFTP channel") }
                        return@launch
                    }
                    sftp = session
                    // Restore the last browsed path if persisted; else resolve the start dir via realpath.
                    val savedPath = sessionRepo.load().views
                        .firstOrNull { it.viewId == PersistedView.sftpViewId(connectionId) }?.arg
                    val start = savedPath ?: runCatching { session.canonicalize(".") }.getOrDefault("/")
                    navigateTo(start)
                }
                is ExecHoldResult.Fail -> {
                    pendingSavePassword?.fill(' '); pendingSavePassword = null
                    _ui.update { it.copy(loading = false, error = r.error.userMessage()) }
                }
            }
        }
    }

    fun navigateTo(path: String) {
        val session = sftp ?: return
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { session.list(path) }
                .onSuccess {
                    rawEntries = it
                    _ui.update { s -> s.copy(path = path, loading = false, error = null) }
                    refreshDisplay()
                    // Persist this open SFTP view + its current path (metadata only, §4).
                    sessionRepo.upsert(PersistedView(PersistedView.sftpViewId(connectionId), connectionId, ViewKind.SFTP, path))
                }
                .onFailure { _ui.update { s -> s.copy(loading = false, error = "Can't open $path: ${friendly(it)}") } }
        }
    }

    fun up() = navigateTo(SftpPath.parent(_ui.value.path))
    fun refresh() = navigateTo(_ui.value.path)

    fun open(entry: SftpEntry) {
        if (entry.isDirectory || entry.isSymlink) navigateTo(entry.path)
        // FILE taps are handled by the screen (launches SAF CreateDocument -> download).
    }

    fun setSort(mode: SortMode) { _ui.update { it.copy(sort = mode) }; refreshDisplay() }
    fun toggleHidden() { _ui.update { it.copy(showHidden = !it.showHidden) }; refreshDisplay() }

    private fun refreshDisplay() {
        val sort = _ui.value.sort
        val showHidden = _ui.value.showHidden
        val filtered = if (showHidden) rawEntries else rawEntries.filter { !it.isHidden }
        val cmp: Comparator<SftpEntry> = when (sort) {
            SortMode.NAME -> compareBy { it.name.lowercase() }
            SortMode.SIZE -> compareByDescending { it.size }
            SortMode.DATE -> compareByDescending { it.mtimeEpochSec }
        }
        val ordered = filtered.sortedWith(compareByDescending<SftpEntry> { it.isDirectory }.then(cmp))
        _ui.update { it.copy(entries = ordered) }
    }

    // --- File ops (metadata channel, serialized in SftpSession) ---

    fun mkdir(name: String) = metadataOp("Create folder") { it.mkdir(SftpPath.join(_ui.value.path, name)) }
    fun rename(entry: SftpEntry, newName: String) =
        metadataOp("Rename") { it.rename(entry.path, SftpPath.join(SftpPath.parent(entry.path), newName)) }
    fun chmod(entry: SftpEntry, mode: Int) = metadataOp("chmod") { it.chmod(entry.path, mode) }
    fun delete(entry: SftpEntry) = metadataOp("Delete") { it.deleteRecursive(entry.path) }

    private fun metadataOp(label: String, op: suspend (SftpSession) -> Unit) {
        val session = sftp ?: return
        viewModelScope.launch {
            runCatching { op(session) }
                .onSuccess { navigateTo(_ui.value.path) } // refresh listing
                .onFailure { e -> _ui.update { it.copy(error = "$label failed: ${friendly(e)}") } }
        }
    }

    /** Classified, user-readable message for an SFTP failure (never a raw stack trace). */
    private fun friendly(t: Throwable): String =
        (t as? SftpException)?.reason?.userMessage() ?: t.message ?: "Operation failed"

    fun clearError() = _ui.update { it.copy(error = null) }

    // --- Transfers (own channel, FGS, on the manager's app scope) ---

    fun upload(name: String, total: Long, source: InputStream) {
        val remote = SftpPath.join(_ui.value.path, name)
        manager.startUpload(connectionId, remote, name, total, source) { p -> onProgress(p, refreshOnDone = true) }
    }

    fun download(entry: SftpEntry, sink: OutputStream) {
        manager.startDownload(connectionId, entry.path, entry.name, sink) { p -> onProgress(p) }
    }

    fun cancelTransfer(id: Long) = manager.cancelTransfer(id)

    fun dismissTransfer(id: Long) {
        transferMap.remove(id)
        publishTransfers()
    }

    private fun onProgress(p: TransferProgress, refreshOnDone: Boolean = false) {
        transferMap[p.id] = p
        publishTransfers()
        if (p.done && refreshOnDone) viewModelScope.launch { navigateTo(_ui.value.path) }
    }

    private fun publishTransfers() {
        val list = transferMap.values.toList()
        _ui.update { it.copy(transfers = list) }
    }

    override fun onCleared() {
        // Close the pooled metadata channel (off-main — sshj close is blocking) + release the
        // (non-FGS) hold. Active transfers keep running on the app scope: they hold their OWN consumer
        // + FGS session until they finish, so the connection survives until the transfer completes.
        val s = sftp
        Thread { runCatching { s?.close() } }.start()
        holdId?.let { manager.releaseExecConnection(connectionId, it) }
    }
}
