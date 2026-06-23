package com.larateam.sshmanager.ssh

import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

/**
 * Trust-on-first-use host-key verification (CLAUDE.md §4 #4).
 *
 *  - First contact: compute + pin the SHA256 fingerprint, report it, proceed.
 *  - Known host, key matches: proceed.
 *  - Known host, key CHANGED: throw [HostKeyChangedException] (blocking MITM warning).
 *    The pinned fingerprint is NEVER overwritten here — re-pinning is an explicit user action.
 */
class TofuHostKeyVerifier(
    private val store: KnownHostStore,
    private val onHostKey: (fingerprint: String, firstContact: Boolean) -> Unit = { _, _ -> },
) : HostKeyVerifier {

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val fingerprint = Fingerprints.sha256(key)
        when (val pinned = store.pinnedFingerprint(hostname, port)) {
            null -> {
                store.pin(hostname, port, fingerprint)
                onHostKey(fingerprint, true)
            }

            fingerprint -> onHostKey(fingerprint, false)

            else -> throw HostKeyChangedException(hostname, port, expected = pinned, actual = fingerprint)
        }
        return true
    }

    // Returning empty accepts all server host-key algorithms during negotiation (CLAUDE.md §7.2).
    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
}
