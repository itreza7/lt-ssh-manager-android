package com.larateam.sshmanager.ssh

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Registers the FULL BouncyCastle provider (CLAUDE.md §7.2).
 *
 * Android ships a stripped-down "BC" provider; we remove it and insert the bundled
 * bcprov-jdk18on at the highest priority so sshj's key parsing and host-key algorithms work.
 * Idempotent — safe to call from Application.onCreate and from test setup.
 */
object SshProvider {
    @Volatile
    private var registered = false

    @Synchronized
    fun ensureRegistered() {
        if (registered) return
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        registered = true
    }
}
