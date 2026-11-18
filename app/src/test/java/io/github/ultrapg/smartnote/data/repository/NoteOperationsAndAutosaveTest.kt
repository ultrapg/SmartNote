package io.github.ultrapg.smartnote.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.capture.AttachmentImporter
import io.github.ultrapg.smartnote.capture.NoteShareExporter
import io.github.ultrapg.smartnote.data.local.AttachmentEntity
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.duplicates.SmartDuplicateDetector
import io.github.ultrapg.smartnote.reminder.ReminderNotificationBuilder
import io.github.ultrapg.smartnote.reminder.ReminderScheduler
import io.github.ultrapg.smartnote.search.SearchIndexer
import io.github.ultrapg.smartnote.ui.editor.NoteAutosaveCoordinator
import io.github.ultrapg.smartnote.ui.editor.NoteEditorViewModel
import io.github.ultrapg.smartnote.ui.editor.PendingNoteDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class NoteOperationsAndAutosaveTest {

    private lateinit var context: Context
    private lateinit var database: SmartNoteDatabase
    private lateinit var storage: AttachmentStorage
    private lateinit var searchIndexer: SearchIndexer
    private lateinit var tagRepository: TagRepository
    private lateinit var noteRepository: NoteRepository
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = SmartNoteDatabase.createInMemory(context)
        storage = AttachmentStorage(context)
        searchIndexer = SearchIndexer(database)
        tagRepository = TagRepository(database, searchIndexer)
        noteRepository = NoteRepository(database, storage, searchIndexer, tagRepository)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        storage.attachmentsDir.deleteRecursively()
        storage.thumbnailsDir.deleteRecursively()
    }

    @Test
    fun recycleBinPreservesAttachmentUntilPermanentPurgeAndRespectsSharedReferences() = runTest {
        val attId = UUID.randomUUID().toString()
        val relPath = "attachments/$attId.png"
        val file = storage.resolvePrivateFile(relPath).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        assertTrue(file.exists())

        val noteId = noteRepository.createNote(
            title = "Receipt photo",
            blocks = listOf(
                NoteBlock("b1", "n", 0, NoteBlockType.IMAGE, attachmentId = attId)
            ),
            attachments = listOf(
                AttachmentEntity(
                    id = attId,
                    noteId = "placeholder",
                    relativeFilePath = relPath,
                    thumbnailRelativePath = null,
                    mimeType = "image/png",
                    originalDisplayName = "receipt.png",
                    byteSize = 4L,
                    widthPx = 100,
                    heightPx = 100,
                    sha256 = "deadbeef",
                    isScreenshot = true,
                    createdAtMs = System.currentTimeMillis()
                )
            ),
            scheduleBackgroundWork = false
        ).also { createdId ->
            // Ensure attachment entity matches createdId (NoteRepository.createNote uses passed id when matching)
            database.attachmentDao().upsertAttachment(
                AttachmentEntity(
                    id = attId,
                    noteId = createdId,
                    relativeFilePath = relPath,
                    thumbnailRelativePath = null,
                    mimeType = "image/png",
                    originalDisplayName = "receipt.png",
                    byteSize = 4L,
                    widthPx = 100,
                    heightPx = 100,
                    sha256 = "deadbeef",
                    isScreenshot = true,
                    createdAtMs = System.currentTimeMillis()
                )
            )
        }

        // Duplicate note shares the underlying attachment path safely
        val copyId = noteRepository.duplicateNote(noteId)!!
        assertNotNull(noteRepository.getNoteDetail(copyId))

        // Move original to Recycle Bin: file MUST survive
        noteRepository.moveToRecycleBin(noteId)
        assertTrue(file.exists())
        assertEquals(1, noteRepository.observeDeletedNotes().first().size)

        // Permanently deleting original MUST keep file because copy still references it
        noteRepository.deletePermanently(noteId)
        assertNull(noteRepository.getNoteDetail(noteId))
        assertTrue(file.exists())

        // Permanently deleting copy NOW deletes the unreferenced file
        noteRepository.deletePermanently(copyId)
        assertFalse(file.exists())
    }

    @Test
    fun pinArchiveAndThirtyDayExpirationWorkAccurately() = runTest {
        val id = noteRepository.createNote(
            title = "Lifecycle Test",
            blocks = listOf(NoteBlock("b1", "id", 0, NoteBlockType.CHECKLIST, "Buy milk", false)),
            scheduleBackgroundWork = false
        )
        noteRepository.setPinned(id, true)
        assertTrue(noteRepository.getNoteDetail(id)!!.isPinned)

        noteRepository.setArchived(id, true)
        val afterArchive = noteRepository.getNoteDetail(id)!!
        assertTrue(afterArchive.isArchived)
        assertFalse(afterArchive.isPinned)

        // Simulate note deleted 31 days ago -> expires
        val now = System.currentTimeMillis()
        val oldDeletedMs = now - 31L * 24L * 60L * 60L * 1000L
        database.noteDao().moveToRecycleBin(id, oldDeletedMs)
        val purgedCount = noteRepository.purgeExpiredRecycleBin(retentionDays = 30, nowMs = now)
        assertEquals(1, purgedCount)
        assertNull(noteRepository.getNoteDetail(id))
    }

    @Test
    fun autosaveCoordinatorCoalescesRapidTypingAndFlushesOnDemand() = runTest {
        val noteId = noteRepository.createNote(title = "Initial", scheduleBackgroundWork = false)
        val coordinator = NoteAutosaveCoordinator(noteRepository, scope, debounceMs = 150L)

        repeat(40) { i ->
            coordinator.scheduleSave(
                PendingNoteDraft(
                    noteId = noteId,
                    title = "Draft $i",
                    blocks = listOf(NoteBlock("b1", noteId, 0, NoteBlockType.TEXT, "Typing $i")),
                    isPinned = false,
                    revisionToken = i.toLong() + 1L
                )
            )
        }
        // Immediate flush must persist final draft "Draft 39" without losing characters
        coordinator.flushNow(noteId)
        delay(200L)

        val reloaded = noteRepository.getNoteDetail(noteId)!!
        assertEquals("Draft 39", reloaded.title)
        assertEquals("Typing 39", reloaded.blocks.first().textContent)
    }

    @Test
    fun blankUntouchedGhostNotesArePrunedWhilePinnedOrTaggedNotesSurvive() = runTest {
        val blankGhostId = noteRepository.createNote(
            title = "",
            blocks = listOf(NoteBlock("b_blank", "id", 0, NoteBlockType.TEXT, "   ")),
            scheduleBackgroundWork = false
        )
        val pinnedBlankId = noteRepository.createNote(
            title = "",
            blocks = listOf(NoteBlock("b_pin", "id", 0, NoteBlockType.TEXT, "")),
            isPinned = true,
            scheduleBackgroundWork = false
        )
        val taggedBlankId = noteRepository.createNote(
            title = "",
            blocks = listOf(NoteBlock("b_tag", "id", 0, NoteBlockType.TEXT, "")),
            initialTags = listOf("Inbox"),
            scheduleBackgroundWork = false
        )
        val contentNoteId = noteRepository.createNote(
            title = "",
            blocks = listOf(NoteBlock("b_content", "id", 0, NoteBlockType.TEXT, "Keep this")),
            scheduleBackgroundWork = false
        )

        val prunedCount = noteRepository.pruneBlankUntouchedNotes()
        assertEquals(1, prunedCount)
        assertNull(noteRepository.getNoteDetail(blankGhostId))
        assertNotNull(noteRepository.getNoteDetail(pinnedBlankId))
        assertNotNull(noteRepository.getNoteDetail(taggedBlankId))
        assertNotNull(noteRepository.getNoteDetail(contentNoteId))

        // Clearing content note down to blank makes discardIfCompletelyBlank delete it
        assertFalse(noteRepository.discardIfCompletelyBlank(contentNoteId))
        noteRepository.saveNoteContent(
            noteId = contentNoteId,
            title = "",
            blocks = listOf(NoteBlock("b_content", contentNoteId, 0, NoteBlockType.TEXT, "")),
            scheduleBackgroundWork = false
        )
        assertTrue(noteRepository.discardIfCompletelyBlank(contentNoteId))
        assertNull(noteRepository.getNoteDetail(contentNoteId))
    }

    private fun createEditorHarness(
        dsFile: File,
        debounceMs: Long = 150L
    ): NoteEditorViewModel {
        val settingsRepo = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { dsFile })
        )
        val coordinator = NoteAutosaveCoordinator(noteRepository, scope, debounceMs = debounceMs)
        return NoteEditorViewModel(
            initialNoteId = null,
            startChecklist = false,
            fromQuickTile = false,
            savedStateHandle = SavedStateHandle(),
            noteRepository = noteRepository,
            tagRepository = tagRepository,
            autosaveCoordinator = coordinator,
            attachmentImporter = AttachmentImporter(context, storage),
            noteShareExporter = NoteShareExporter(context, storage),
            reminderScheduler = ReminderScheduler(
                context,
                database,
                ReminderNotificationBuilder(context, settingsRepo)
            ),
            duplicateDetector = SmartDuplicateDetector(database, noteRepository),
            settingsRepository = settingsRepo
        )
    }

    @Test
    fun addingImageToBlankNewNoteInEditorPersistsWithoutForeignKeyCrash() = runBlocking {
        val dsFile = File(context.cacheDir, "editor_img_test_${System.nanoTime()}.preferences_pb")
        val samplePng = File.createTempFile("picker_blank_", ".png")
        try {
            val bmp = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888)
            FileOutputStream(samplePng).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()

            val vm = createEditorHarness(dsFile, debounceMs = 150L)
            ShadowLooper.idleMainLooper()

            // Simulate Photo Picker opening -> Activity ON_STOP calls flushPendingEditsOnly()
            vm.flushPendingEditsOnly()
            ShadowLooper.idleMainLooper()

            // Pick an image on an untouched blank note (previously caused FOREIGN KEY constraint crash)
            vm.importImages(listOf(Uri.fromFile(samplePng)))
            val deadline = System.currentTimeMillis() + 5_000L
            while (vm.uiState.value.attachments.isEmpty() && System.currentTimeMillis() < deadline) {
                ShadowLooper.idleMainLooper()
                delay(20L)
            }
            assertEquals(1, vm.uiState.value.attachments.size)
            assertTrue(vm.uiState.value.blocks.any { it.type == NoteBlockType.IMAGE })

            vm.finishEditorSession()
            ShadowLooper.idleMainLooper()
            delay(100L)

            val noteId = vm.uiState.value.noteId
            val persisted = noteRepository.getNoteDetail(noteId)
            assertNotNull(persisted)
            assertEquals(1, persisted!!.attachments.size)
            assertTrue(persisted.blocks.any { it.type == NoteBlockType.IMAGE })
        } finally {
            dsFile.delete()
            samplePng.delete()
        }
    }

    @Test
    fun addingImageWhileLocalTextPendingPreservesBothTextAndImage() = runBlocking {
        val dsFile = File(context.cacheDir, "editor_img_text_${System.nanoTime()}.preferences_pb")
        val samplePng = File.createTempFile("picker_text_", ".png")
        try {
            val bmp = Bitmap.createBitmap(180, 120, Bitmap.Config.ARGB_8888)
            FileOutputStream(samplePng).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()

            val vm = createEditorHarness(dsFile, debounceMs = 400L)
            ShadowLooper.idleMainLooper()

            val firstBlockId = vm.uiState.value.blocks.first().id
            vm.updateBlockText(firstBlockId, "Photo notes before picker")
            vm.flushPendingEditsOnly()
            ShadowLooper.idleMainLooper()

            vm.importImages(listOf(Uri.fromFile(samplePng)))
            val deadline = System.currentTimeMillis() + 5_000L
            while (vm.uiState.value.attachments.isEmpty() && System.currentTimeMillis() < deadline) {
                ShadowLooper.idleMainLooper()
                delay(20L)
            }

            vm.finishEditorSession()
            ShadowLooper.idleMainLooper()
            delay(100L)

            val persisted = noteRepository.getNoteDetail(vm.uiState.value.noteId)
            assertNotNull(persisted)
            assertEquals(1, persisted!!.attachments.size)
            assertTrue(persisted.blocks.any { it.type == NoteBlockType.TEXT && it.textContent.contains("Photo notes before picker") })
            assertTrue(persisted.blocks.any { it.type == NoteBlockType.IMAGE })
        } finally {
            dsFile.delete()
            samplePng.delete()
        }
    }
}
