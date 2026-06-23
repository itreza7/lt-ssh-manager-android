package com.larateam.sshmanager.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmuxParserTest {

    @Test
    fun normal_output_parses_sessions() {
        val out = """
            work: 1 windows (created Thu Jun 19 14:00:00 2026)
            logs: 2 windows (created Thu Jun 19 14:01:00 2026) (attached)
        """.trimIndent()
        val sessions = TmuxParser.parse(out)
        assertEquals(2, sessions.size)
        assertEquals(TmuxSession("work", 1, false), sessions[0])
        assertEquals(TmuxSession("logs", 2, true), sessions[1])
    }

    @Test
    fun no_server_running_yields_empty() {
        assertTrue(TmuxParser.parse("").isEmpty())
        assertTrue(TmuxParser.parse("no server running on /tmp/tmux-0/default").isEmpty())
    }
}
