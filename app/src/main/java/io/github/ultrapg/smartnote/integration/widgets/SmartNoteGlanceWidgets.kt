package io.github.ultrapg.smartnote.integration.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.ultrapg.smartnote.MainActivity
import io.github.ultrapg.smartnote.R
import io.github.ultrapg.smartnote.SmartNoteApplication
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.domain.model.ChecklistPreviewItem
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NoteDetail
import io.github.ultrapg.smartnote.domain.model.NoteSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetActionParams {
    val NOTE_ID = ActionParameters.Key<String>("smartnote_widget_note_id")
    val BLOCK_ID = ActionParameters.Key<String>("smartnote_widget_block_id")
}

object WidgetRefreshHelper {
    suspend fun refreshAllWidgets(context: Context) {
        val appContext = context.applicationContext ?: context
        runCatching { QuickNoteWidget().updateAll(appContext) }
        runCatching { QuickActionsGridWidget().updateAll(appContext) }
        runCatching { SmartSearchBarWidget().updateAll(appContext) }
        runCatching { PinnedNoteWidget().updateAll(appContext) }
        runCatching { ChecklistWidget().updateAll(appContext) }
        runCatching { NotesOverviewWidget().updateAll(appContext) }
    }
}

private fun launchMainAction(context: Context, action: String, noteId: String? = null) =
    actionStartActivity(buildWidgetLaunchIntent(context, action, noteId))

abstract class BaseSmartNoteGlanceWidget : GlanceAppWidget(R.layout.widget_error_fallback) {

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        Log.e("SmartNoteWidget", "Composition failed for widgetId=$appWidgetId", throwable)
        runCatching {
            val rv = RemoteViews(context.packageName, R.layout.widget_error_fallback)
            val openAppIntent = buildWidgetLaunchIntent(context, Intent.ACTION_MAIN)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.widget_error_root, pendingIntent)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, rv)
        }
    }

    protected fun safeAppWidgetId(context: Context, id: GlanceId): Int =
        runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)

    protected fun safeRepository(context: Context): NoteRepository? =
        (context.applicationContext as? SmartNoteApplication)?.container?.noteRepository

    protected suspend fun safeLoadActiveSummaries(context: Context): List<NoteSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                safeRepository(context)?.getAllSummariesSnapshot(includeArchived = false).orEmpty()
            }.getOrDefault(emptyList())
        }

    protected suspend fun safeLoadNoteDetail(context: Context, noteId: String): NoteDetail? =
        withContext(Dispatchers.IO) {
            runCatching {
                safeRepository(context)?.getNoteDetail(noteId)
            }.getOrNull()
        }
}

abstract class BaseSmartNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            WidgetPreferencesStore.clearWidget(context, appWidgetId)
        }
    }
}

class QuickNoteWidget : BaseSmartNoteGlanceWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(140.dp, 56.dp),
            DpSize(210.dp, 56.dp),
            DpSize(290.dp, 56.dp),
            DpSize(210.dp, 112.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val size = LocalSize.current
            val mode = QuickCaptureLayoutMode.forSize(size)
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .cornerRadius(24.dp)
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(10.dp)
                ) {
                    when (mode) {
                        QuickCaptureLayoutMode.COMPACT_ICONS_ONLY -> CompactQuickCaptureRow(context)
                        QuickCaptureLayoutMode.MEDIUM_PRIMARY_PLUS_TWO_ICONS -> MediumQuickCaptureRow(context)
                        QuickCaptureLayoutMode.WIDE_PRIMARY_PLUS_THREE_ICONS -> WideQuickCaptureRow(context)
                        QuickCaptureLayoutMode.TALL_TWO_ROW -> TallQuickCaptureGrid(context)
                    }
                }
            }
        }
    }

    @Composable
    private fun CompactQuickCaptureRow(context: Context) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_add,
                contentDescription = "New note",
                backgroundColor = GlanceTheme.colors.primaryContainer,
                tintColor = GlanceTheme.colors.onPrimaryContainer,
                action = launchMainAction(context, MainActivity.ACTION_NEW_NOTE),
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_checklist,
                contentDescription = "New checklist",
                backgroundColor = GlanceTheme.colors.secondaryContainer,
                tintColor = GlanceTheme.colors.onSecondaryContainer,
                action = launchMainAction(context, MainActivity.ACTION_NEW_CHECKLIST),
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_search,
                contentDescription = "SmartSearch",
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                tintColor = GlanceTheme.colors.onSurfaceVariant,
                action = launchMainAction(context, MainActivity.ACTION_OPEN_SEARCH),
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
        }
    }

    @Composable
    private fun MediumQuickCaptureRow(context: Context) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryNewNotePill(
                context = context,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_checklist,
                contentDescription = "New checklist",
                backgroundColor = GlanceTheme.colors.secondaryContainer,
                tintColor = GlanceTheme.colors.onSecondaryContainer,
                action = launchMainAction(context, MainActivity.ACTION_NEW_CHECKLIST),
                modifier = GlanceModifier.width(42.dp).fillMaxHeight()
            )
            Spacer(GlanceModifier.width(6.dp))
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_search,
                contentDescription = "SmartSearch",
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                tintColor = GlanceTheme.colors.onSurfaceVariant,
                action = launchMainAction(context, MainActivity.ACTION_OPEN_SEARCH),
                modifier = GlanceModifier.width(42.dp).fillMaxHeight()
            )
        }
    }

    @Composable
    private fun WideQuickCaptureRow(context: Context) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryNewNotePill(
                context = context,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_checklist,
                contentDescription = "New checklist",
                backgroundColor = GlanceTheme.colors.secondaryContainer,
                tintColor = GlanceTheme.colors.onSecondaryContainer,
                action = launchMainAction(context, MainActivity.ACTION_NEW_CHECKLIST),
                modifier = GlanceModifier.width(42.dp).fillMaxHeight()
            )
            Spacer(GlanceModifier.width(6.dp))
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_image,
                contentDescription = "New image note",
                backgroundColor = GlanceTheme.colors.tertiaryContainer,
                tintColor = GlanceTheme.colors.onTertiaryContainer,
                action = launchMainAction(context, MainActivity.ACTION_NEW_IMAGE_NOTE),
                modifier = GlanceModifier.width(42.dp).fillMaxHeight()
            )
            Spacer(GlanceModifier.width(6.dp))
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_search,
                contentDescription = "SmartSearch",
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                tintColor = GlanceTheme.colors.onSurfaceVariant,
                action = launchMainAction(context, MainActivity.ACTION_OPEN_SEARCH),
                modifier = GlanceModifier.width(42.dp).fillMaxHeight()
            )
        }
    }

    @Composable
    private fun TallQuickCaptureGrid(context: Context) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryNewNotePill(
                    context = context,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                WidgetIconButton(
                    iconRes = R.drawable.ic_widget_image,
                    contentDescription = "New image note",
                    backgroundColor = GlanceTheme.colors.tertiaryContainer,
                    tintColor = GlanceTheme.colors.onTertiaryContainer,
                    action = launchMainAction(context, MainActivity.ACTION_NEW_IMAGE_NOTE),
                    modifier = GlanceModifier.width(46.dp).fillMaxHeight()
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabeledActionPill(
                    iconRes = R.drawable.ic_widget_checklist,
                    label = "Checklist",
                    backgroundColor = GlanceTheme.colors.secondaryContainer,
                    contentColor = GlanceTheme.colors.onSecondaryContainer,
                    action = launchMainAction(context, MainActivity.ACTION_NEW_CHECKLIST),
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                LabeledActionPill(
                    iconRes = R.drawable.ic_widget_search,
                    label = "Search",
                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                    contentColor = GlanceTheme.colors.onSurfaceVariant,
                    action = launchMainAction(context, MainActivity.ACTION_OPEN_SEARCH),
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
            }
        }
    }

    @Composable
    private fun PrimaryNewNotePill(
        context: Context,
        modifier: GlanceModifier
    ) {
        Row(
            modifier = modifier
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .clickable(launchMainAction(context, MainActivity.ACTION_NEW_NOTE))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = "New note",
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun LabeledActionPill(
        iconRes: Int,
        label: String,
        backgroundColor: ColorProvider,
        contentColor: ColorProvider,
        action: androidx.glance.action.Action,
        modifier: GlanceModifier
    ) {
        Row(
            modifier = modifier
                .cornerRadius(16.dp)
                .background(backgroundColor)
                .clickable(action)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(contentColor)
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

class QuickNoteWidgetReceiver : BaseSmartNoteWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickNoteWidget()
}

class QuickActionsGridWidget : BaseSmartNoteGlanceWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 120.dp),
            DpSize(180.dp, 180.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val size = LocalSize.current
            val showLabels = size.width >= 115.dp && size.height >= 110.dp
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .cornerRadius(24.dp)
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    ) {
                        BentoActionTile(
                            iconRes = R.drawable.ic_widget_add,
                            label = "Note",
                            showLabel = showLabels,
                            backgroundColor = GlanceTheme.colors.primaryContainer,
                            contentColor = GlanceTheme.colors.onPrimaryContainer,
                            action = launchMainAction(context, MainActivity.ACTION_NEW_NOTE),
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        BentoActionTile(
                            iconRes = R.drawable.ic_widget_checklist,
                            label = "Checklist",
                            showLabel = showLabels,
                            backgroundColor = GlanceTheme.colors.secondaryContainer,
                            contentColor = GlanceTheme.colors.onSecondaryContainer,
                            action = launchMainAction(context, MainActivity.ACTION_NEW_CHECKLIST),
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                        )
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    ) {
                        BentoActionTile(
                            iconRes = R.drawable.ic_widget_image,
                            label = "Image",
                            showLabel = showLabels,
                            backgroundColor = GlanceTheme.colors.tertiaryContainer,
                            contentColor = GlanceTheme.colors.onTertiaryContainer,
                            action = launchMainAction(context, MainActivity.ACTION_NEW_IMAGE_NOTE),
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        BentoActionTile(
                            iconRes = R.drawable.ic_widget_search,
                            label = "Search",
                            showLabel = showLabels,
                            backgroundColor = GlanceTheme.colors.surfaceVariant,
                            contentColor = GlanceTheme.colors.onSurfaceVariant,
                            action = launchMainAction(context, MainActivity.ACTION_OPEN_SEARCH),
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun BentoActionTile(
        iconRes: Int,
        label: String,
        showLabel: Boolean,
        backgroundColor: ColorProvider,
        contentColor: ColorProvider,
        action: androidx.glance.action.Action,
        modifier: GlanceModifier
    ) {
        Column(
            modifier = modifier
                .cornerRadius(16.dp)
                .background(backgroundColor)
                .clickable(action)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = label,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(contentColor)
            )
            if (showLabel) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = label,
                    maxLines = 1,
                    style = TextStyle(
                        color = contentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

class QuickActionsGridWidgetReceiver : BaseSmartNoteWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsGridWidget()
}

class SmartSearchBarWidget : BaseSmartNoteGlanceWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(160.dp, 56.dp),
            DpSize(240.dp, 56.dp),
            DpSize(310.dp, 56.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val size = LocalSize.current
            GlanceTheme {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .cornerRadius(28.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .clickable(launchMainAction(context, MainActivity.ACTION_OPEN_SEARCH)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_search),
                            contentDescription = "Search SmartNote",
                            modifier = GlanceModifier.size(20.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                        )
                        Spacer(GlanceModifier.width(10.dp))
                        Text(
                            text = if (size.width >= 230.dp) "Search SmartNote..." else "Search...",
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetIconButton(
                        iconRes = R.drawable.ic_widget_add,
                        contentDescription = "New note",
                        backgroundColor = GlanceTheme.colors.primaryContainer,
                        tintColor = GlanceTheme.colors.onPrimaryContainer,
                        action = launchMainAction(context, MainActivity.ACTION_NEW_NOTE),
                        cornerRadiusDp = 20,
                        modifier = GlanceModifier.size(40.dp)
                    )
                }
            }
        }
    }
}

class SmartSearchBarWidgetReceiver : BaseSmartNoteWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmartSearchBarWidget()
}

class PinnedNoteWidget : BaseSmartNoteGlanceWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(160.dp, 110.dp),
            DpSize(240.dp, 140.dp),
            DpSize(280.dp, 200.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = safeAppWidgetId(context, id)
        val configuredNoteId = WidgetPreferencesStore.getPinnedNoteId(context, appWidgetId)
        val summaries = safeLoadActiveSummaries(context)
        val targetNote = resolveTargetPinnedNote(summaries, configuredNoteId)

        provideContent {
            val size = LocalSize.current
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .cornerRadius(24.dp)
                        .background(GlanceTheme.colors.widgetBackground)
                        .clickable(
                            if (targetNote != null) {
                                launchMainAction(context, MainActivity.ACTION_OPEN_NOTE, targetNote.id)
                            } else {
                                launchMainAction(context, MainActivity.ACTION_NEW_NOTE)
                            }
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_pin),
                            contentDescription = null,
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = targetNote?.title?.ifBlank { "SmartNote" } ?: "No pinned note",
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        val firstTag = targetNote?.tags?.firstOrNull()?.name
                        if (!firstTag.isNullOrBlank() && size.width >= 200.dp) {
                            Spacer(GlanceModifier.width(6.dp))
                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(10.dp)
                                    .background(GlanceTheme.colors.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "#$firstTag",
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onPrimaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    if (targetNote != null &&
                        targetNote.checklistPreview.isNotEmpty() &&
                        targetNote.previewText.isBlank()
                    ) {
                        val maxItems = if (size.height >= 165.dp) 4 else 2
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            targetNote.checklistPreview.take(maxItems).forEachIndexed { idx, item ->
                                if (idx > 0) Spacer(GlanceModifier.height(4.dp))
                                PinnedChecklistPreviewRow(item)
                            }
                        }
                    } else {
                        val maxBodyLines = if (size.height >= 165.dp) 6 else 4
                        Text(
                            text = targetNote?.previewText?.ifBlank { "Tap to open in SmartNote" }
                                ?: "Tap to create your first note",
                            maxLines = maxBodyLines,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PinnedChecklistPreviewRow(item: ChecklistPreviewItem) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(
                    if (item.isChecked) {
                        R.drawable.ic_widget_checkbox_checked
                    } else {
                        R.drawable.ic_widget_checkbox_unchecked
                    }
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(15.dp),
                colorFilter = ColorFilter.tint(
                    if (item.isChecked) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
                )
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = item.text.ifBlank { "Item" },
                maxLines = 1,
                style = TextStyle(
                    color = if (item.isChecked) {
                        GlanceTheme.colors.onSurfaceVariant
                    } else {
                        GlanceTheme.colors.onSurface
                    },
                    fontSize = 12.sp,
                    textDecoration = if (item.isChecked) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    }
                )
            )
        }
    }
}

class PinnedNoteWidgetReceiver : BaseSmartNoteWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PinnedNoteWidget()
}

class ChecklistWidget : BaseSmartNoteGlanceWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 150.dp),
            DpSize(250.dp, 210.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = safeAppWidgetId(context, id)
        val configuredNoteId = WidgetPreferencesStore.getChecklistNoteId(context, appWidgetId)
        val summaries = safeLoadActiveSummaries(context)
        val targetSummary = resolveTargetChecklistNote(summaries, configuredNoteId)
        val noteDetail = targetSummary?.let { safeLoadNoteDetail(context, it.id) }
        val checklistBlocks = noteDetail?.blocks
            ?.filter { it.type == NoteBlockType.CHECKLIST && it.textContent.isNotBlank() }
            ?.ifEmpty { noteDetail.blocks.filter { it.type == NoteBlockType.CHECKLIST } }
            .orEmpty()

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .cornerRadius(24.dp)
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(14.dp)
                ) {
                    val headerClickAction = if (targetSummary != null) {
                        launchMainAction(context, MainActivity.ACTION_OPEN_NOTE, targetSummary.id)
                    } else {
                        launchMainAction(context, MainActivity.ACTION_NEW_CHECKLIST)
                    }
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(headerClickAction),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_checklist),
                            contentDescription = null,
                            modifier = GlanceModifier.size(18.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = targetSummary?.title?.ifBlank { "Checklist" } ?: "Checklist",
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            if (checklistBlocks.isNotEmpty()) {
                                val doneCount = checklistBlocks.count { it.isChecked }
                                Text(
                                    text = "$doneCount / ${checklistBlocks.size} done",
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetIconButton(
                            iconRes = if (targetSummary != null) R.drawable.ic_widget_note else R.drawable.ic_widget_add,
                            contentDescription = if (targetSummary != null) "Open checklist" else "New checklist",
                            backgroundColor = GlanceTheme.colors.primaryContainer,
                            tintColor = GlanceTheme.colors.onPrimaryContainer,
                            action = headerClickAction,
                            cornerRadiusDp = 12,
                            modifier = GlanceModifier.size(32.dp)
                        )
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    if (targetSummary == null || checklistBlocks.isEmpty()) {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                                .cornerRadius(14.dp)
                                .background(GlanceTheme.colors.surfaceVariant)
                                .clickable(headerClickAction)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (targetSummary == null) {
                                    "No checklist yet"
                                } else {
                                    "No items yet"
                                },
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                text = if (targetSummary == null) {
                                    "Tap to create a checklist note"
                                } else {
                                    "Tap to add checklist items"
                                },
                                maxLines = 2,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                        ) {
                            itemsIndexed(
                                items = checklistBlocks,
                                itemId = { index, _ -> index.toLong() + 1L }
                            ) { _, block ->
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Row(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .cornerRadius(12.dp)
                                            .background(GlanceTheme.colors.surfaceVariant)
                                            .clickable(
                                                actionRunCallback<ToggleChecklistItemCallback>(
                                                    actionParametersOf(
                                                        WidgetActionParams.NOTE_ID to targetSummary.id,
                                                        WidgetActionParams.BLOCK_ID to block.id
                                                    )
                                                )
                                            )
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            provider = ImageProvider(
                                                if (block.isChecked) {
                                                    R.drawable.ic_widget_checkbox_checked
                                                } else {
                                                    R.drawable.ic_widget_checkbox_unchecked
                                                }
                                            ),
                                            contentDescription = if (block.isChecked) "Checked" else "Unchecked",
                                            modifier = GlanceModifier.size(18.dp),
                                            colorFilter = ColorFilter.tint(
                                                if (block.isChecked) {
                                                    GlanceTheme.colors.primary
                                                } else {
                                                    GlanceTheme.colors.onSurfaceVariant
                                                }
                                            )
                                        )
                                        Spacer(GlanceModifier.width(8.dp))
                                        Text(
                                            text = block.textContent.ifBlank { "Untitled item" },
                                            maxLines = 2,
                                            modifier = GlanceModifier.defaultWeight(),
                                            style = TextStyle(
                                                color = if (block.isChecked) {
                                                    GlanceTheme.colors.onSurfaceVariant
                                                } else {
                                                    GlanceTheme.colors.onSurface
                                                },
                                                fontSize = 13.sp,
                                                textDecoration = if (block.isChecked) {
                                                    TextDecoration.LineThrough
                                                } else {
                                                    TextDecoration.None
                                                }
                                            )
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

class ChecklistWidgetReceiver : BaseSmartNoteWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChecklistWidget()
}

class ToggleChecklistItemCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val noteId = parameters[WidgetActionParams.NOTE_ID] ?: return
        val blockId = parameters[WidgetActionParams.BLOCK_ID] ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val repo = (context.applicationContext as? SmartNoteApplication)
                    ?.container
                    ?.noteRepository
                    ?: return@runCatching
                repo.toggleChecklistBlock(noteId, blockId)
                ChecklistWidget().update(context, glanceId)
                WidgetRefreshHelper.refreshAllWidgets(context)
            }
        }
    }
}

class NotesOverviewWidget : BaseSmartNoteGlanceWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(220.dp, 160.dp),
            DpSize(280.dp, 220.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val summaries = safeLoadActiveSummaries(context)
        val recent = resolveRecentOverviewNotes(summaries, limit = 12)

        provideContent {
            val size = LocalSize.current
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .cornerRadius(24.dp)
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SmartNote",
                            maxLines = 1,
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(launchMainAction(context, Intent.ACTION_MAIN)),
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        WidgetIconButton(
                            iconRes = R.drawable.ic_widget_search,
                            contentDescription = "SmartSearch",
                            backgroundColor = GlanceTheme.colors.surfaceVariant,
                            tintColor = GlanceTheme.colors.onSurfaceVariant,
                            action = launchMainAction(context, MainActivity.ACTION_OPEN_SEARCH),
                            cornerRadiusDp = 12,
                            modifier = GlanceModifier.size(32.dp)
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Row(
                            modifier = GlanceModifier
                                .height(32.dp)
                                .cornerRadius(12.dp)
                                .background(GlanceTheme.colors.primaryContainer)
                                .clickable(launchMainAction(context, MainActivity.ACTION_NEW_NOTE))
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_add),
                                contentDescription = null,
                                modifier = GlanceModifier.size(14.dp),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                            )
                            Spacer(GlanceModifier.width(4.dp))
                            Text(
                                text = "New",
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onPrimaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    if (recent.isEmpty()) {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                                .cornerRadius(14.dp)
                                .background(GlanceTheme.colors.surfaceVariant)
                                .clickable(launchMainAction(context, MainActivity.ACTION_NEW_NOTE))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "No notes yet",
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                text = "Tap + New to capture your first note",
                                maxLines = 2,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                        ) {
                            itemsIndexed(
                                items = recent,
                                itemId = { index, _ -> index.toLong() + 1L }
                            ) { _, item ->
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Column(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .cornerRadius(14.dp)
                                            .background(GlanceTheme.colors.surfaceVariant)
                                            .clickable(
                                                launchMainAction(
                                                    context = context,
                                                    action = MainActivity.ACTION_OPEN_NOTE,
                                                    noteId = item.id
                                                )
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = GlanceModifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (item.isPinned) {
                                                Image(
                                                    provider = ImageProvider(R.drawable.ic_widget_pin),
                                                    contentDescription = "Pinned",
                                                    modifier = GlanceModifier.size(13.dp),
                                                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                                                )
                                                Spacer(GlanceModifier.width(5.dp))
                                            }
                                            Text(
                                                text = item.title.ifBlank {
                                                    item.previewText.ifBlank { "Untitled" }
                                                },
                                                maxLines = 1,
                                                modifier = GlanceModifier.defaultWeight(),
                                                style = TextStyle(
                                                    color = GlanceTheme.colors.onSurface,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            if (item.hasChecklist && item.checklistTotalCount > 0) {
                                                Spacer(GlanceModifier.width(6.dp))
                                                Text(
                                                    text = "${item.checklistCheckedCount}/${item.checklistTotalCount}",
                                                    maxLines = 1,
                                                    style = TextStyle(
                                                        color = GlanceTheme.colors.primary,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            }
                                        }
                                        val snippet = item.previewText.takeIf {
                                            it.isNotBlank() && it != item.title && size.height >= 170.dp
                                        }
                                        if (snippet != null) {
                                            Spacer(GlanceModifier.height(2.dp))
                                            Text(
                                                text = snippet,
                                                maxLines = 1,
                                                style = TextStyle(
                                                    color = GlanceTheme.colors.onSurfaceVariant,
                                                    fontSize = 12.sp
                                                )
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

class NotesOverviewWidgetReceiver : BaseSmartNoteWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesOverviewWidget()
}

@Composable
private fun WidgetIconButton(
    iconRes: Int,
    contentDescription: String,
    backgroundColor: ColorProvider,
    tintColor: ColorProvider,
    action: androidx.glance.action.Action,
    modifier: GlanceModifier,
    cornerRadiusDp: Int = 16
) {
    Box(
        modifier = modifier
            .cornerRadius(cornerRadiusDp.dp)
            .background(backgroundColor)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(tintColor)
        )
    }
}
