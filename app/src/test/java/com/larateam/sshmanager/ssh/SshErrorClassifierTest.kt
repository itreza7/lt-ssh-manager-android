package com.larateam.sshmanager.ssh

import com.larateam.sshmanager.data.model.ConnError
import net.schmizz.sshj.userauth.UserAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class SshErrorClassifierTest {

    @Test
    fun permanent_errors_fail_fast() {
        assertEquals(ConnError.HOST_KEY_CHANGED, SshErrorClassifier.classify(HostKeyChangedException("h", 22, "a", "b")))
        assertEquals(ConnError.AUTH_FAILED, SshErrorClassifier.classify(UserAuthException("bad creds")))
        assertEquals(ConnError.MISSING_KEY, SshErrorClassifier.classify(MissingKeyException("no key")))

        assertTrue(SshErrorClassifier.isPermanent(HostKeyChangedException("h", 22, "a", "b")))
        assertTrue(SshErrorClassifier.isPermanent(UserAuthException("x")))
        assertTrue(SshErrorClassifier.isPermanent(MissingKeyException("x")))
    }

    @Test
    fun transient_errors_are_retryable() {
        assertEquals(ConnError.TIMEOUT, SshErrorClassifier.classify(SocketTimeoutException()))
        assertEquals(ConnError.NETWORK, SshErrorClassifier.classify(ConnectException("refused")))
        assertFalse(SshErrorClassifier.isPermanent(SocketTimeoutException()))
        assertFalse(SshErrorClassifier.isPermanent(ConnectException("refused")))
    }

    @Test
    fun wrapped_cause_is_classified() {
        // sshj wraps a thrown verifier exception; the cause chain must still be classified.
        val wrapped = RuntimeException("transport failed", HostKeyChangedException("h", 22, "a", "b"))
        assertEquals(ConnError.HOST_KEY_CHANGED, SshErrorClassifier.classify(wrapped))
        assertTrue(SshErrorClassifier.isPermanent(wrapped))
    }
}
