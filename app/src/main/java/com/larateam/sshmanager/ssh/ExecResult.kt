package com.larateam.sshmanager.ssh

/** Captured result of a one-shot remote command run over an exec channel. */
data class ExecResult(
    val stdout: String,
    val stderr: String,
    /** Remote exit status, or -1 if it could not be determined. */
    val exitCode: Int,
)
