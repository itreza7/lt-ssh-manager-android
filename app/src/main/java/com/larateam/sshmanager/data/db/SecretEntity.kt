package com.larateam.sshmanager.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Encrypted secret row (CLAUDE.md §4). Holds ONLY AES-GCM ciphertext + IV — never plaintext,
 * never the key. [ref] is the connection's `key_alias` reference.
 */
@Entity(tableName = "secrets")
data class SecretEntity(
    @PrimaryKey val ref: String,
    val ciphertext: ByteArray,
    val iv: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecretEntity) return false
        return ref == other.ref &&
            ciphertext.contentEquals(other.ciphertext) &&
            iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = ref.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
