package com.larateam.sshmanager.sftp

/** Classified SFTP failure reason — the domain seam so the UI never sees sshj's SFTPException. */
enum class SftpErrorReason { PERMISSION_DENIED, NOT_FOUND, REJECTED, IO }

/** A domain SFTP failure carrying a classified [reason] (mapped from sshj inside the ssh/ layer). */
class SftpException(val reason: SftpErrorReason, message: String) : Exception(message)
