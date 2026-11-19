package io.github.ultrapg.smartnote.integration.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.ultrapg.smartnote.MainActivity
import io.github.ultrapg.smartnote.domain.model.NoteSummary

enum class QuickCaptureLayoutMode {
    COMPACT_ICONS_ONLY,
    MEDIUM_PRIMARY_PLUS_TWO_ICONS,
    WIDE_PRIMARY_PLUS_THREE_ICONS,
    TALL_TWO_ROW;

    companion object {
        fun forSize(size: DpSize): QuickCaptureLayoutMode = when {
            size.height >= 104.dp -> TALL_TWO_ROW
            size.width < 190.dp -> COMPACT_ICONS_ONLY
            size.width < 280.dp -> MEDIUM_PRIMARY_PLUS_TWO_ICONS
            else -> WIDE_PRIMARY_PLUS_THREE_ICONS
        }
    }
}

object WidgetPreferencesStore {
    private const val PREFS_NAME = "smartnote_widget_prefs"
    private const val PREFIX_PINNED_NOTE = "pinned_note_"
    private const val PREFIX_CHECKLIST_NOTE = "checklist_note_"

    fun getPinnedNoteId(context: Context, appWidgetId: Int): String? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
        return runCatching {
            prefs(context).getString(pinnedKey(appWidgetId), null)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun setPinnedNoteId(context: Context, appWidgetId: Int, noteId: String?) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        runCatching {
            prefs(context).edit().apply {
                if (noteId.isNullOrBlank()) {
                    remove(pinnedKey(appWidgetId))
                } else {
                    putString(pinnedKey(appWidgetId), noteId)
                }
            }.commit()
        }
    }

    fun getChecklistNoteId(context: Context, appWidgetId: Int): String? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
        return runCatching {
            prefs(context).getString(checklistKey(appWidgetId), null)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun setChecklistNoteId(context: Context, appWidgetId: Int, noteId: String?) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        runCatching {
            prefs(context).edit().apply {
                if (noteId.isNullOrBlank()) {
                    remove(checklistKey(appWidgetId))
                } else {
                    putString(checklistKey(appWidgetId), noteId)
                }
            }.commit()
        }
    }

    fun clearWidget(context: Context, appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        runCatching {
            prefs(context).edit()
                .remove(pinnedKey(appWidgetId))
                .remove(checklistKey(appWidgetId))
                .commit()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun pinnedKey(appWidgetId: Int) = "$PREFIX_PINNED_NOTE$appWidgetId"
    private fun checklistKey(appWidgetId: Int) = "$PREFIX_CHECKLIST_NOTE$appWidgetId"
}

fun buildWidgetLaunchIntent(
    context: Context,
    action: String,
    noteId: String? = null
): Intent = Intent(context, MainActivity::class.java).apply {
    this.action = action
    data = Uri.parse("smartnote://widget/${Uri.encode(action)}/${Uri.encode(noteId ?: "default")}")
    if (!noteId.isNullOrBlank()) {
        putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
    }
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}

fun resolveTargetPinnedNote(
    summaries: List<NoteSummary>,
    configuredNoteId: String?
): NoteSummary? {
    val active = summaries.filter { !it.isBlankUntouchedGhost && !it.isArchived && !it.isDeleted }
    if (!configuredNoteId.isNullOrBlank()) {
        active.firstOrNull { it.id == configuredNoteId }?.let { return it }
    }
    return active.filter { it.isPinned }.maxByOrNull { it.updatedAtMs }
        ?: active.maxByOrNull { it.updatedAtMs }
}

fun resolveTargetChecklistNote(
    summaries: List<NoteSummary>,
    configuredNoteId: String?
): NoteSummary? {
    val checklists = summaries.filter {
        !it.isBlankUntouchedGhost && !it.isArchived && !it.isDeleted && it.hasChecklist
    }
    if (!configuredNoteId.isNullOrBlank()) {
        checklists.firstOrNull { it.id == configuredNoteId }?.let { return it }
    }
    return checklists.filter { it.isPinned }.maxByOrNull { it.updatedAtMs }
        ?: checklists.maxByOrNull { it.updatedAtMs }
}

fun resolveRecentOverviewNotes(
    summaries: List<NoteSummary>,
    limit: Int = 12
): List<NoteSummary> = summaries
    .asSequence()
    .filter { !it.isBlankUntouchedGhost && !it.isArchived && !it.isDeleted }
    .sortedWith(
        compareByDescending<NoteSummary> { it.isPinned }
            .thenByDescending { it.updatedAtMs }
    )
    .take(limit)
    .toList()
