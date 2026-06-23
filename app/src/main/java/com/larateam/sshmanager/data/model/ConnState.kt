package com.larateam.sshmanager.data.model

/** SHA256 host-key fingerprint plus whether this was the first time we saw the host (TOFU). */
data class HostKeyInfo(val fingerprintSha256: String, val firstContact: Boolean)

/**
 * Classified connection failures. [permanent] errors are NEVER retried — bad auth, a missing
 * key, and especially a CHANGED host key (a security event) fail fast.
 */
enum class ConnError(val permanent: Boolean) {
    AUTH_FAILED(true),
    HOST_KEY_CHANGED(true),
    MISSING_KEY(true),
    TIMEOUT(false),
    NETWORK(false),
    UNKNOWN(true),
}

/** Per-session connection state; the UI status indicator observes this as a StateFlow. */
sealed interface ConnState {
    data object Disconnected : ConnState
    data object Connecting : ConnState
    data class Connected(val hostKey: HostKeyInfo) : ConnState
    data class Error(val error: ConnError, val message: String) : ConnState
}
