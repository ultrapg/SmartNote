package io.github.ultrapg.smartnote.integration

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.MainActivity
import io.github.ultrapg.smartnote.R
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.integration.widgets.ChecklistWidgetConfigActivity
import io.github.ultrapg.smartnote.integration.widgets.ChecklistWidgetReceiver
import io.github.ultrapg.smartnote.integration.widgets.NotesOverviewWidgetReceiver
import io.github.ultrapg.smartnote.integration.widgets.PinnedNoteWidgetConfigActivity
import io.github.ultrapg.smartnote.integration.widgets.PinnedNoteWidgetReceiver
import io.github.ultrapg.smartnote.integration.widgets.QuickActionsGridWidgetReceiver
import io.github.ultrapg.smartnote.integration.widgets.QuickCaptureLayoutMode
import io.github.ultrapg.smartnote.integration.widgets.QuickNoteWidgetReceiver
import io.github.ultrapg.smartnote.integration.widgets.SmartSearchBarWidgetReceiver
import io.github.ultrapg.smartnote.integration.widgets.WidgetPreferencesStore
import io.github.ultrapg.smartnote.integration.widgets.buildWidgetLaunchIntent
import io.github.ultrapg.smartnote.integration.widgets.resolveRecentOverviewNotes
import io.github.ultrapg.smartnote.integration.widgets.resolveTargetChecklistNote
import io.github.ultrapg.smartnote.integration.widgets.resolveTargetPinnedNote
import io.github.ultrapg.smartnote.search.SearchIndexer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetsAndIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: SmartNoteDatabase
    private lateinit var storage: AttachmentStorage
    private lateinit var indexer: SearchIndexer
    private lateinit var tagRepo: TagRepository
    private lateinit var noteRepository: NoteRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = SmartNoteDatabase.createInMemory(context)
        storage = AttachmentStorage(context)
        indexer = SearchIndexer(database)
        tagRepo = TagRepository(database, indexer)
        noteRepository = NoteRepository(database, storage, indexer, tagRepo)
    }

    @After
    fun tearDown() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        database.close()
        storage.attachmentsDir.deleteRecursively()
        storage.thumbnailsDir.deleteRecursively()
    }

    @Test
    fun allWidgetReceiversAndConfigActivitiesAreProperlyExportedInManifest() {
        val pm = context.packageManager
        val expectedReceivers = listOf(
            QuickNoteWidgetReceiver::class.java,
            QuickActionsGridWidgetReceiver::class.java,
            SmartSearchBarWidgetReceiver::class.java,
            PinnedNoteWidgetReceiver::class.java,
            ChecklistWidgetReceiver::class.java,
            NotesOverviewWidgetReceiver::class.java
        )

        val configureActivitiesByName = pm.queryIntentActivities(
            Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setPackage(context.packageName),
            PackageManager.ResolveInfoFlags.of(0L)
        ).mapNotNull { it.activityInfo }.associateBy { it.name }

        expectedReceivers.forEach { receiverClass ->
            val component = ComponentName(context, receiverClass)
            val receiverInfo = pm.getReceiverInfo(
                component,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
            assertTrue("${receiverClass.simpleName} must be enabled", receiverInfo.enabled)
            assertTrue("${receiverClass.simpleName} must be exported", receiverInfo.exported)

            val providerXmlRes = receiverInfo.metaData?.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER) ?: 0
            assertTrue("${receiverClass.simpleName} missing appwidget provider XML", providerXmlRes != 0)

            val configuredActivityClassName = readConfigureAttributeFromProviderXml(providerXmlRes)
            if (!configuredActivityClassName.isNullOrBlank()) {
                val activityInfo = configureActivitiesByName[configuredActivityClassName]
                assertNotNull(
                    "$configuredActivityClassName must declare APPWIDGET_CONFIGURE intent-filter",
                    activityInfo
                )
                assertTrue("$configuredActivityClassName must be enabled", activityInfo!!.enabled)
                assertTrue(
                    "$configuredActivityClassName must be exported to prevent SecurityException",
                    activityInfo.exported
                )
            }
        }

        assertTrue(PinnedNoteWidgetConfigActivity::class.java.name in configureActivitiesByName)
        assertTrue(ChecklistWidgetConfigActivity::class.java.name in configureActivitiesByName)
    }

    @Test
    fun allWidgetPreviewAndFallbackLayoutsInflateCleanly() {
        val inflater = LayoutInflater.from(context)
        val layoutIds = listOf(
            R.layout.widget_error_fallback,
            R.layout.widget_preview_quick_note,
            R.layout.widget_preview_quick_actions,
            R.layout.widget_preview_search_bar,
            R.layout.widget_preview_pinned_note,
            R.layout.widget_preview_checklist,
            R.layout.widget_preview_overview
        )
        layoutIds.forEach { layoutId ->
            val view = inflater.inflate(layoutId, null)
            assertNotNull("Failed to inflate layout $layoutId", view)
        }
        val errorView = inflater.inflate(R.layout.widget_error_fallback, null)
        assertNotNull(errorView.findViewById<android.view.View>(R.id.widget_error_root))
    }

    @Test
    fun quickCaptureLayoutModesAdaptAcrossOneUiAndLauncherWidths() {
        assertEquals(
            QuickCaptureLayoutMode.COMPACT_ICONS_ONLY,
            QuickCaptureLayoutMode.forSize(DpSize(140.dp, 56.dp))
        )
        assertEquals(
            QuickCaptureLayoutMode.MEDIUM_PRIMARY_PLUS_TWO_ICONS,
            QuickCaptureLayoutMode.forSize(DpSize(210.dp, 56.dp))
        )
        assertEquals(
            QuickCaptureLayoutMode.WIDE_PRIMARY_PLUS_THREE_ICONS,
            QuickCaptureLayoutMode.forSize(DpSize(290.dp, 56.dp))
        )
        assertEquals(
            QuickCaptureLayoutMode.WIDE_PRIMARY_PLUS_THREE_ICONS,
            QuickCaptureLayoutMode.forSize(DpSize(360.dp, 56.dp))
        )
        assertEquals(
            QuickCaptureLayoutMode.TALL_TWO_ROW,
            QuickCaptureLayoutMode.forSize(DpSize(210.dp, 120.dp))
        )
    }

    @Test
    fun buildWidgetLaunchIntentProducesUniqueFilterUrisAndExtras() {
        val openNote1 = buildWidgetLaunchIntent(context, MainActivity.ACTION_OPEN_NOTE, "note-1")
        val openNote2 = buildWidgetLaunchIntent(context, MainActivity.ACTION_OPEN_NOTE, "note-2")
        assertFalse(openNote1.filterEquals(openNote2))
        assertEquals("note-1", openNote1.getStringExtra(MainActivity.EXTRA_NOTE_ID))
        assertEquals("note-2", openNote2.getStringExtra(MainActivity.EXTRA_NOTE_ID))
        assertEquals("smartnote", openNote1.data?.scheme)

        val newImage = buildWidgetLaunchIntent(context, MainActivity.ACTION_NEW_IMAGE_NOTE)
        val openSearch = buildWidgetLaunchIntent(context, MainActivity.ACTION_OPEN_SEARCH)
        assertFalse(newImage.filterEquals(openSearch))
        assertEquals(MainActivity.ACTION_NEW_IMAGE_NOTE, newImage.action)
        assertEquals(MainActivity.ACTION_OPEN_SEARCH, openSearch.action)
    }

    @Test
    fun widgetPreferencesStoreSavesReadsAndClearsPerWidgetId() {
        WidgetPreferencesStore.setPinnedNoteId(context, 101, "note-alpha")
        WidgetPreferencesStore.setPinnedNoteId(context, 102, "note-beta")
        WidgetPreferencesStore.setChecklistNoteId(context, 201, "checklist-gamma")

        assertEquals("note-alpha", WidgetPreferencesStore.getPinnedNoteId(context, 101))
        assertEquals("note-beta", WidgetPreferencesStore.getPinnedNoteId(context, 102))
        assertEquals("checklist-gamma", WidgetPreferencesStore.getChecklistNoteId(context, 201))

        WidgetPreferencesStore.setPinnedNoteId(context, 101, null)
        assertNull(WidgetPreferencesStore.getPinnedNoteId(context, 101))

        WidgetPreferencesStore.clearWidget(context, 102)
        WidgetPreferencesStore.clearWidget(context, 201)
        assertNull(WidgetPreferencesStore.getPinnedNoteId(context, 102))
        assertNull(WidgetPreferencesStore.getChecklistNoteId(context, 201))

        WidgetPreferencesStore.setPinnedNoteId(context, AppWidgetManager.INVALID_APPWIDGET_ID, "ignored")
        assertNull(WidgetPreferencesStore.getPinnedNoteId(context, AppWidgetManager.INVALID_APPWIDGET_ID))
    }

    @Test
    fun widgetSelectionResolversAndChecklistToggleWorkEndToEndWithDatabase() = runTest {
        noteRepository.createNote(id = "ghost-blank", title = "", isPinned = false)
        val plainId = noteRepository.createNote(
            id = "plain-1",
            title = "Plain Note",
            blocks = listOf(NoteBlock("b-plain", "plain-1", 0, NoteBlockType.TEXT, "Body text")),
            isPinned = false
        )
        val pinnedId = noteRepository.createNote(
            id = "pinned-1",
            title = "Important Note",
            blocks = listOf(NoteBlock("b-pin", "pinned-1", 0, NoteBlockType.TEXT, "Keep handy")),
            isPinned = true
        )
        Thread.sleep(5)
        val checklistPinnedId = noteRepository.createNote(
            id = "check-pinned",
            title = "Groceries",
            blocks = listOf(
                NoteBlock("chk-1", "check-pinned", 0, NoteBlockType.CHECKLIST, "Coffee", isChecked = false),
                NoteBlock("chk-2", "check-pinned", 1, NoteBlockType.CHECKLIST, "Bread", isChecked = true)
            ),
            isPinned = true
        )
        val checklistOtherId = noteRepository.createNote(
            id = "check-other",
            title = "Packing",
            blocks = listOf(
                NoteBlock("chk-3", "check-other", 0, NoteBlockType.CHECKLIST, "Charger", isChecked = false)
            ),
            isPinned = false
        )

        val summaries = noteRepository.getAllSummariesSnapshot(includeArchived = false)
        val recent = resolveRecentOverviewNotes(summaries)
        assertFalse(recent.any { it.id == "ghost-blank" })
        assertTrue(recent.take(2).all { it.isPinned })

        assertEquals(plainId, resolveTargetPinnedNote(summaries, plainId)?.id)
        assertEquals(checklistPinnedId, resolveTargetPinnedNote(summaries, "missing-id")?.id)

        assertEquals(checklistOtherId, resolveTargetChecklistNote(summaries, checklistOtherId)?.id)
        assertEquals(checklistPinnedId, resolveTargetChecklistNote(summaries, null)?.id)

        noteRepository.toggleChecklistBlock(checklistPinnedId, "chk-1")
        val afterToggle = noteRepository.getNoteDetail(checklistPinnedId)!!
        assertTrue(afterToggle.blocks.first { it.id == "chk-1" }.isChecked)

        noteRepository.toggleChecklistBlock(checklistPinnedId, "chk-1")
        val afterSecondToggle = noteRepository.getNoteDetail(checklistPinnedId)!!
        assertFalse(afterSecondToggle.blocks.first { it.id == "chk-1" }.isChecked)
        assertEquals(pinnedId, resolveTargetPinnedNote(summaries, pinnedId)?.id)
    }

    @Test
    fun widgetConfigActivitiesHandleInvalidAppWidgetIdWithoutCrashing() {
        val pinnedController = Robolectric.buildActivity(PinnedNoteWidgetConfigActivity::class.java, Intent()).create()
        val pinnedActivity = pinnedController.get()
        assertTrue(pinnedActivity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(pinnedActivity).resultCode)

        val checklistController = Robolectric.buildActivity(ChecklistWidgetConfigActivity::class.java, Intent()).create()
        val checklistActivity = checklistController.get()
        assertTrue(checklistActivity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(checklistActivity).resultCode)
    }

    private fun readConfigureAttributeFromProviderXml(xmlResId: Int): String? {
        val parser = context.resources.getXml(xmlResId)
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "configure") {
                            return parser.getAttributeValue(i)
                        }
                    }
                }
                eventType = parser.next()
            }
        } finally {
            parser.close()
        }
        return null
    }
}
