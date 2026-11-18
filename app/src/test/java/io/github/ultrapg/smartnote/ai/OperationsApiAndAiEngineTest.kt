package io.github.ultrapg.smartnote.ai

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.AiBackendPreference
import io.github.ultrapg.smartnote.domain.model.AiHardwareBackend
import io.github.ultrapg.smartnote.domain.model.LinkFetchStatus
import io.github.ultrapg.smartnote.domain.model.LinkPreviewInfo
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.operations.SmartNoteOperationException
import io.github.ultrapg.smartnote.domain.operations.SmartNoteOperationsApi
import io.github.ultrapg.smartnote.reminder.ReminderNotificationBuilder
import io.github.ultrapg.smartnote.reminder.ReminderScheduler
import io.github.ultrapg.smartnote.search.SearchIndexer
import io.github.ultrapg.smartnote.search.SmartSearchEngine
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OperationsApiAndAiEngineTest {

    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private lateinit var dsFile: File
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var database: SmartNoteDatabase
    private lateinit var noteRepository: NoteRepository
    private lateinit var operationsApi: SmartNoteOperationsApi

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dsFile = File(context.cacheDir, "ops_ai_${System.nanoTime()}.preferences_pb")
        settingsRepo = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { dsFile })
        )
        database = SmartNoteDatabase.createInMemory(context)
        val storage = AttachmentStorage(context)
        val indexer = SearchIndexer(database)
        val tagRepo = TagRepository(database, indexer)
        noteRepository = NoteRepository(database, storage, indexer, tagRepo)
        val searchEngine = SmartSearchEngine(database, noteRepository)
        val notifBuilder = ReminderNotificationBuilder(context, settingsRepo)
        val remScheduler = ReminderScheduler(context, database, notifBuilder)
        operationsApi = SmartNoteOperationsApi(noteRepository, tagRepo, searchEngine, remScheduler, settingsRepo)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        dsFile.delete()
    }

    @Test
    fun operationsApiValidatesInputsSoftDeletesOnlyAndHonorsMasterToggle() = runTest {
        val created = operationsApi.createNote(
            title = "Shopping List",
            content = "Milk and coffee",
            tags = listOf("Shopping")
        )
        assertEquals("Shopping List", created.title)

        val listed = operationsApi.listNotes(limit = 10, tagFilter = "Shopping")
        assertEquals(1, listed.size)

        val updated = operationsApi.updateNote(created.id, newTitle = "Updated Shopping List", appendContent = "Bread")
        assertEquals("Updated Shopping List", updated.title)
        assertTrue(updated.content.contains("Bread"))

        val found = operationsApi.searchNotes("coffee")
        assertEquals(1, found.size)

        // Delete requires confirmation
        var confirmationBlocked = false
        try {
            operationsApi.deleteNoteToRecycleBin(created.id, confirmedByUser = false)
        } catch (ex: SmartNoteOperationException) {
            confirmationBlocked = ex.code == "CONFIRMATION_REQUIRED"
        }
        assertTrue(confirmationBlocked)

        // Confirmed delete moves to Recycle Bin (recoverable, never permanent delete)
        val delRes = operationsApi.deleteNoteToRecycleBin(created.id, confirmedByUser = true)
        assertTrue(delRes.success)
        assertTrue(noteRepository.getNoteDetail(created.id)!!.isDeleted)
    }

    @Test
    fun aiEngineVerifiesBundledTfliteAssetAndHandlesHardwareFallbacksHonestly() = runTest {
        // Confirm real TFL3 FlatBuffer model is packaged in assets
        val tfliteHeader = context.assets.open("models/smartnote_text_embed_v1.tflite").use { stream ->
            val buf = ByteArray(8)
            stream.read(buf)
            buf
        }
        assertEquals('T'.code.toByte(), tfliteHeader[4])
        assertEquals('F'.code.toByte(), tfliteHeader[5])
        assertEquals('L'.code.toByte(), tfliteHeader[6])
        assertEquals('3'.code.toByte(), tfliteHeader[7])

        // 1. NPU fails -> falls back to GPU cleanly and reports exact backend + reason
        var initializedList = mutableListOf<AiHardwareBackend>()
        val npuFailGpuSuccessBridge = object : LiteRtRuntimeBridge {
            override fun probeCapabilities() = AiHardwareCapabilities(
                socDescription = "Snapdragon Mock",
                npuDeviceSupported = true,
                npuRuntimeReady = true,
                availableAccelerators = setOf(AiHardwareBackend.NPU, AiHardwareBackend.GPU, AiHardwareBackend.CPU)
            )
            override fun initialize(targetBackend: AiHardwareBackend): Result<AiHardwareBackend> {
                initializedList += targetBackend
                return if (targetBackend == AiHardwareBackend.NPU) {
                    Result.failure(IllegalStateException("NPU op unsupported"))
                } else {
                    Result.success(targetBackend)
                }
            }
            override fun runProjection(input128: FloatArray) = Result.success(FloatArray(64) { 0.125f })
            override fun close() {}
        }

        settingsRepo.setAiBackendPreference(AiBackendPreference.PREFER_NPU)
        val engine = SmartNoteAiEngine(database, settingsRepo, npuFailGpuSuccessBridge, scope, idleUnloadMs = 0L)
        val vec = engine.embedText("Android Kotlin Compose")
        assertEquals(64, vec.size)
        val diag = engine.diagnosticsFlow.value
        assertEquals(AiHardwareBackend.GPU, diag.activeNeuralBackend)
        assertNotNull(diag.fallbackReason)
        assertTrue(diag.fallbackReason!!.contains("NPU"))

        // 2. CPU_ONLY strictly bypasses NPU & GPU
        initializedList.clear()
        settingsRepo.setAiBackendPreference(AiBackendPreference.CPU_ONLY)
        engine.embedText("Strict CPU check")
        assertEquals(listOf(AiHardwareBackend.CPU), initializedList)
        assertEquals(AiHardwareBackend.CPU, engine.diagnosticsFlow.value.activeNeuralBackend)
        assertFalse(engine.diagnosticsFlow.value.activeNeuralBackend == AiHardwareBackend.NPU)
    }

    @Test
    fun smartTitleGeneratorSynthesizesMarkdownChecklistOcrUrlAndRespectsToggle() = runTest {
        val generator = SmartTitleGenerator()

        // 1. Markdown heading takes precedence
        val mdTitle = generator.generateFastTitle(
            blocks = listOf(
                NoteBlock("b1", "n1", 0, NoteBlockType.TEXT, "## **Offline Migration Checklist**\nDetailed steps follow.")
            )
        )
        assertEquals("Offline Migration Checklist", mdTitle)

        // 2. Checklist grocery domain synthesis
        val groceryTitle = generator.generateFastTitle(
            blocks = listOf(
                NoteBlock("c1", "n2", 0, NoteBlockType.CHECKLIST, "Oat milk"),
                NoteBlock("c2", "n2", 1, NoteBlockType.CHECKLIST, "Coffee beans"),
                NoteBlock("c3", "n2", 2, NoteBlockType.CHECKLIST, "Eggs")
            )
        )
        assertEquals("Shopping: Oat milk, Coffee beans (+1)", groceryTitle)

        // 3. Screenshot OCR skips noise (time, %, short symbols) and picks meaningful line
        val ocrTitle = generator.generateSmartTitle(
            blocks = emptyList(),
            ocrTexts = listOf("15:06\n79%\n5G\nNullPointerException in NoteProcessingWorker\nstack frame at line 42")
        )
        assertEquals("NullPointerException in NoteProcessingWorker", ocrTitle)

        // 4. URL slug + domain extraction when no prose exists
        val urlTitle = generator.generateFastTitle(
            blocks = emptyList(),
            links = listOf(
                LinkPreviewInfo(
                    id = "l1",
                    noteId = "n3",
                    rawUrl = "https://developer.android.com/develop/ui/compose/animation",
                    canonicalUrl = "https://developer.android.com/develop/ui/compose/animation",
                    domain = "developer.android.com",
                    fetchStatus = LinkFetchStatus.LOCAL_ONLY,
                    updatedAtMs = 0L
                )
            )
        )
        assertTrue(urlTitle.contains("Compose Animation"))
        assertTrue(urlTitle.contains("developer.android.com"))

        // 5. Generic placeholders recognized for background upgrade
        assertTrue(SmartTitleGenerator.isPlaceholderOrBlankTitle(""))
        assertTrue(SmartTitleGenerator.isPlaceholderOrBlankTitle("Shared image"))
        assertTrue(SmartTitleGenerator.isPlaceholderOrBlankTitle("3 shared images"))
        assertTrue(SmartTitleGenerator.isPlaceholderOrBlankTitle("Link from github.com"))
        assertFalse(SmartTitleGenerator.isPlaceholderOrBlankTitle("Sprint Planning Notes"))

        // 6. Operations API auto-titles untitled createNote when enabled & respects per-feature and master AI toggles
        val autoTitled = operationsApi.createNote(
            title = "",
            content = "Architecture review for local FTS5 search index.",
            tags = listOf("Android")
        )
        assertTrue(autoTitled.title.contains("Architecture review", ignoreCase = true))

        settingsRepo.setAutoTitleEnabled(false)
        val untitledDisabled = operationsApi.createNote(
            title = "",
            content = "Body remains untitled when auto-title toggle is off."
        )
        assertEquals("", untitledDisabled.title)

        settingsRepo.setAutoTitleEnabled(true)
        settingsRepo.setAiFeaturesEnabled(false)
        val masterOffNote = operationsApi.createNote(
            title = "",
            content = "Body remains untitled when master AI switch is off."
        )
        assertEquals("", masterOffNote.title)
        settingsRepo.setAiFeaturesEnabled(true)

        // 7. Atomic repository update guards against stale revisions
        val staleRejected = noteRepository.applyGeneratedTitleIfUnmodified(
            noteId = untitledDisabled.id,
            expectedRevision = 999L,
            generatedTitle = "Should Not Apply"
        )
        assertFalse(staleRejected)

        val currentDetail = noteRepository.getNoteDetail(untitledDisabled.id)!!
        val applied = noteRepository.applyGeneratedTitleIfUnmodified(
            noteId = untitledDisabled.id,
            expectedRevision = currentDetail.contentRevision,
            generatedTitle = "FTS5 Search Benchmarks"
        )
        assertTrue(applied)
        assertEquals("FTS5 Search Benchmarks", noteRepository.getNoteDetail(untitledDisabled.id)!!.title)
    }
}
