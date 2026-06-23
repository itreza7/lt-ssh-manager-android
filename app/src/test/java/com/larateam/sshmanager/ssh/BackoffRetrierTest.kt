package com.larateam.sshmanager.ssh

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketException
import java.net.SocketTimeoutException

class BackoffRetrierTest {

    @Test
    fun ceiling_grows_then_caps() {
        val backoff = Backoff(baseMillis = 500, maxMillis = 8_000, factor = 2.0, jitter = { 1.0 })
        // 500, 1000, 2000, 4000, 8000, then capped at 8000
        assertEquals(500L, backoff.ceilingMillis(0))
        assertEquals(1000L, backoff.ceilingMillis(1))
        assertEquals(2000L, backoff.ceilingMillis(2))
        assertEquals(8000L, backoff.ceilingMillis(4))
        assertEquals(8000L, backoff.ceilingMillis(9)) // capped
    }

    @Test
    fun jitter_keeps_delays_within_ceiling() {
        val backoff = Backoff(baseMillis = 1000, maxMillis = 10_000, factor = 2.0, jitter = { 0.37 })
        for (attempt in 0..6) {
            val d = backoff.delayMillis(attempt)
            assertTrue("delay $d within ceiling", d in 0..backoff.ceilingMillis(attempt))
        }
        // full-jitter extremes
        assertEquals(0L, Backoff(jitter = { 0.0 }).delayMillis(3))
    }

    @Test
    fun transient_failures_are_retried_with_growing_jittered_delays() = runTest {
        val recordedDelays = mutableListOf<Long>()
        val backoff = Backoff(baseMillis = 100, maxMillis = 5_000, factor = 2.0, jitter = { 1.0 })
        val retrier = Retrier(backoff, maxAttempts = 5, isPermanent = SshErrorClassifier::isPermanent)
        var calls = 0

        val result = retrier.run(onRetry = { _, d -> recordedDelays.add(d) }) {
            calls++
            if (calls < 3) throw SocketTimeoutException("transient") else "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, calls) // 2 failures + 1 success
        assertEquals(listOf(200L, 400L), recordedDelays) // ceil(1)=200, ceil(2)=400, jitter=1.0
        assertTrue("delays grow", recordedDelays[1] > recordedDelays[0])
    }

    @Test
    fun permanent_failure_fails_fast_with_zero_retries() = runTest {
        val retrier = Retrier(Backoff(), maxAttempts = 5, isPermanent = SshErrorClassifier::isPermanent)
        var calls = 0
        var thrown: Throwable? = null
        try {
            retrier.run { calls++; throw HostKeyChangedException("h", 22, "SHA256:a", "SHA256:b") }
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue(thrown is HostKeyChangedException)
        assertEquals(1, calls) // no retries
    }

    @Test
    fun gives_up_after_maxAttempts_on_persistent_transient() = runTest {
        val retrier = Retrier(Backoff(jitter = { 0.0 }), maxAttempts = 3, isPermanent = SshErrorClassifier::isPermanent)
        var calls = 0
        try {
            retrier.run { calls++; throw SocketException("network down") }
        } catch (_: SocketException) {
        }
        assertEquals(3, calls)
    }
}
