package com.larateam.sshmanager.service

/** The whole policy: be a foreground service iff there is at least one active session. */
object ForegroundPolicy {
    enum class Action { GO_FOREGROUND, STOP }

    fun actionFor(activeCount: Int): Action =
        if (activeCount > 0) Action.GO_FOREGROUND else Action.STOP
}
