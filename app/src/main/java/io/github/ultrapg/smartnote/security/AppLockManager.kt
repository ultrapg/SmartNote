package io.github.ultrapg.smartnote.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import io.github.ultrapg.smartnote.domain.model.AppSettings

data class AppLockCapabilityStatus(
    val isDeviceSecure: Boolean,
    val isBiometricAvailable: Boolean,
    val statusSummary: String
)

/**
 * Tracks in-memory unlock state across lifecycle transitions and configuration changes
 * so active Android biometric / PIN prompts never accidentally re-lock the session.
 */
class AppLockSessionTracker {
    @Volatile
    private var unlockedThisSession: Boolean = false

    @Volatile
    private var lastBackgroundedElapsedMs: Long = -1L

    @Volatile
    private var authPromptInFlight: Boolean = false

    fun isLocked(settings: AppSettings): Boolean =
        settings.appLockEnabled && !unlockedThisSession

    fun setAuthPromptInFlight(inFlight: Boolean) {
        authPromptInFlight = inFlight
    }

    fun isAuthPromptInFlight(): Boolean = authPromptInFlight

    fun markUnlocked() {
        unlockedThisSession = true
        lastBackgroundedElapsedMs = -1L
        authPromptInFlight = false
    }

    fun lockNow() {
        unlockedThisSession = false
        lastBackgroundedElapsedMs = -1L
    }

    fun onBackgrounded(settings: AppSettings, elapsedRealtimeMs: Long) {
        if (!settings.appLockEnabled || authPromptInFlight || !unlockedThisSession) return
        lastBackgroundedElapsedMs = elapsedRealtimeMs
    }

    fun onForegrounded(settings: AppSettings, elapsedRealtimeMs: Long): Boolean {
        if (!settings.appLockEnabled) {
            lastBackgroundedElapsedMs = -1L
            return false
        }
        if (authPromptInFlight) {
            return false
        }
        if (!unlockedThisSession) {
            return true
        }
        val bgAt = lastBackgroundedElapsedMs
        lastBackgroundedElapsedMs = -1L
        if (bgAt < 0L) {
            return false
        }
        val elapsedAwayMs = (elapsedRealtimeMs - bgAt).coerceAtLeast(0L)
        val shouldLock = elapsedAwayMs >= settings.appLockTimeout.timeoutMs
        if (shouldLock) {
            unlockedThisSession = false
        }
        return shouldLock
    }
}

object AppLockAuthenticator {

    fun inspectCapabilities(context: Context): AppLockCapabilityStatus {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val deviceSecure = keyguard?.isDeviceSecure == true

        val biometricReady = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val bm = context.getSystemService(BiometricManager::class.java)
                val auth = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                bm?.canAuthenticate(auth) == BiometricManager.BIOMETRIC_SUCCESS
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                @Suppress("DEPRECATION")
                context.getSystemService(BiometricManager::class.java)?.canAuthenticate() ==
                    BiometricManager.BIOMETRIC_SUCCESS
            }
            else -> {
                deviceSecure && context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
            }
        }

        val summary = when {
            biometricReady && deviceSecure -> "Fingerprint / biometrics and device PIN ready"
            deviceSecure -> "Device PIN, pattern, or password ready"
            biometricReady -> "Biometric sensor available — set a screen lock PIN in Android Settings"
            else -> "No Android screen lock set. Configure a PIN, pattern, or fingerprint first."
        }
        return AppLockCapabilityStatus(
            isDeviceSecure = deviceSecure || biometricReady,
            isBiometricAvailable = biometricReady,
            statusSummary = summary
        )
    }

    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String,
        launchLegacyDeviceCredential: (Intent) -> Unit,
        onResult: (succeeded: Boolean, message: String?) -> Unit
    ) {
        val capabilities = inspectCapabilities(activity)
        if (!capabilities.isDeviceSecure) {
            onResult(false, "Set up a screen lock PIN or fingerprint in Android Settings first.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            authenticateApi30Plus(activity, title, subtitle, onResult)
            return
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            authenticateApi29(activity, title, subtitle, onResult)
            return
        }

        authenticateApi28(
            activity = activity,
            title = title,
            subtitle = subtitle,
            hasBiometric = capabilities.isBiometricAvailable,
            launchLegacyDeviceCredential = launchLegacyDeviceCredential,
            onResult = onResult
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun authenticateApi30Plus(
        activity: Activity,
        title: String,
        subtitle: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        runCatching {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                buildCallback(onResult)
            )
        }.onFailure { err ->
            onResult(false, err.message ?: "Authentication could not start")
        }
    }

    @Suppress("DEPRECATION")
    private fun authenticateApi29(
        activity: Activity,
        title: String,
        subtitle: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        runCatching {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDeviceCredentialAllowed(true)
                .build()
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                buildCallback(onResult)
            )
        }.onFailure { err ->
            onResult(false, err.message ?: "Authentication could not start")
        }
    }

    @Suppress("DEPRECATION")
    private fun authenticateApi28(
        activity: Activity,
        title: String,
        subtitle: String,
        hasBiometric: Boolean,
        launchLegacyDeviceCredential: (Intent) -> Unit,
        onResult: (Boolean, String?) -> Unit
    ) {
        fun triggerKeyguardConfirm() {
            val km = activity.getSystemService(KeyguardManager::class.java)
            val intent = km?.createConfirmDeviceCredentialIntent(title, subtitle)
            if (intent != null) {
                launchLegacyDeviceCredential(intent)
            } else {
                onResult(false, "Device PIN prompt unavailable")
            }
        }

        if (!hasBiometric) {
            triggerKeyguardConfirm()
            return
        }

        runCatching {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton("Use PIN", activity.mainExecutor) { _, _ ->
                    triggerKeyguardConfirm()
                }
                .build()
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                buildCallback(onResult)
            )
        }.onFailure {
            triggerKeyguardConfirm()
        }
    }

    private fun buildCallback(
        onResult: (Boolean, String?) -> Unit
    ): BiometricPrompt.AuthenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
            onResult(true, null)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
            val isUserCancel = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
            val msg = if (isUserCancel) null else errString?.toString()
            onResult(false, msg)
        }

        override fun onAuthenticationFailed() {
            // System prompt handles retry feedback natively until terminal success/error
        }
    }
}
