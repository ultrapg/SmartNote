package io.github.ultrapg.smartnote.integration.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import io.github.ultrapg.smartnote.SmartNoteApplication
import io.github.ultrapg.smartnote.domain.model.AppSettings
import io.github.ultrapg.smartnote.domain.model.NoteFilterType
import io.github.ultrapg.smartnote.domain.model.NoteSortOrder
import io.github.ultrapg.smartnote.ui.designsystem.theme.SmartNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseWidgetNoteConfigActivity(
    private val checklistsOnly: Boolean
) : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val app = applicationContext as? SmartNoteApplication
        if (app == null) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            val settings by app.container.settingsRepository.settingsFlow.collectAsState(
                initial = AppSettings()
            )
            val allNotes by app.container.noteRepository.observeActiveNotes(
                NoteSortOrder.UPDATED_DESC,
                if (checklistsOnly) NoteFilterType.CHECKLISTS else NoteFilterType.ALL
            ).collectAsState(initial = emptyList())
            val notes = remember(allNotes, checklistsOnly) {
                allNotes.filter {
                    !it.isBlankUntouchedGhost && (!checklistsOnly || it.hasChecklist)
                }
            }
            val scope = rememberCoroutineScope()
            var isApplyingSelection by remember { mutableStateOf(false) }

            val applySelection: (String?) -> Unit = { selectedNoteId ->
                if (!isApplyingSelection) {
                    isApplyingSelection = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (checklistsOnly) {
                                WidgetPreferencesStore.setChecklistNoteId(
                                    this@BaseWidgetNoteConfigActivity,
                                    appWidgetId,
                                    selectedNoteId
                                )
                            } else {
                                WidgetPreferencesStore.setPinnedNoteId(
                                    this@BaseWidgetNoteConfigActivity,
                                    appWidgetId,
                                    selectedNoteId
                                )
                            }
                            val widget = if (checklistsOnly) ChecklistWidget() else PinnedNoteWidget()
                            val singleUpdateSucceeded = runCatching {
                                val manager = GlanceAppWidgetManager(this@BaseWidgetNoteConfigActivity)
                                val glanceId = manager.getGlanceIdBy(appWidgetId)
                                widget.update(this@BaseWidgetNoteConfigActivity, glanceId)
                            }.isSuccess
                            if (!singleUpdateSucceeded) {
                                runCatching { widget.updateAll(this@BaseWidgetNoteConfigActivity) }
                            }
                        }
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        )
                        finish()
                    }
                }
            }

            SmartNoteTheme(
                palette = settings.themePalette,
                darkModeConfig = settings.darkModeConfig,
                reducedMotionPreference = settings.reducedMotion
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    if (checklistsOnly) {
                                        "Choose checklist for widget"
                                    } else {
                                        "Choose note for widget"
                                    }
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Cancel"
                                    )
                                }
                            }
                        )
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "automatic_option") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isApplyingSelection) { applySelection(null) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (checklistsOnly) {
                                                "Automatic (latest checklist)"
                                            } else {
                                                "Automatic (latest pinned note)"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = if (checklistsOnly) {
                                                "Automatically shows your most recent pinned or updated checklist."
                                            } else {
                                                "Automatically shows your latest pinned note (or newest note if none are pinned)."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        if (notes.isEmpty()) {
                            item(key = "empty_hint") {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(
                                            text = if (checklistsOnly) {
                                                "No checklists yet"
                                            } else {
                                                "No notes yet"
                                            },
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Tap Automatic above to place the widget now—it will populate as soon as you create one.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(notes, key = { it.id }) { note ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isApplyingSelection) {
                                            applySelection(note.id)
                                        }
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (note.isPinned) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PushPin,
                                                    contentDescription = "Pinned",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = note.title.ifBlank { "Untitled note" },
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (note.hasChecklist && note.checklistTotalCount > 0) {
                                                Text(
                                                    text = "${note.checklistCheckedCount}/${note.checklistTotalCount}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        if (note.previewText.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = note.previewText.take(120),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
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
}

class PinnedNoteWidgetConfigActivity : BaseWidgetNoteConfigActivity(checklistsOnly = false)

class ChecklistWidgetConfigActivity : BaseWidgetNoteConfigActivity(checklistsOnly = true)
