package com.larateam.sshmanager.ssh

/**
 * Port that lets the (Android-free, testable) [SshConnectionManager] start the foreground service
 * when the first connection becomes active. The service stops itself when the active count hits 0.
 */
interface ForegroundServiceController {
    fun start()
}
