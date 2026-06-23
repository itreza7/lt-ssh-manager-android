package com.larateam.sshmanager.ssh

import kotlin.math.pow
import kotlin.random.Random

/**
 * Jittered exponential backoff with a cap. [delayMillis] applies "full jitter": a uniform random
 * value in [0, ceiling], where the ceiling grows as base * factor^attempt, capped at [maxMillis].
 * The jitter source is injectable for deterministic tests.
 */
class Backoff(
    private val baseMillis: Long = 500,
    private val maxMillis: Long = 20_000,
    private val factor: Double = 2.0,
    private val jitter: () -> Double = { Random.nextDouble() },
) {
    /** Upper bound (no jitter) for the given attempt, capped at [maxMillis]. */
    fun ceilingMillis(attempt: Int): Long =
        (baseMillis.toDouble() * factor.pow(attempt.coerceAtLeast(0)))
            .coerceAtMost(maxMillis.toDouble())
            .toLong()

    fun delayMillis(attempt: Int): Long =
        (jitter().coerceIn(0.0, 1.0) * ceilingMillis(attempt)).toLong().coerceAtLeast(0)
}
