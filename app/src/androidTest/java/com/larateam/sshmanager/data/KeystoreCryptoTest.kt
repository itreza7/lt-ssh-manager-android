package com.larateam.sshmanager.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.larateam.sshmanager.data.crypto.EncryptedData
import com.larateam.sshmanager.data.crypto.KeystoreCrypto
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException

@RunWith(AndroidJUnit4::class)
class KeystoreCryptoTest {

    private val alias = "larateam.test.crypto.${System.nanoTime()}"
    private lateinit var crypto: KeystoreCrypto

    @Before
    fun setUp() {
        crypto = KeystoreCrypto(alias)
    }

    @After
    fun tearDown() {
        crypto.deleteKey()
    }

    @Test
    fun encrypt_then_decrypt_returns_original() {
        val plaintext = "BEGIN OPENSSH PRIVATE KEY-secret-bytes".encodeToByteArray()
        val encrypted = crypto.encrypt(plaintext)
        assertArrayEquals(plaintext, crypto.decrypt(encrypted))
    }

    @Test
    fun ciphertext_differs_from_plaintext_and_iv_is_gcm_sized() {
        val plaintext = "hunter2-private-key".encodeToByteArray()
        val encrypted = crypto.encrypt(plaintext)
        assertFalse("ciphertext must not equal plaintext", plaintext.contentEquals(encrypted.ciphertext))
        assertEquals("GCM IV should be 12 bytes", 12, encrypted.iv.size)
    }

    @Test
    fun tampered_ciphertext_fails_to_decrypt() {
        val encrypted = crypto.encrypt("authentic".encodeToByteArray())
        val tampered = encrypted.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() }
        try {
            crypto.decrypt(EncryptedData(tampered, encrypted.iv))
            fail("decrypt should reject tampered ciphertext")
        } catch (_: GeneralSecurityException) {
            // expected: AEAD auth tag mismatch
        }
    }

    @Test
    fun tampered_iv_fails_to_decrypt() {
        val encrypted = crypto.encrypt("authentic".encodeToByteArray())
        val tamperedIv = encrypted.iv.copyOf().also { it[0] = (it[0] + 1).toByte() }
        try {
            crypto.decrypt(EncryptedData(encrypted.ciphertext, tamperedIv))
            fail("decrypt should reject a tampered IV")
        } catch (_: GeneralSecurityException) {
            // expected
        }
    }
}
