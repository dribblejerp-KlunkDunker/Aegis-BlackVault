package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity

/**
 * Optional app lock (roadmap Phase 2). When enabled, the app asks for the device's biometric or
 * screen lock on launch and whenever it returns from the background. Off by default.
 *
 * BIOMETRIC_WEAK | DEVICE_CREDENTIAL is the one authenticator combination supported on every API
 * level from 24 upward, so the user can always fall back to their PIN, pattern or password.
 */
object AppLock {
    private const val PREFS = "aegis_settings"
    private const val KEY_ENABLED = "app_lock_enabled"
    const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    /** True when the device has a biometric or a screen lock the app can ask for. */
    fun isAvailable(context: Context): Boolean = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Enables or disables the lock. Enabling is refused (returns false) when the device has no
     * biometric or screen lock, since the app could then never be unlocked.
     */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (enabled && !isAvailable(context)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, enabled) }
        return true
    }

    fun prompt(activity: FragmentActivity, onUnlocked: () -> Unit, onFailed: (String) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailed(errString.toString())
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Aegis Blackvault")
            .setSubtitle("Use your fingerprint, face, or screen lock")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
