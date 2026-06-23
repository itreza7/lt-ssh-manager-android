package com.larateam.sshmanager.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larateam.sshmanager.data.model.ConnState
import com.larateam.sshmanager.data.repo.ConnectionRepository
import com.larateam.sshmanager.data.repo.SessionStateRepository
import com.larateam.sshmanager.session.ViewKind
import com.larateam.sshmanager.terminal.TerminalSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One restorable session row for the UI. */
data class SessionItem(
    val viewId: Long,
    val connectionId: Long,
    val kind: ViewKind,
    val title: String,
    val subtitle: String,
    val state: ConnState,
)

/**
 * The restore surface: lists persisted open views (terminals / dashboards / SFTP) — restored LAZILY as
 * Disconnected. Terminals reflect the live store state; tapping routes to the right screen, which
 * re-resolves auth and reconnects.
 */
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessionRepo: SessionStateRepository,
    private val connectionsRepo: ConnectionRepository,
    private val store: TerminalSessionStore,
) : ViewModel() {

    val items: StateFlow<List<SessionItem>> =
        combine(sessionRepo.sessions, store.tabs, connectionsRepo.connections) { sessions, tabs, conns ->
            val byId = conns.associateBy { it.id }
            sessions.views.map { v ->
                val name = byId[v.connectionId]?.displayName ?: "connection ${v.connectionId}"
                val subtitle = when (v.kind) {
                    ViewKind.SHELL -> "Terminal"
                    ViewKind.TMUX -> "tmux: ${v.arg}"
                    ViewKind.DASHBOARD -> "Dashboard"
                    ViewKind.SFTP -> "SFTP — ${v.arg ?: "/"}"
                }
                val state = if (v.kind == ViewKind.SHELL || v.kind == ViewKind.TMUX) {
                    tabs.firstOrNull { it.tabId == v.viewId }?.state?.value ?: ConnState.Disconnected
                } else {
                    ConnState.Disconnected
                }
                SessionItem(v.viewId, v.connectionId, v.kind, name, subtitle, state)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Forget a saved view (and close its terminal tab if it's a terminal). */
    fun remove(item: SessionItem) {
        if (item.kind == ViewKind.SHELL || item.kind == ViewKind.TMUX) store.closeTab(item.viewId)
        else viewModelScope.launch { sessionRepo.remove(item.viewId) }
    }
}
