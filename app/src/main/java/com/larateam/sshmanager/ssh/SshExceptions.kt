package com.larateam.sshmanager.ssh

/**
 * Thrown when a known host presents a DIFFERENT key than the pinned one — a possible MITM.
 * This is a permanent, never-retried security event; the pinned fingerprint is NOT overwritten.
 */
class HostKeyChangedException(
    val host: String,
    val port: Int,
    val expected: String,
    val actual: String,
) : RuntimeException(
    "Host key for $host:$port CHANGED. Pinned $expected but server presented $actual. " +
        "Possible man-in-the-middle attack.",
)

/** Thrown when key-based auth is requested but no usable key material is available. */
class MissingKeyException(message: String) : RuntimeException(message)
