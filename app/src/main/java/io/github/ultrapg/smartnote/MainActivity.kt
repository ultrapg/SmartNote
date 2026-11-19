package io.github.ultrapg.smartnote

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.ultrapg.smartnote.security.AppLockAuthenticator
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteTheme
import io.github.ultrapg.smartnote.ui.navigation.SmartNoteApp
import io.github.ultrapg.smartnote.ui.security.AppLockScreen
import kotlinx.coroutines.launch

sealed interface LaunchDeepLink {
    data object Overview : LaunchDeepLink
    data class OpenNote(val noteId: String) : LaunchDeepLink
    data class NewNote(val startChecklist: Boolean = false, val fromQuickTile: Boolean = false) : LaunchDeepLink
    data object OpenSearch : LaunchDeepLink
}

class MainActivity : ComponentActivity() {

    private var deepLinkState by mutableStateOf<LaunchDeepLink>(LaunchDeepLink.Overview)
    private var isAppLocked by mutableStateOf(false)
    private var lockErrorMessage by mutableStateOf<String?>(null)
    private var legacyCredentialCallback: ((Boolean, String?) -> Unit)? = null

    private val deviceCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = legacyCredentialCallback
        legacyCredentialCallback = null
        val appLockSession = (applicationContext as SmartNoteApplication).container.appLockSession
        appLockSession.setAuthPromptInFlight(false)
        val ok = result.resultCode == Activity.RESULT_OK
        callback?.invoke(ok, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkState = parseLaunchIntent(intent)

        val container = (applicationContext as SmartNoteApplication).container
        isAppLocked = container.appLockSession.isLocked(container.settingsRepository.currentSnapshot)

        setContent {
            val settings by container.settingsRepository.settingsFlow.collectAsState(
                initial = container.settingsRepository.currentSnapshot
            )
            val scope = rememberCoroutineScope()

            LaunchedEffect(settings.appLockEnabled, settings.hideRecentsWhenLocked) {
                if (settings.appLockEnabled && settings.hideRecentsWhenLocked) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                if (!settings.appLockEnabled) {
                    isAppLocked = false
                    lockErrorMessage = null
                }
            }

            SmartNoteTheme(
                palette = settings.themePalette,
                darkModeConfig = settings.darkModeConfig,
                reducedMotionPreference = settings.reducedMotion,
                syncWindowBackground = true
            ) {
                val showLockGate = settings.appLockEnabled && isAppLocked
                if (showLockGate) {
                    var autoPrompted by rememberSaveable(showLockGate) { mutableStateOf(false) }
                    val triggerUnlockPrompt: () -> Unit = {
                        lockErrorMessage = null
                        requestDeviceAuth(
                            title = "Unlock SmartNote",
                            subtitle = "Use your fingerprint, face, or device PIN"
                        ) { ok, err ->
                            if (ok) {
                                container.appLockSession.markUnlocked()
                                isAppLocked = false
                                lockErrorMessage = null
                            } else {
                                lockErrorMessage = err
                            }
                        }
                    }
                    LaunchedEffect(showLockGate) {
                        if (!autoPrompted) {
                            autoPrompted = true
                            triggerUnlockPrompt()
                        }
                    }
                    AppLockScreen(
                        errorMessage = lockErrorMessage,
                        onUnlockClick = triggerUnlockPrompt
                    )
                } else {
                    SmartNoteApp(
                        container = container,
                        deepLink = deepLinkState,
                        onDeepLinkConsumed = { deepLinkState = LaunchDeepLink.Overview },
                        onRequestAppLockChange = { desiredEnabled, onDone ->
                            val title = if (desiredEnabled) "Enable App Lock" else "Disable App Lock"
                            val subtitle = if (desiredEnabled) {
                                "Confirm with fingerprint or device PIN to lock SmartNote"
                            } else {
                                "Confirm with fingerprint or device PIN to turn off App Lock"
                            }
                            requestDeviceAuth(title = title, subtitle = subtitle) { ok, err ->
                                if (ok) {
                                    scope.launch {
                                        container.settingsRepository.setAppLockEnabled(desiredEnabled)
                                        if (desiredEnabled) {
                                            container.appLockSession.markUnlocked()
                                        }
                                        isAppLocked = false
                                    }
                                }
                                onDone(ok, err)
                            }
                        },
                        onLockAppNow = {
                            container.appLockSession.lockNow()
                            lockErrorMessage = null
                            isAppLocked = true
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val container = (applicationContext as SmartNoteApplication).container
        val snapshot = container.settingsRepository.currentSnapshot
        if (container.appLockSession.onForegrounded(snapshot, SystemClock.elapsedRealtime())) {
            lockErrorMessage = null
            isAppLocked = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            val container = (applicationContext as SmartNoteApplication).container
            container.appLockSession.onBackgrounded(
                settings = container.settingsRepository.currentSnapshot,
                elapsedRealtimeMs = SystemClock.elapsedRealtime()
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkState = parseLaunchIntent(intent)
    }

    private fun requestDeviceAuth(
        title: String,
        subtitle: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val appLockSession = (applicationContext as SmartNoteApplication).container.appLockSession
        if (appLockSession.isAuthPromptInFlight()) return
        appLockSession.setAuthPromptInFlight(true)
        AppLockAuthenticator.authenticate(
            activity = this,
            title = title,
            subtitle = subtitle,
            launchLegacyDeviceCredential = { authIntent ->
                legacyCredentialCallback = onResult
                deviceCredentialLauncher.launch(authIntent)
            },
            onResult = { ok, err ->
                appLockSession.setAuthPromptInFlight(false)
                onResult(ok, err)
            }
        )
    }

    private fun parseLaunchIntent(intent: Intent?): LaunchDeepLink {
        if (intent == null) return LaunchDeepLink.Overview
        val action = intent.action ?: intent.getStringExtra(EXTRA_WIDGET_ACTION)
        return when (action) {
            ACTION_OPEN_NOTE -> {
                val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
                if (!noteId.isNullOrBlank()) LaunchDeepLink.OpenNote(noteId) else LaunchDeepLink.Overview
            }
            ACTION_NEW_NOTE -> LaunchDeepLink.NewNote(
                startChecklist = false,
                fromQuickTile = intent.getBooleanExtra(EXTRA_SOURCE_QUICK_TILE, false)
            )
            ACTION_NEW_CHECKLIST -> LaunchDeepLink.NewNote(startChecklist = true)
            ACTION_OPEN_SEARCH -> LaunchDeepLink.OpenSearch
            else -> LaunchDeepLink.Overview
        }
    }

    companion object {
        const val ACTION_OPEN_NOTE = "io.github.ultrapg.smartnote.action.OPEN_NOTE"
        const val ACTION_NEW_NOTE = "io.github.ultrapg.smartnote.action.NEW_NOTE"
        const val ACTION_NEW_CHECKLIST = "io.github.ultrapg.smartnote.action.NEW_CHECKLIST"
        const val ACTION_OPEN_SEARCH = "io.github.ultrapg.smartnote.action.OPEN_SEARCH"
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_SOURCE_QUICK_TILE = "extra_source_quick_tile"
        const val EXTRA_WIDGET_ACTION = "smartnote_action"
    }
}
