package io.github.ultrapg.smartnote.capture

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.backup.BackupManager
import io.github.ultrapg.smartnote.backup.RestoreMode
import io.github.ultrapg.smartnote.backup.SafeZipExtractor
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.reminder.ReminderNotificationBuilder
import io.github.ultrapg.smartnote.reminder.ReminderScheduler
import io.github.ultrapg.smartnote.search.SearchIndexer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class SmartCaptureAndBackupSecurityTest {

    private lateinit var context: Context
    private lateinit var database: SmartNoteDatabase
    private lateinit var storage: AttachmentStorage
    private lateinit var importer: AttachmentImporter
    private lateinit var noteRepository: NoteRepository
    private lateinit var backupManager: BackupManager
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        database = SmartNoteDatabase.createInMemory(context)
        storage = AttachmentStorage(context)
        importer = AttachmentImporter(context, storage)
        val indexer = SearchIndexer(database)
        val tagRepo = TagRepository(database, indexer)
        noteRepository = NoteRepository(database, storage, indexer, tagRepo)
        val settingsRepo = SettingsRepository(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(context.cacheDir, "cap_bk_${System.nanoTime()}.preferences_pb") }
            )
        )
        val notifBuilder = ReminderNotificationBuilder(context, settingsRepo)
        val remScheduler = ReminderScheduler(context, database, notifBuilder)
        backupManager = BackupManager(context, database, storage, indexer, remScheduler)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        storage.attachmentsDir.deleteRecursively()
        storage.thumbnailsDir.deleteRecursively()
    }

    @Test
    fun parsesSingleAndMultipleSharesheetIntentsAndRejectsPrivateAppFileUris() = runTest {
        val samplePng = java.nio.file.Files.createTempFile("Screenshot_2026_test_", ".png").toFile()
        try {
            val bmp = Bitmap.createBitmap(108, 220, Bitmap.Config.ARGB_8888)
            FileOutputStream(samplePng).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_SUBJECT, "Stacktrace screenshot")
                putExtra(Intent.EXTRA_TEXT, "Check https://developer.android.com/compose")
                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(samplePng))
            }
            val payload = SmartCaptureActivity.extractSharePayload(sendIntent)
            assertEquals("Stacktrace screenshot", payload.subject)
            assertEquals(1, payload.imageUris.size)
            assertEquals("developer.android.com", payload.detectedLinks.first().domain)

            // Staging copies file bytes into private staging dir + generates thumbnail
            val staged = importer.stageIncomingUris("sess1", payload.imageUris)
            assertEquals(1, staged.size)
            assertTrue(staged.first().isScreenshot)
            assertNotNull(staged.first().thumbnailRelativePath)

            val entities = importer.commitStagedAttachments("note-cap-1", staged)
            assertEquals(1, entities.size)
            val persistedFile = storage.resolvePrivateFile(entities.first().relativeFilePath)
            assertTrue(persistedFile.exists())

            // Even if external original file is deleted (simulating expired URI permission), private copy remains
            samplePng.delete()
            assertTrue(persistedFile.exists())

            // Reject file:// URIs targeting SmartNote's private filesDir or cacheDir
            assertFalse(importer.isSafeUri(Uri.fromFile(persistedFile)))
            assertFalse(importer.isSafeUri(Uri.fromFile(File(context.cacheDir, "internal.png"))))
        } finally {
            samplePng.delete()
        }

        // ACTION_SEND_MULTIPLE extracts multiple ClipData / stream URIs
        val multi = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(Uri.parse("content://media/1"), Uri.parse("content://media/2"))
            )
            clipData = ClipData.newRawUri("img3", Uri.parse("content://media/3"))
        }
        val multiPayload = SmartCaptureActivity.extractSharePayload(multi)
        assertEquals(3, multiPayload.imageUris.size)
    }

    @Test
    fun backupRoundTripMergeAndReplaceWorksAndBlocksZipSlipTraversal() = runTest {
        // 1. Verify Zip-Slip attack rejection
        val evilZipBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("../../etc/evil.txt"))
                zos.write("pwn".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()

        val staging = File(context.cacheDir, "zipslip_test_${System.nanoTime()}")
        var blocked = false
        try {
            ZipInputStream(ByteArrayInputStream(evilZipBytes)).use { zis ->
                SafeZipExtractor.extractValidated(zis, staging)
            }
        } catch (_: SecurityException) {
            blocked = true
        } finally {
            staging.deleteRecursively()
        }
        assertTrue("SafeZipExtractor must reject Zip-Slip traversal", blocked)

        // 2. Full backup export + Replace & Merge round trip
        val noteId = noteRepository.createNote(
            title = "Backup Target",
            blocks = listOf(NoteBlock("b1", "n", 0, NoteBlockType.TEXT, "Important offline memo.")),
            initialTags = listOf("ArchiveTag"),
            isPinned = true,
            scheduleBackgroundWork = false
        )
        val backupFile = File(context.cacheDir, "test_backup.snbackup")
        val exportRes = backupManager.exportToUri(Uri.fromFile(backupFile))
        assertEquals(1, exportRes.noteCount)
        assertTrue(backupFile.length() > 100)

        // Delete note and restore via REPLACE_ALL
        noteRepository.deletePermanently(noteId)
        assertEquals(0, noteRepository.getAllSummariesSnapshot(includeArchived = true).size)

        val restoreRes = backupManager.restoreFromUri(Uri.fromFile(backupFile), RestoreMode.REPLACE_ALL)
        assertEquals(1, restoreRes.notesImported)
        val reloaded = noteRepository.getNoteDetail(noteId)
        assertNotNull(reloaded)
        assertEquals("Backup Target", reloaded!!.title)
        assertTrue(reloaded.isPinned)
        assertTrue(reloaded.tags.any { it.name == "ArchiveTag" })

        // Verify tampered backup throws checksum exception before touching DB
        val tamperedFile = File(context.cacheDir, "tampered.snbackup")
        val rawBytes = backupFile.readBytes()
        // Corrupt a byte inside ZIP archive or rebuild tampered data.json
        ZipInputStream(backupFile.inputStream()).use { zis ->
            ZipOutputStream(FileOutputStream(tamperedFile)).use { zos ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    zos.putNextEntry(ZipEntry(e.name))
                    val data = zis.readBytes()
                    if (e.name == "data.json") {
                        zos.write(String(data).replace("Important", "Tampered!").toByteArray())
                    } else {
                        zos.write(data)
                    }
                    zos.closeEntry()
                }
            }
        }
        var tamperRejected = false
        try {
            backupManager.restoreFromUri(Uri.fromFile(tamperedFile), RestoreMode.MERGE)
        } catch (_: IllegalArgumentException) {
            tamperRejected = true
        }
        assertTrue("Tampered archive SHA-256 must be rejected", tamperRejected)

        backupFile.delete()
        tamperedFile.delete()
    }
}
