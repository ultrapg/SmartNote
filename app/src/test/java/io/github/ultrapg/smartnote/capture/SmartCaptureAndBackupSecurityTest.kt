package io.github.ultrapg.smartnote.capture

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.ai.AiHardwareCapabilities
import io.github.ultrapg.smartnote.ai.ClassifiedTextEntity
import io.github.ultrapg.smartnote.ai.DetectedImageLabel
import io.github.ultrapg.smartnote.ai.GeminiNanoAvailability
import io.github.ultrapg.smartnote.ai.GeminiNanoCapabilities
import io.github.ultrapg.smartnote.ai.ImageLabelingAvailability
import io.github.ultrapg.smartnote.ai.ImageLabelingEngine
import io.github.ultrapg.smartnote.ai.LiteRtRuntimeBridge
import io.github.ultrapg.smartnote.ai.OnDeviceGenAiAssistant
import io.github.ultrapg.smartnote.ai.SmartNoteAiEngine
import io.github.ultrapg.smartnote.ai.SmartTitleGenerator
import io.github.ultrapg.smartnote.ai.SystemEntityAvailability
import io.github.ultrapg.smartnote.ai.SystemEntityAnalyzer
import io.github.ultrapg.smartnote.backup.BackupManager
import io.github.ultrapg.smartnote.backup.RestoreMode
import io.github.ultrapg.smartnote.backup.SafeZipExtractor
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.AiHardwareBackend
import io.github.ultrapg.smartnote.domain.model.LinkFetchStatus
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NoteSourceType
import io.github.ultrapg.smartnote.domain.model.OcrStatus
import io.github.ultrapg.smartnote.links.LinkMetadataFetcher
import io.github.ultrapg.smartnote.ocr.OcrEngine
import io.github.ultrapg.smartnote.ocr.OcrOutcome
import io.github.ultrapg.smartnote.ocr.SmartOcrCoordinator
import io.github.ultrapg.smartnote.search.SearchIndexer
import io.github.ultrapg.smartnote.search.SmartSearchEngine
import io.github.ultrapg.smartnote.tags.SmartTagSuggestion
import io.github.ultrapg.smartnote.tags.SmartTaggingCoordinator
import io.github.ultrapg.smartnote.tags.SmartTagsEngine
import io.github.ultrapg.smartnote.worker.NoteProcessingOutcome
import io.github.ultrapg.smartnote.worker.NoteProcessingPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
    private lateinit var indexer: SearchIndexer
    private lateinit var tagRepo: TagRepository
    private lateinit var noteRepository: NoteRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var dsFile: File
    private lateinit var backupManager: BackupManager
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        database = SmartNoteDatabase.createInMemory(context)
        storage = AttachmentStorage(context)
        importer = AttachmentImporter(context, storage)
        indexer = SearchIndexer(database)
        tagRepo = TagRepository(database, indexer)
        noteRepository = NoteRepository(database, storage, indexer, tagRepo)
        dsFile = File(context.cacheDir, "cap_bk_${System.nanoTime()}.preferences_pb")
        settingsRepo = SettingsRepository(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { dsFile }
            )
        )
        backupManager = BackupManager(context, database, storage, indexer)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        dsFile.delete()
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

    @Test
    fun sanitizeInitialSubjectFiltersUrlsDuplicatesAndCameraFilenames() {
        assertEquals("", SmartCaptureActivity.sanitizeInitialSubject("   ", "hello"))
        assertEquals("", SmartCaptureActivity.sanitizeInitialSubject("https://example.com/a", ""))
        assertEquals("", SmartCaptureActivity.sanitizeInitialSubject("www.example.com", ""))
        assertEquals("", SmartCaptureActivity.sanitizeInitialSubject("Same body text", "same body text"))
        assertEquals("", SmartCaptureActivity.sanitizeInitialSubject("Screenshot_20261119_115500.png", ""))
        assertEquals("", SmartCaptureActivity.sanitizeInitialSubject("IMG_20261119_120000.jpg", ""))
        assertEquals("", SmartCaptureActivity.sanitizeInitialSubject("PXL_20261119_120000123", ""))
        assertEquals(
            "Quarterly Planning Deck",
            SmartCaptureActivity.sanitizeInitialSubject("Quarterly Planning Deck", "Check attached PDF notes")
        )
    }

    @Test
    fun shareCapturePipelineRunsOcrLinkPreviewAutoTitleSmartTagsAndDeduplicatesByRevision() = runTest {
        settingsRepo.setAutoTitleEnabled(true)
        settingsRepo.setFetchLinkPreviews(true)
        settingsRepo.setAllowNewTopicTags(true)

        var ocrCalls = 0
        val fakeOcr = object : OcrEngine {
            override suspend fun recognizeFile(file: File): OcrOutcome {
                ocrCalls++
                return OcrOutcome(
                    text = "Coffee Beans  $14.50\nSupermarket Receipt\nSubtotal EUR 12.18\nTotal EUR 14.50",
                    confidence = 0.91f,
                    succeeded = true
                )
            }
        }
        val fakeLabeler = object : ImageLabelingEngine {
            override suspend fun checkAvailability(forceRefresh: Boolean) = ImageLabelingAvailability.AVAILABLE
            override suspend fun labelImageFile(file: File) = listOf(
                DetectedImageLabel("Receipt", 0.92f)
            )
        }
        val readyEntities = object : SystemEntityAnalyzer {
            override fun checkAvailability() = SystemEntityAvailability.AVAILABLE
            override suspend fun extractEntities(text: String) = emptyList<ClassifiedTextEntity>()
        }
        val cpuBridge = object : LiteRtRuntimeBridge {
            override fun probeCapabilities() = AiHardwareCapabilities(
                socDescription = "Test SoC",
                npuDeviceSupported = false,
                npuRuntimeReady = false,
                availableAccelerators = setOf(AiHardwareBackend.CPU)
            )
            override fun initialize(targetBackend: AiHardwareBackend) = Result.success(targetBackend)
            override fun runProjection(input128: FloatArray) = Result.success(FloatArray(64) { 0f })
            override fun close() {}
        }
        val unavailableNano = object : OnDeviceGenAiAssistant {
            override suspend fun checkAvailability() = GeminiNanoAvailability.UNAVAILABLE
            override suspend fun probeCapabilities(forceRefresh: Boolean) = GeminiNanoCapabilities()
            override fun triggerBackgroundDownloadIfNeeded() {}
            override suspend fun generateTitleCandidates(
                noteText: String,
                ocrTexts: List<String>,
                imageLabels: List<String>,
                linkHints: List<String>,
                primaryImageBitmap: Bitmap?
            ) = emptyList<String>()
            override suspend fun generateTags(
                title: String,
                noteText: String,
                ocrTexts: List<String>,
                imageLabels: List<String>,
                existingVocabulary: List<String>,
                existingNoteTags: Set<String>
            ) = emptyList<SmartTagSuggestion>()
            override fun close() {}
        }
        val fakeHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val html = """
                    <html><head>
                      <meta property="og:title" content="Crispy Garlic Pasta Recipe — Chefkoch" />
                      <meta name="description" content="Easy pasta dinner recipe with garlic, olive oil, and parmesan." />
                    </head></html>
                """.trimIndent()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(html.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()

        val aiEngine = SmartNoteAiEngine(
            database = database,
            settingsRepository = settingsRepo,
            runtimeBridge = cpuBridge,
            applicationScope = scope,
            idleUnloadMs = 0L,
            geminiNanoAssistant = unavailableNano,
            imageLabelingEngine = fakeLabeler,
            systemEntityAnalyzer = readyEntities
        )
        val coordinator = SmartTaggingCoordinator(
            smartTagsEngine = SmartTagsEngine(),
            aiEngine = aiEngine,
            systemEntityAnalyzer = readyEntities,
            imageLabelingEngine = fakeLabeler,
            geminiNanoAssistant = unavailableNano,
            attachmentStorage = storage
        )
        val pipeline = NoteProcessingPipeline(
            database = database,
            noteRepository = noteRepository,
            tagRepository = tagRepo,
            settingsRepository = settingsRepo,
            attachmentStorage = storage,
            searchIndexer = indexer,
            smartOcrCoordinator = SmartOcrCoordinator(fakeOcr, unavailableNano),
            imageLabelingEngine = fakeLabeler,
            systemEntityAnalyzer = readyEntities,
            linkMetadataFetcher = LinkMetadataFetcher(settingsRepo, fakeHttpClient, storage) { true },
            smartTitleGenerator = SmartTitleGenerator(aiEngine, unavailableNano),
            smartTaggingCoordinator = coordinator,
            aiEngine = aiEngine,
            applicationScope = scope
        )

        // 1. Shared image note runs OCR -> Auto-Title -> Image+OCR SmartTags -> FTS5, and deduplicates repeat runs
        val sampleImg = java.nio.file.Files.createTempFile("IMG_20261119_", ".jpg").toFile()
        try {
            val bmp = Bitmap.createBitmap(180, 240, Bitmap.Config.ARGB_8888)
            FileOutputStream(sampleImg).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bmp.recycle()

            val staged = importer.stageIncomingUris("sess_pipe", listOf(Uri.fromFile(sampleImg)))
            val imgNoteId = java.util.UUID.randomUUID().toString()
            val committed = importer.commitStagedAttachments(imgNoteId, staged)
            noteRepository.createNote(
                id = imgNoteId,
                title = "",
                blocks = listOf(
                    NoteBlock("bi1", imgNoteId, 0, NoteBlockType.IMAGE, attachmentId = committed.first().id)
                ),
                sourceType = NoteSourceType.SHARE_IMAGE,
                attachments = committed,
                scheduleBackgroundWork = false
            )

            assertEquals(NoteProcessingOutcome.COMPLETED, pipeline.processNote(imgNoteId))
            assertEquals(1, ocrCalls)

            val processedImg = noteRepository.getNoteDetail(imgNoteId)!!
            assertTrue(processedImg.title.isNotBlank())
            assertEquals(OcrStatus.COMPLETED, processedImg.attachments.first().ocrStatus)
            val imgTagNames = processedImg.tags.map { it.name }
            assertTrue("Expected Receipt tag on shared receipt image, got $imgTagNames", "Receipt" in imgTagNames)

            val searchEngine = SmartSearchEngine(database, noteRepository)
            assertTrue(searchEngine.search("Supermarket").results.any { it.note.id == imgNoteId })

            // Repeat invocation on same revision must skip without re-running OCR
            assertEquals(NoteProcessingOutcome.SKIPPED_ALREADY_CURRENT, pipeline.processNote(imgNoteId))
            assertEquals(1, ocrCalls)
        } finally {
            sampleImg.delete()
        }

        // 2. Shared URL note feeds fetched webpage title & description into Auto-Title and SmartTags
        val urlNoteId = noteRepository.createNote(
            title = "",
            blocks = listOf(
                NoteBlock("bu1", "u", 0, NoteBlockType.LINK, textContent = "chefkoch.de", linkUrl = "https://chefkoch.de/rezepte/123")
            ),
            sourceType = NoteSourceType.SHARE_URL,
            scheduleBackgroundWork = false
        )
        assertEquals(NoteProcessingOutcome.COMPLETED, pipeline.processNote(urlNoteId))
        val processedUrl = noteRepository.getNoteDetail(urlNoteId)!!
        assertEquals(LinkFetchStatus.FETCHED, processedUrl.links.first().fetchStatus)
        assertEquals("Crispy Garlic Pasta Recipe", processedUrl.title)
        assertTrue(
            "Expected Recipe tag from fetched webpage metadata + domain, got ${processedUrl.tags.map { it.name }}",
            processedUrl.tags.any { it.name == "Recipe" }
        )

        // 3. Shared non-taxonomy text note with allowNewTopicTags = true gets open-vocabulary topic tags cleanly
        val topicNoteId = noteRepository.createNote(
            title = "Sourdough fermentation schedule",
            blocks = listOf(
                NoteBlock("bt1", "t", 0, NoteBlockType.TEXT, "Feed sourdough starter at 28C and stretch sourdough dough overnight")
            ),
            sourceType = NoteSourceType.SHARE_TEXT,
            scheduleBackgroundWork = false
        )
        assertEquals(NoteProcessingOutcome.COMPLETED, pipeline.processNote(topicNoteId))
        val processedTopic = noteRepository.getNoteDetail(topicNoteId)!!
        assertTrue(
            "Expected Sourdough open-topic tag on shared note, got ${processedTopic.tags.map { it.name }}",
            processedTopic.tags.any { it.name == "Sourdough" }
        )
    }
}
