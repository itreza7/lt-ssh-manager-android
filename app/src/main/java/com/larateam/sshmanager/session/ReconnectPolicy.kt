package com.larateam.sshmanager.session

import com.larateam.sshmanager.data.model.ConnError

/** Pure reconnect decisions (testable without a live connection). */
object ReconnectPolicy {

    /**
     * Auto-reconnect ONLY transient drops (timeout / network). Permanent failures — bad auth, a
     * MISSING key, and especially a CHANGED host key — must never loop (§4 / Phase 3 fail-fast).
     */
    fun shouldAutoReconnect(error: ConnError): Boolean = !error.permanent

    /**
     * tmux re-attach decision: attach to the named session if it's still alive, otherwise (re)create
     * it. `new -A` is itself attach-or-create, so this is safe even if the session races away.
     */
    fun tmuxReconnectCommand(sessionName: String, alive: Boolean): String {
        val quoted = shellSingleQuote(sessionName)
        return if (alive) "tmux attach -t $quoted" else "tmux new -A -s $quoted"
    }

    /** The probe a caller runs to decide [tmuxReconnectCommand]'s `alive`: exit 0 ⇒ alive. */
    fun tmuxHasSessionCommand(sessionName: String): String = "tmux has-session -t ${shellSingleQuote(sessionName)}"

    private fun shellSingleQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
