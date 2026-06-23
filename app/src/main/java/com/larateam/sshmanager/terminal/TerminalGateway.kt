package com.larateam.sshmanager.terminal

import com.larateam.sshmanager.data.model.AuthCredentials
import com.larateam.sshmanager.data.model.ConnError
import com.larateam.sshmanager.data.model.ConnectionTarget
import com.larateam.sshmanager.data.model.HostKeyInfo
import com.larateam.sshmanager.ssh.ShellIo
import com.larateam.sshmanager.ssh.ShellOpenResult
import com.larateam.sshmanager.ssh.SshConnectionManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Result of opening a tab's shell channel. */
sealed interface GatewayResult {
    data class Ok(val channelId: Long, val channel: ShellIo, val hostKey: HostKeyInfo) : GatewayResult
    data class Fail(val error: ConnError, val message: String) : GatewayResult
}

/**
 * The seam between [TerminalSessionStore] (tab lifecycle) and the sshj layer. There is ONE connection
 * (SSHClient) per host; each tab opens a CHANNEL multiplexed over it. Authentication happens once per
 * host — a second tab to an already-connected host opens with no credentials and no prompt.
 * Abstracted so the store can be tested with an in-memory fake.
 */
interface TerminalGateway {
    /** Active-channel count driving the Phase 4 foreground service. */
    val activeCount: StateFlow<Int>

    /** True if the host connection is already live (so a new channel needs no credentials/prompt). */
    fun isConnected(connectionId: Long): Boolean

    /** Open a shell channel on [connectionId]'s connection (establishing + authing it once if needed). */
    suspend fun open(
        connectionId: Long,
        target: ConnectionTarget,
        credentials: AuthCredentials?,
        columns: Int,
        rows: Int,
    ): GatewayResult

    /** Release one channel; the shared host connection closes only when its last channel is released. */
    fun release(channelId: Long)
}

@Singleton
class RealTerminalGateway @Inject constructor(
    private val manager: SshConnectionManager,
) : TerminalGateway {

    override val activeCount: StateFlow<Int> get() = manager.activeCount

    override fun isConnected(connectionId: Long): Boolean = manager.isConnected(connectionId)

    override suspend fun open(
        connectionId: Long,
        target: ConnectionTarget,
        credentials: AuthCredentials?,
        columns: Int,
        rows: Int,
    ): GatewayResult = when (val r = manager.openShell(connectionId, target, credentials, columns, rows)) {
        is ShellOpenResult.Opened -> GatewayResult.Ok(r.channelId, r.channel, r.hostKey)
        is ShellOpenResult.Failed -> GatewayResult.Fail(r.error, r.message)
    }

    override fun release(channelId: Long) = manager.releaseChannel(channelId)
}
