package com.larateam.sshmanager.sftp

/**
 * Minimal ops the recursive delete needs — abstracted so the no-follow-symlink SAFETY is unit-testable
 * without a real remote filesystem.
 */
interface SftpDeleteOps {
    /** MUST be a NO-FOLLOW stat (lstat): a symlink reports [SftpEntryType.SYMLINK], not its target. */
    suspend fun entryType(path: String): SftpEntryType
    suspend fun children(path: String): List<String>
    suspend fun removeFile(path: String)
    suspend fun removeDir(path: String)
}

/**
 * Recursively delete [path], NEVER following symlinked directories: a symlink is removed as the link
 * ITSELF — its target's contents are never touched (deleting through a symlink could destroy files
 * outside the tree). Correctness hinges on [SftpDeleteOps.entryType] being no-follow.
 */
suspend fun deleteRecursive(ops: SftpDeleteOps, path: String) {
    when (ops.entryType(path)) {
        SftpEntryType.SYMLINK -> ops.removeFile(path)        // remove the link; do NOT descend
        SftpEntryType.DIRECTORY -> {
            ops.children(path).forEach { deleteRecursive(ops, it) }
            ops.removeDir(path)
        }
        else -> ops.removeFile(path)                          // regular file / other
    }
}
