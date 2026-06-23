package com.larateam.sshmanager.session

import com.larateam.sshmanager.data.model.ConnError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {

    @Test
    fun transient_drops_auto_reconnect() {
        assertTrue(ReconnectPolicy.shouldAutoReconnect(ConnError.NETWORK))
        assertTrue(ReconnectPolicy.shouldAutoReconnect(ConnError.TIMEOUT))
    }

    @Test
    fun permanent_failures_do_not_loop() {
        assertFalse(ReconnectPolicy.shouldAutoReconnect(ConnError.AUTH_FAILED))
        assertFalse("a CHANGED host key must never auto-reconnect", ReconnectPolicy.shouldAutoReconnect(ConnError.HOST_KEY_CHANGED))
        assertFalse(ReconnectPolicy.shouldAutoReconnect(ConnError.MISSING_KEY))
        assertFalse(ReconnectPolicy.shouldAutoReconnect(ConnError.UNKNOWN))
    }

    @Test
    fun tmux_attaches_when_alive_otherwise_creates() {
        assertEquals("tmux attach -t 'work'", ReconnectPolicy.tmuxReconnectCommand("work", alive = true))
        assertEquals("tmux new -A -s 'work'", ReconnectPolicy.tmuxReconnectCommand("work", alive = false))
    }

    @Test
    fun tmux_session_names_are_shell_quoted() {
        assertEquals("tmux attach -t 'a b'", ReconnectPolicy.tmuxReconnectCommand("a b", alive = true))
        assertEquals("tmux has-session -t 'work'", ReconnectPolicy.tmuxHasSessionCommand("work"))
    }
}
