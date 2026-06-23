package com.larateam.sshmanager.data.model

import com.larateam.sshmanager.sftp.SftpErrorReason

/**
 * Central error → user-readable message mapping (Phase 12 error-UX sweep). Pure + JVM-testable; the UI
 * surfaces these instead of raw stack traces. Whether to offer a Retry follows the classification:
 * transient failures retry, permanent ones (auth, changed key, missing key) only dismiss.
 */
fun ConnError.userMessage(): String = when (this) {
    ConnError.AUTH_FAILED -> "Authentication failed. Check the username and your password or key."
    ConnError.HOST_KEY_CHANGED ->
        "The host key changed — this could be a man-in-the-middle attack, so the connection was blocked. " +
            "If the server legitimately rotated its key, forget it in Settings → Known hosts, then reconnect."
    ConnError.MISSING_KEY -> "The private key could not be found or read."
    ConnError.TIMEOUT -> "Timed out reaching the server. Check the host, port, and your network."
    ConnError.NETWORK -> "Network error reaching the server. Check your connection and try again."
    ConnError.UNKNOWN -> "Couldn't connect. Please try again."
}

/** Transient failures are worth a Retry; permanent ones (auth/changed-key/missing-key) are not. */
val ConnError.retryable: Boolean get() = !permanent

fun SftpErrorReason.userMessage(): String = when (this) {
    SftpErrorReason.PERMISSION_DENIED -> "Permission denied."
    SftpErrorReason.NOT_FOUND -> "No such file or directory."
    SftpErrorReason.REJECTED -> "The server rejected the operation."
    SftpErrorReason.IO -> "Transfer failed — the connection was interrupted."
}
