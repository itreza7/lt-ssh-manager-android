package com.larateam.sshmanager.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundPolicyTest {
    @Test
    fun foreground_iff_active_count_positive() {
        assertEquals(ForegroundPolicy.Action.STOP, ForegroundPolicy.actionFor(0))
        assertEquals(ForegroundPolicy.Action.GO_FOREGROUND, ForegroundPolicy.actionFor(1))
        assertEquals(ForegroundPolicy.Action.GO_FOREGROUND, ForegroundPolicy.actionFor(5))
    }
}
