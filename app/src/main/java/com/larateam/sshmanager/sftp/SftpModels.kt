package com.larateam.sshmanager.sftp

enum class SftpEntryType { FILE, DIRECTORY, SYMLINK, OTHER }

/** A remote filesystem entry. Domain model — the UI never sees sshj types. */
data class SftpEntry(
    val name: String,
    val path: String,
    val type: SftpEntryType,
    val size: Long,
    /** Permission bits, e.g. 0o644. */
    val mode: Int,
    /** Modification time, epoch seconds (0 if unknown). */
    val mtimeEpochSec: Long,
) {
    val isDirectory: Boolean get() = type == SftpEntryType.DIRECTORY
    val isSymlink: Boolean get() = type == SftpEntryType.SYMLINK
    val isHidden: Boolean get() = name.startsWith(".")
}

/** Progress of one in-flight transfer. [total] is -1 when unknown. */
data class TransferProgress(
    val id: Long,
    val name: String,
    val isUpload: Boolean,
    val bytes: Long = 0,
    val total: Long = -1,
    val done: Boolean = false,
    val cancelled: Boolean = false,
    val error: String? = null,
) {
    val percent: Int get() = if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 100) else 0
    val active: Boolean get() = !done && !cancelled && error == null
}

/** Render a permission mask as `rwxr-xr-x`. */
fun permissionsString(mode: Int): String {
    val bits = "rwxrwxrwx"
    return buildString {
        for (i in 0 until 9) {
            append(if (mode and (1 shl (8 - i)) != 0) bits[i] else '-')
        }
    }
}
