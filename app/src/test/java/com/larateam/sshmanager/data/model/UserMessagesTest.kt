package com.larateam.sshmanager.data.model

import com.larateam.sshmanager.sftp.SftpErrorReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMessagesTest {

    @Test
    fun every_connect_error_maps_to_a_nonblank_message() {
        ConnError.entries.forEach { assertTrue("$it has a message", it.userMessage().isNotBlank()) }
    }

    @Test
    fun auth_failed_message_is_actionable() {
        val m = ConnError.AUTH_FAILED.userMessage()
        assertTrue(m.contains("Authentication", ignoreCase = true))
    }

    @Test
    fun changed_key_message_warns_of_mitm_and_points_to_forget() {
        val m = ConnError.HOST_KEY_CHANGED.userMessage()
        assertTrue(m.contains("man-in-the-middle", ignoreCase = true))
        assertTrue(m.contains("forget", ignoreCase = true) || m.contains("Known hosts", ignoreCase = true))
    }

    @Test
    fun retryable_follows_classification() {
        assertTrue(ConnError.NETWORK.retryable)
        assertTrue(ConnError.TIMEOUT.retryable)
        assertFalse(ConnError.AUTH_FAILED.retryable)
        assertFalse(ConnError.HOST_KEY_CHANGED.retryable)
        assertFalse(ConnError.MISSING_KEY.retryable)
    }

    @Test
    fun sftp_reasons_map_to_messages() {
        assertTrue(SftpErrorReason.PERMISSION_DENIED.userMessage().contains("Permission", ignoreCase = true))
        assertTrue(SftpErrorReason.NOT_FOUND.userMessage().contains("No such", ignoreCase = true))
        SftpErrorReason.entries.forEach { assertTrue(it.userMessage().isNotBlank()) }
    }
}
