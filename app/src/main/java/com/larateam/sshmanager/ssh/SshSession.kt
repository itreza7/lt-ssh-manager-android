package com.larateam.sshmanager.ssh

import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.ConnState
import kotlinx.coroutines.flow.StateFlow

/**
 * One SSH connection to a host (one SSHClient). Multiple CHANNELS are multiplexed over it via
 * [openShell] (and later dashboard exec / SFTP) — authentication happens ONCE per connection.
 * Implemented by [SshConnection]; abstracted so [SshConnectionManager] can be unit-tested with fakes.
 */
interface SshSession {
    val state: StateFlow<ConnState>
    val isConnected: Boolean

    suspend fun connect(credentials: AuthCredentials)

    /** Opens a new interactive shell CHANNEL multiplexed over this connection (no re-auth). */
    suspend fun openShell(columns: Int, rows: Int): ShellIo

    suspend fun exec(command: String): String

    /** Runs a one-shot command over an exec channel, capturing stdout + stderr + exit code. */
    suspend fun execCapturing(command: String): ExecResult

    /** Streams stdout line-by-line until the command ends or the coroutine is cancelled. */
    suspend fun execStream(command: String, onLine: (String) -> Unit)

    suspend fun disconnect()
}
