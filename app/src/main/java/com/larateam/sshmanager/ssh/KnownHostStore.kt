package com.larateam.sshmanager.ssh

/**
 * Synchronous pinned-fingerprint store used by [TofuHostKeyVerifier] (sshj calls verify() on a
 * blocking IO thread). Implemented by KnownHostsRepository; faked in unit tests.
 */
interface KnownHostStore {
    fun pinnedFingerprint(host: String, port: Int): String?

    /** Pins a fingerprint on first contact. Implementations MUST NOT overwrite an existing pin here. */
    fun pin(host: String, port: Int, fingerprintSha256: String)
}
