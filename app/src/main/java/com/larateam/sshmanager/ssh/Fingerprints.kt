package com.larateam.sshmanager.ssh

import net.schmizz.sshj.common.Buffer
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/** OpenSSH-style SHA256 host-key fingerprint: "SHA256:" + base64(sha256(wire-encoded key)). */
internal object Fingerprints {
    fun sha256(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(digest)
        return "SHA256:$encoded"
    }
}
