package io.github.ultrapg.smartnote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteTheme
import io.github.ultrapg.smartnote.ui.navigation.SmartNoteApp

sealed interface LaunchDeepLink {
    data object Overview : LaunchDeepLink
    data class OpenNote(val noteId: String) : LaunchDeepLink
    data class NewNote(val startChecklist: Boolean = false, val fromQuickTile: Boolean = false) : LaunchDeepLink
    data object OpenSearch : LaunchDeepLink
}

class MainActivity : ComponentActivity() {

    private var deepLinkState by mutableStateOf<LaunchDeepLink>(LaunchDeepLink.Overview)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkState = parseLaunchIntent(intent)

        val container = (applicationContext as SmartNoteApplication).container
        setContent {
            val settings by container.settingsRepository.settingsFlow.collectAsState(
                initial = container.settingsRepository.currentSnapshot
            )
            SmartNoteTheme(
                palette = settings.themePalette,
                darkModeConfig = settings.darkModeConfig,
                reducedMotionPreference = settings.reducedMotion,
                syncWindowBackground = true
            ) {
                SmartNoteApp(
                    container = container,
                    deepLink = deepLinkState,
                    onDeepLinkConsumed = { deepLinkState = LaunchDeepLink.Overview }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkState = parseLaunchIntent(intent)
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
