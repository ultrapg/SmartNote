package io.github.ultrapg.smartnote.integration.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.ultrapg.smartnote.MainActivity
import io.github.ultrapg.smartnote.SmartNoteApplication
import io.github.ultrapg.smartnote.domain.model.NoteFilterType
import io.github.ultrapg.smartnote.domain.model.NoteSortOrder
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteTheme
import kotlinx.coroutines.launch

object WidgetKeys {
    val KEY_PINNED_NOTE_ID = stringPreferencesKey("pinned_widget_note_id")
    val EXTRA_ACTION_PARAM = ActionParameters.Key<String>("smartnote_action")
    val EXTRA_NOTE_ID_PARAM = ActionParameters.Key<String>("smartnote_note_id")
}

object WidgetRefreshHelper {
    suspend fun refreshAllWidgets(context: Context) {
        runCatching { QuickNoteWidget().updateAll(context) }
        runCatching { PinnedNoteWidget().updateAll(context) }
        runCatching { NotesOverviewWidget().updateAll(context) }
    }
}

private fun launchMainAction(context: Context, action: String, noteId: String? = null) =
    actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            this.action = action
            if (noteId != null) putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )

class QuickNoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(24.dp)
                        .background(GlanceTheme.colors.primaryContainer)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(launchMainAction(context, MainActivity.ACTION_NEW_NOTE))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "+ Quick Note",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Capture offline",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onPrimaryContainer
                                )
                            )
                        }
                        Spacer(GlanceModifier.width(8.dp))
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(16.dp)
                                .background(GlanceTheme.colors.surface)
                                .clickable(launchMainAction(context, MainActivity.ACTION_NEW_CHECKLIST))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Checklist",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(16.dp)
                                .background(GlanceTheme.colors.surface)
                                .clickable(launchMainAction(context, MainActivity.ACTION_OPEN_SEARCH))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Search",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

class QuickNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickNoteWidget()
}

class PinnedNoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as SmartNoteApplication
        val summaries = app.container.noteRepository.getAllSummariesSnapshot(includeArchived = false)
        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val configuredNoteId = prefs[WidgetKeys.KEY_PINNED_NOTE_ID]
            val targetNote = summaries.firstOrNull { it.id == configuredNoteId }
                ?: summaries.firstOrNull { it.isPinned }
                ?: summaries.firstOrNull()

            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(24.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(
                            if (targetNote != null) {
                                launchMainAction(context, MainActivity.ACTION_OPEN_NOTE, targetNote.id)
                            } else {
                                launchMainAction(context, MainActivity.ACTION_NEW_NOTE)
                            }
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        text = targetNote?.title?.ifBlank { "SmartNote" } ?: "No pinned note",
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = targetNote?.previewText?.ifBlank { "Tap to open in SmartNote" }
                            ?: "Tap to create your first note",
                        maxLines = 4,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

class PinnedNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PinnedNoteWidget()
}

class PinnedNoteWidgetConfigActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val app = applicationContext as SmartNoteApplication
        setContent {
            val settings by app.container.settingsRepository.settingsFlow.collectAsState(
                initial = io.github.ultrapg.smartnote.domain.model.AppSettings()
            )
            val notes by app.container.noteRepository.observeActiveNotes(
                NoteSortOrder.UPDATED_DESC,
                NoteFilterType.ALL
            ).collectAsState(initial = emptyList())
            val scope = rememberCoroutineScope()

            SmartNoteTheme(
                palette = settings.themePalette,
                darkModeConfig = settings.darkModeConfig,
                reducedMotionPreference = settings.reducedMotion
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("Choose note for widget") })
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = padding.calculateTopPadding() + 12.dp,
                            bottom = padding.calculateBottomPadding() + 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val glanceId = GlanceAppWidgetManager(this@PinnedNoteWidgetConfigActivity)
                                                .getGlanceIdBy(appWidgetId)
                                            updateAppWidgetState(this@PinnedNoteWidgetConfigActivity, glanceId) { prefs ->
                                                prefs[WidgetKeys.KEY_PINNED_NOTE_ID] = note.id
                                            }
                                            PinnedNoteWidget().update(this@PinnedNoteWidgetConfigActivity, glanceId)
                                            setResult(
                                                RESULT_OK,
                                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                            )
                                            finish()
                                        }
                                    }
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        text = note.title.ifBlank { "Untitled note" },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (note.previewText.isNotBlank()) {
                                        Text(
                                            text = note.previewText.take(100),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class NotesOverviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as SmartNoteApplication
        val recent = app.container.noteRepository.getAllSummariesSnapshot(includeArchived = false)
            .sortedWith(
                compareByDescending<io.github.ultrapg.smartnote.domain.model.NoteSummary> { it.isPinned }
                    .thenByDescending { it.updatedAtMs }
            )
            .take(4)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(24.dp)
                        .background(GlanceTheme.colors.surface)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SmartNote",
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(12.dp)
                                .background(GlanceTheme.colors.primaryContainer)
                                .clickable(launchMainAction(context, MainActivity.ACTION_NEW_NOTE))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "+ New",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    if (recent.isEmpty()) {
                        Text(
                            text = "No notes yet — tap + New",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                        )
                    } else {
                        recent.forEach { item ->
                            Column(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .cornerRadius(12.dp)
                                    .background(GlanceTheme.colors.surfaceVariant)
                                    .clickable(launchMainAction(context, MainActivity.ACTION_OPEN_NOTE, item.id))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = (if (item.isPinned) "★ " else "") +
                                        item.title.ifBlank { item.previewText.ifBlank { "Untitled" } },
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

class NotesOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesOverviewWidget()
}
