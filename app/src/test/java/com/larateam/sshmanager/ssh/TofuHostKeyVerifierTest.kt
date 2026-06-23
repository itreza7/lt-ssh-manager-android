package com.larateam.sshmanager.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.PublicKey

class TofuHostKeyVerifierTest {

    /** In-memory [KnownHostStore]; pin() throws if a pin already exists (mirrors INSERT ABORT). */
    private class FakeStore : KnownHostStore {
        val pins = mutableMapOf<String, String>()
        override fun pinnedFingerprint(host: String, port: Int) = pins["$host:$port"]
        override fun pin(host: String, port: Int, fingerprintSha256: String) {
            check("$host:$port" !in pins) { "already pinned" }
            pins["$host:$port"] = fingerprintSha256
        }
    }

    private lateinit var keyA: PublicKey
    private lateinit var keyB: PublicKey

    @Before
    fun setUp() {
        SshProvider.ensureRegistered()
        val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        keyA = gen.generateKeyPair().public
        keyB = gen.generateKeyPair().public
    }

    @Test
    fun first_contact_pins_the_fingerprint() {
        val store = FakeStore()
        var reported: Pair<String, Boolean>? = null
        val verifier = TofuHostKeyVerifier(store) { fp, first -> reported = fp to first }

        assertTrue(verifier.verify("example.com", 22, keyA))
        assertEquals(Fingerprints.sha256(keyA), store.pins["example.com:22"])
        assertEquals(true, reported?.second) // firstContact
    }

    @Test
    fun matching_key_passes() {
        val store = FakeStore().apply { pins["example.com:22"] = Fingerprints.sha256(keyA) }
        val verifier = TofuHostKeyVerifier(store)
        assertTrue(verifier.verify("example.com", 22, keyA))
        assertEquals(Fingerprints.sha256(keyA), store.pins["example.com:22"]) // unchanged
    }

    @Test
    fun changed_key_throws_and_does_not_overwrite_pin() {
        val pinnedFp = Fingerprints.sha256(keyA)
        val store = FakeStore().apply { pins["example.com:22"] = pinnedFp }
        val verifier = TofuHostKeyVerifier(store)

        val ex = assertThrows(HostKeyChangedException::class.java) {
            verifier.verify("example.com", 22, keyB)
        }
        assertEquals(pinnedFp, ex.expected)
        assertEquals(Fingerprints.sha256(keyB), ex.actual)
        // CRITICAL: the pinned fingerprint must remain the original, never auto-updated.
        assertEquals(pinnedFp, store.pins["example.com:22"])
    }

    @Test
    fun forgetting_a_pin_lets_the_next_connect_re_tofu() {
        // Host was pinned to keyA, then legitimately rotated to keyB.
        val store = FakeStore().apply { pins["example.com:22"] = Fingerprints.sha256(keyA) }
        // In-session, the rotated key is BLOCKED (the security path — never auto-trust).
        assertThrows(HostKeyChangedException::class.java) { TofuHostKeyVerifier(store).verify("example.com", 22, keyB) }

        // User forgets the host (Settings → Known hosts). The next connect must re-TOFU.
        store.pins.remove("example.com:22")
        var reported: Pair<String, Boolean>? = null
        val verifier = TofuHostKeyVerifier(store) { fp, first -> reported = fp to first }

        assertTrue(verifier.verify("example.com", 22, keyB)) // treated as first-contact
        assertEquals(true, reported?.second)                  // firstContact == true
        assertEquals(Fingerprints.sha256(keyB), store.pins["example.com:22"]) // re-pinned to the NEW key
    }

    @Test
    fun findExistingAlgorithms_is_empty() {
        assertTrue(TofuHostKeyVerifier(FakeStore()).findExistingAlgorithms("h", 22).isEmpty())
        assertNull(FakeStore().pinnedFingerprint("h", 22))
    }
}
