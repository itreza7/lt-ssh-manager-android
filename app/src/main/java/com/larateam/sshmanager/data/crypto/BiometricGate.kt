package com.larateam.sshmanager.data.crypto

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * App-level authentication gate (design a). Wraps [BiometricPrompt] with device-credential
 * fallback. Because the Keystore key is not auth-bound, no CryptoObject is needed — a
 * successful prompt simply authorises the caller to invoke decryption.
 */
class BiometricGate {

    sealed interface Outcome {
        data object Success : Outcome
        data object Unavailable : Outcome
        data class Error(val code: Int, val message: String) : Outcome
    }

    /** True when a biometric or device credential is available to authenticate with. */
    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(authenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS

    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
    ): Outcome {
        if (!canAuthenticate(activity)) return Outcome.Unavailable
        return suspendCancellableCoroutine { cont ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(Outcome.Error(errorCode, errString.toString()))
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(Outcome.Success)
                }
                // onAuthenticationFailed = one rejected attempt; the prompt stays open, so do nothing.
            }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
            prompt.authenticate(builder.build())
        }
    }

    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }
}
