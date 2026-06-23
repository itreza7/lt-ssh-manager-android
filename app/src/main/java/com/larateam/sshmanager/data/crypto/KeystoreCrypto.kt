package com.larateam.sshmanager.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM encryption backed by a non-exportable [AndroidKeyStore][ANDROID_KEYSTORE] key.
 *
 * Design (a) — see report: the Keystore key is NOT user-authentication-bound, so stored
 * secrets survive the user adding/removing a biometric. Authentication is enforced as a
 * separate app-level gate ([BiometricGate]) before [decrypt] is called.
 *
 * Only ciphertext + IV ever leave this class; the key material never does.
 */
@Singleton
class KeystoreCrypto(private val keyAlias: String) {

    @Inject
    constructor() : this(DEFAULT_ALIAS)

    fun encrypt(plaintext: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedData(ciphertext = ciphertext, iv = cipher.iv)
    }

    fun decrypt(data: EncryptedData): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, requireKey(), GCMParameterSpec(TAG_BITS, data.iv))
        return cipher.doFinal(data.ciphertext)
    }

    /** Test/maintenance helper: drop the Keystore entry. */
    fun deleteKey() = keyStore().deleteEntry(keyAlias)

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Intentionally NOT setUserAuthenticationRequired(true) — see class KDoc (design a).
                .build(),
        )
        return generator.generateKey()
    }

    private fun requireKey(): SecretKey =
        (keyStore().getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: error("Keystore key '$keyAlias' is missing")

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val DEFAULT_ALIAS = "larateam.secret.aes.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
