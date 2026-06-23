package com.larateam.sshmanager.ssh

import kotlinx.coroutines.delay

/**
 * Runs [block] with retry-on-transient semantics: permanent failures (per [isPermanent]) are
 * rethrown immediately with ZERO retries; transient failures are retried up to [maxAttempts]
 * with jittered exponential [backoff] between attempts.
 */
class Retrier(
    private val backoff: Backoff,
    private val maxAttempts: Int = 5,
    private val isPermanent: (Throwable) -> Boolean,
) {
    suspend fun <T> run(
        onRetry: (attempt: Int, delayMs: Long) -> Unit = { _, _ -> },
        block: suspend () -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (t: Throwable) {
                if (isPermanent(t)) throw t
                attempt++
                if (attempt >= maxAttempts) throw t
                val delayMs = backoff.delayMillis(attempt)
                onRetry(attempt, delayMs)
                delay(delayMs)
            }
        }
    }
}
