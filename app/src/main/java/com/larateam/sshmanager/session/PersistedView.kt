package com.larateam.sshmanager.session

/** What kind of open view was persisted. */
enum class ViewKind { SHELL, TMUX, DASHBOARD, SFTP }

/**
 * Metadata for one restorable open view. Holds NO credentials (§4) — only where to reconnect and what
 * to reopen: the [connectionId], the [kind], and an [arg] (tmux session name for TMUX, remote path for
 * SFTP). Restored lazily and Disconnected; auth is re-resolved at reconnect time.
 */
data class PersistedView(
    val viewId: Long,
    val connectionId: Long,
    val kind: ViewKind,
    val arg: String? = null,
) {
    companion object {
        // Deterministic NEGATIVE ids for the (single) dashboard/SFTP view per connection, so upsert
        // dedups them; terminal tabs use their own POSITIVE tabId → the two ranges never collide.
        fun dashboardViewId(connectionId: Long): Long = -(connectionId * 2 + 1)
        fun sftpViewId(connectionId: Long): Long = -(connectionId * 2 + 2)
    }
}

/** The full persisted open-session set plus which view was selected. */
data class PersistedSessions(
    val views: List<PersistedView> = emptyList(),
    val selectedViewId: Long = -1L,
)
