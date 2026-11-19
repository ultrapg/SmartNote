package io.github.ultrapg.smartnote.ai

import android.content.Context
import android.view.textclassifier.TextClassifier
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.github.ultrapg.smartnote.ai.ClassifiedTextEntity
import io.github.ultrapg.smartnote.ai.GeminiNanoAvailability
import io.github.ultrapg.smartnote.ai.OnDeviceGenAiAssistant
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.AiBackendPreference
import io.github.ultrapg.smartnote.domain.model.AiHardwareBackend
import io.github.ultrapg.smartnote.domain.model.GeminiNanoFeaturePolicy
import io.github.ultrapg.smartnote.domain.model.GeminiNanoMode
import io.github.ultrapg.smartnote.domain.model.GeminiNanoVariant
import io.github.ultrapg.smartnote.domain.model.LinkFetchStatus
import io.github.ultrapg.smartnote.domain.model.LinkPreviewInfo
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.TagPredictionSensitivity
import io.github.ultrapg.smartnote.domain.model.TitleStylePreference
import io.github.ultrapg.smartnote.domain.operations.SmartNoteOperationException
import io.github.ultrapg.smartnote.domain.operations.SmartNoteOperationsApi
import io.github.ultrapg.smartnote.search.SearchIndexer
import io.github.ultrapg.smartnote.search.SmartSearchEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
        operationsApi = SmartNoteOperationsApi(noteRepository, tagRepo, searchEngine, settingsRepo)
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

        // 6. Operations API keeps title blank by default and auto-titles untitled notes when opt-in is enabled & respects master AI toggle
        val untitledDefault = operationsApi.createNote(
            title = "",
            content = "Body remains untitled when auto-title toggle is off by default."
        )
        assertEquals("", untitledDefault.title)

        settingsRepo.setAutoTitleEnabled(true)
        val autoTitled = operationsApi.createNote(
            title = "",
            content = "Architecture review for local FTS5 search index.",
            tags = listOf("Android")
        )
        assertTrue(autoTitled.title.contains("Architecture review", ignoreCase = true))

        settingsRepo.setAiFeaturesEnabled(false)
        val masterOffNote = operationsApi.createNote(
            title = "",
            content = "Body remains untitled when master AI switch is off."
        )
        assertEquals("", masterOffNote.title)
        settingsRepo.setAiFeaturesEnabled(true)
        settingsRepo.setAutoTitleEnabled(false)

        // 7. Atomic repository update guards against stale revisions
        val staleRejected = noteRepository.applyGeneratedTitleIfUnmodified(
            noteId = untitledDefault.id,
            expectedRevision = 999L,
            generatedTitle = "Should Not Apply"
        )
        assertFalse(staleRejected)

        val currentDetail = noteRepository.getNoteDetail(untitledDefault.id)!!
        val applied = noteRepository.applyGeneratedTitleIfUnmodified(
            noteId = untitledDefault.id,
            expectedRevision = currentDetail.contentRevision,
            generatedTitle = "FTS5 Search Benchmarks"
        )
        assertTrue(applied)
        assertEquals("FTS5 Search Benchmarks", noteRepository.getNoteDetail(untitledDefault.id)!!.title)
    }

    @Test
    fun energyEfficientModePrioritizesLowPowerNpuAndSkipsGpuOnBatterySaver() = runTest {
        val attempts = mutableListOf<AiHardwareBackend>()
        val powerSaveWithNpuBridge = object : LiteRtRuntimeBridge {
            override fun probeCapabilities() = AiHardwareCapabilities(
                socDescription = "QTI SM8750",
                npuDeviceSupported = true,
                npuRuntimeReady = true,
                availableAccelerators = setOf(AiHardwareBackend.NPU, AiHardwareBackend.GPU, AiHardwareBackend.CPU),
                isPowerSaveMode = true,
                thermalStatus = 0
            )
            override fun initialize(targetBackend: AiHardwareBackend): Result<AiHardwareBackend> {
                attempts += targetBackend
                return Result.success(targetBackend)
            }
            override fun runProjection(input128: FloatArray) = Result.success(FloatArray(64) { 0.25f })
            override fun close() {}
        }

        val npuEngine = SmartNoteAiEngine(database, settingsRepo, powerSaveWithNpuBridge, scope, idleUnloadMs = 0L)
        settingsRepo.setEnergyEfficientAi(true)

        // 1. Even with PREFER_GPU, Battery Saver + Energy-Efficient Processing prioritizes NPU and skips GPU
        settingsRepo.setAiBackendPreference(AiBackendPreference.PREFER_GPU)
        npuEngine.embedText("NPU energy saving check")
        assertEquals(AiHardwareBackend.NPU, npuEngine.diagnosticsFlow.value.activeNeuralBackend)
        assertTrue(npuEngine.diagnosticsFlow.value.energySavingActive)
        assertTrue(AiHardwareBackend.GPU !in attempts)

        // 2. AUTO also keeps low-power NPU active under Battery Saver
        attempts.clear()
        settingsRepo.setAiBackendPreference(AiBackendPreference.AUTO)
        npuEngine.embedText("AUTO energy saving check")
        assertEquals(AiHardwareBackend.NPU, npuEngine.diagnosticsFlow.value.activeNeuralBackend)
        assertTrue(AiHardwareBackend.GPU !in attempts)

        // 3. Explicit CPU_ONLY still honors strict CPU preference
        attempts.clear()
        settingsRepo.setAiBackendPreference(AiBackendPreference.CPU_ONLY)
        npuEngine.embedText("CPU only override")
        assertEquals(listOf(AiHardwareBackend.CPU), attempts)

        // 4. Without NPU support under Battery Saver, high-wattage GPU is skipped and CPU is used
        val noNpuAttempts = mutableListOf<AiHardwareBackend>()
        val noNpuPowerSaveBridge = object : LiteRtRuntimeBridge {
            override fun probeCapabilities() = AiHardwareCapabilities(
                socDescription = "GPU+CPU SoC",
                npuDeviceSupported = false,
                npuRuntimeReady = false,
                availableAccelerators = setOf(AiHardwareBackend.GPU, AiHardwareBackend.CPU),
                isPowerSaveMode = true,
                thermalStatus = 0
            )
            override fun initialize(targetBackend: AiHardwareBackend): Result<AiHardwareBackend> {
                noNpuAttempts += targetBackend
                return Result.success(targetBackend)
            }
            override fun runProjection(input128: FloatArray) = Result.success(FloatArray(64) { 0.25f })
            override fun close() {}
        }

        val gpuFallbackEngine = SmartNoteAiEngine(database, settingsRepo, noNpuPowerSaveBridge, scope, idleUnloadMs = 0L)
        settingsRepo.setAiBackendPreference(AiBackendPreference.PREFER_GPU)
        gpuFallbackEngine.embedText("No NPU fallback check")
        assertEquals(listOf(AiHardwareBackend.CPU), noNpuAttempts)
        assertEquals(AiHardwareBackend.CPU, gpuFallbackEngine.diagnosticsFlow.value.activeNeuralBackend)
    }

    @Test
    fun upgradedTitleGeneratorHandlesFillersEntitiesImageLabelsAndGeminiNano() = runTest {
        val generator = SmartTitleGenerator()

        // 1. English filler stripping & hashtag cleanup
        val enFiltered = generator.generateSmartTitle(
            blocks = listOf(NoteBlock("1", "n", 0, NoteBlockType.TEXT, "Remember to book airport shuttle before Friday #Travel"))
        )
        assertEquals("Book airport shuttle before Friday", enFiltered)

        // 2. German filler prefix stripping
        val deFiltered = generator.generateSmartTitle(
            blocks = listOf(NoteBlock("2", "n", 0, NoteBlockType.TEXT, "Ich muss noch Steuerunterlagen für 2026 einreichen"))
        )
        assertEquals("Steuerunterlagen für 2026 einreichen", deFiltered)

        // 3. Long clause compression & dangling trailing preposition removal
        val trimmedLong = generator.generateSmartTitle(
            blocks = listOf(
                NoteBlock(
                    "3",
                    "n",
                    0,
                    NoteBlockType.TEXT,
                    "Prepare architecture slides for the summit in Berlin and coordinate speakers"
                )
            )
        )
        assertTrue("Long clause should stay concise without dangling prepositions: $trimmedLong", trimmedLong.length <= 56)
        assertFalse(trimmedLong.endsWith(" in", ignoreCase = true))
        assertFalse(trimmedLong.endsWith(" for", ignoreCase = true))

        // 4. German grocery checklist synthesis
        val deGrocery = generator.generateFastTitle(
            blocks = listOf(
                NoteBlock("c1", "n", 0, NoteBlockType.CHECKLIST, "Milch"),
                NoteBlock("c2", "n", 1, NoteBlockType.CHECKLIST, "Brot"),
                NoteBlock("c3", "n", 2, NoteBlockType.CHECKLIST, "Eier")
            )
        )
        assertEquals("Shopping: Milch, Brot (+1)", deGrocery)

        // 5. Skips bare ISO date / greeting meta line in favor of topical line
        val skippedDate = generator.generateSmartTitle(
            blocks = listOf(NoteBlock("4", "n", 0, NoteBlockType.TEXT, "2026-11-19\nQuarterly Budget Review\nReview Q4 numbers"))
        )
        assertEquals("Quarterly Budget Review", skippedDate)

        // 6. Produces multiple distinct alternatives for cycling
        val multi = generator.generateTitleCandidates(
            blocks = listOf(NoteBlock("5", "n", 0, NoteBlockType.TEXT, "Dentist appointment tomorrow\nPick up night guard"))
        )
        assertTrue("Expected at least 2 distinct candidates, got $multi", multi.size >= 2)

        // 7. System TextClassifier flight number & ML Kit Image Labeling fallbacks
        val flightTitle = generator.generateSmartTitle(
            blocks = listOf(NoteBlock("6", "n", 0, NoteBlockType.TEXT, "LH402")),
            systemEntities = listOf(ClassifiedTextEntity(TextClassifier.TYPE_FLIGHT_NUMBER, "LH402", 0.95f))
        )
        assertEquals("Flight LH402", flightTitle)

        val imageTitle = generator.generateSmartTitle(
            blocks = emptyList(),
            imageLabels = listOf("Golden Retriever", "Park")
        )
        assertEquals("Golden Retriever & Park", imageTitle)

        // 8. Gemini Nano on-device synthesis when AVAILABLE (including visual image forwarding even with OCR present)
        var capturedBitmap: android.graphics.Bitmap? = null
        val fakeNano = object : OnDeviceGenAiAssistant {
            override suspend fun checkAvailability() = GeminiNanoAvailability.AVAILABLE
            override fun triggerBackgroundDownloadIfNeeded() {}
            override suspend fun generateTitleCandidates(
                noteText: String,
                ocrTexts: List<String>,
                imageLabels: List<String>,
                linkHints: List<String>,
                primaryImageBitmap: android.graphics.Bitmap?
            ): List<String> {
                capturedBitmap = primaryImageBitmap
                return listOf(if (primaryImageBitmap != null) "Espresso Shot Receipt" else "Q4 Hiring Plan Summary")
            }
            override suspend fun generateTags(
                title: String,
                noteText: String,
                ocrTexts: List<String>,
                imageLabels: List<String>,
                existingVocabulary: List<String>,
                existingNoteTags: Set<String>
            ) = emptyList<io.github.ultrapg.smartnote.tags.SmartTagSuggestion>()
            override fun close() {}
        }
        val nanoGenerator = SmartTitleGenerator(genAiAssistant = fakeNano)
        val nanoTitle = nanoGenerator.generateSmartTitle(
            blocks = listOf(NoteBlock("7", "n", 0, NoteBlockType.TEXT, "We should align headcount with product roadmap targets next week."))
        )
        assertEquals("Q4 Hiring Plan Summary", nanoTitle)

        // Force prioritizes Gemini Nano ahead of explicit # Heading while keeping # Heading as alternative
        val forcedCandidates = nanoGenerator.generateTitleCandidates(
            blocks = listOf(NoteBlock("7b", "n", 0, NoteBlockType.TEXT, "# Draft Header\nWe should align headcount with product roadmap targets next week.")),
            forceGeminiNano = true
        )
        assertEquals("Q4 Hiring Plan Summary", forcedCandidates.first())
        assertTrue("Draft Header" in forcedCandidates)

        val testBmp = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val visualWithOcrTitle = nanoGenerator.generateSmartTitle(
                blocks = emptyList(),
                ocrTexts = listOf("Latte 4.50\nThank you"),
                primaryImageBitmap = testBmp
            )
            assertTrue(capturedBitmap === testBmp)
            assertEquals("Espresso Shot Receipt", visualWithOcrTitle)
        } finally {
            testBmp.recycle()
        }

        // 9. Respects allowGeminiNano = false and Concise maxTitleLength
        val noNanoTitle = nanoGenerator.generateSmartTitle(
            blocks = listOf(NoteBlock("8", "n", 0, NoteBlockType.TEXT, "Quarterly budget planning summary for engineering hiring next month")),
            allowGeminiNano = false,
            maxTitleLength = 44
        )
        assertFalse(noNanoTitle == "Q4 Hiring Plan Summary")
        assertTrue("Expected title <= 44 chars, got '$noNanoTitle' (${noNanoTitle.length})", noNanoTitle.length <= 44)
    }

    @Test
    fun signedVectorizerProbedDiagnosticsPreservationAndExpandedAiSettingsPersistCleanly() = runTest {
        // 1. Signed trigram vectorization avoids high similarity between unrelated everyday notes
        val casualVec = TextFeatureVectorizer.vectorizeInput(
            "Ich wollte mal schauen ob wir heute Abend noch rausgehen oder lieber zu Hause bleiben."
        )
        val financeProtoVec = TextFeatureVectorizer.vectorizeInput(
            "finance budget invoice tax bank salary expense payment transfer steuer konto ueberweisung"
        )
        val unrelatedSim = TextFeatureVectorizer.cosineSimilarity(casualVec, financeProtoVec)
        assertTrue("Unrelated notes cosine ($unrelatedSim) should remain low (< 0.35)", unrelatedSim < 0.35f)

        // 2. SmartNoteAiEngine.embedText preserves probed Gemini Nano, Image Labeling, and Entity capabilities
        val cpuBridge = object : LiteRtRuntimeBridge {
            override fun probeCapabilities() = AiHardwareCapabilities(
                socDescription = "Test SoC",
                npuDeviceSupported = false,
                npuRuntimeReady = false,
                availableAccelerators = setOf(AiHardwareBackend.CPU)
            )
            override fun initialize(targetBackend: AiHardwareBackend) = Result.success(targetBackend)
            override fun runProjection(input128: FloatArray) = Result.success(FloatArray(64) { 0.125f })
            override fun close() {}
        }
        val fakeNano = object : OnDeviceGenAiAssistant {
            override suspend fun checkAvailability() = GeminiNanoAvailability.AVAILABLE
            override suspend fun probeCapabilities(forceRefresh: Boolean) = GeminiNanoCapabilities(
                fastAvailability = GeminiNanoAvailability.AVAILABLE,
                fullAvailability = GeminiNanoAvailability.DOWNLOADABLE,
                baseModelName = "gemini-nano-v2"
            )
            override fun triggerBackgroundDownloadIfNeeded() {}
            override suspend fun generateTitleCandidates(
                noteText: String,
                ocrTexts: List<String>,
                imageLabels: List<String>,
                linkHints: List<String>,
                primaryImageBitmap: android.graphics.Bitmap?
            ) = emptyList<String>()
            override suspend fun generateTags(
                title: String,
                noteText: String,
                ocrTexts: List<String>,
                imageLabels: List<String>,
                existingVocabulary: List<String>,
                existingNoteTags: Set<String>
            ) = emptyList<io.github.ultrapg.smartnote.tags.SmartTagSuggestion>()
            override fun close() {}
        }
        val readyImageLabeler = object : ImageLabelingEngine {
            override suspend fun checkAvailability(forceRefresh: Boolean) = ImageLabelingAvailability.AVAILABLE
            override suspend fun labelImageFile(file: File) = emptyList<DetectedImageLabel>()
        }
        val readyEntities = object : SystemEntityAnalyzer {
            override fun checkAvailability() = SystemEntityAvailability.AVAILABLE
            override suspend fun extractEntities(text: String) = emptyList<ClassifiedTextEntity>()
        }

        val engine = SmartNoteAiEngine(
            database = database,
            settingsRepository = settingsRepo,
            runtimeBridge = cpuBridge,
            applicationScope = scope,
            idleUnloadMs = 0L,
            geminiNanoAssistant = fakeNano,
            imageLabelingEngine = readyImageLabeler,
            systemEntityAnalyzer = readyEntities
        )
        engine.refreshDiagnostics(forceRefresh = true)
        engine.embedText("Verify capability preservation after embedding")
        val diag = engine.diagnosticsFlow.value
        assertTrue(diag.capabilitiesProbed)
        assertEquals(GeminiNanoAvailability.AVAILABLE, diag.geminiNanoCapabilities.fastAvailability)
        assertEquals(GeminiNanoAvailability.DOWNLOADABLE, diag.geminiNanoCapabilities.fullAvailability)
        assertEquals("gemini-nano-v2", diag.geminiNanoCapabilities.baseModelName)
        assertEquals(ImageLabelingAvailability.AVAILABLE, diag.imageLabelingAvailability)
        assertEquals(SystemEntityAvailability.AVAILABLE, diag.systemEntityAvailability)

        // 3. Expanded AI settings defaults & persistence
        val defaultSettings = settingsRepo.settingsFlow.first()
        assertTrue(defaultSettings.allowNewTopicTags)
        assertTrue(defaultSettings.useGeminiNanoForOcr)
        assertTrue(defaultSettings.useGeminiNanoForSearch)

        settingsRepo.setMaxPredictedTags(99)
        settingsRepo.setTagSensitivity(TagPredictionSensitivity.CONSERVATIVE)
        settingsRepo.setAllowNewTopicTags(false)
        settingsRepo.setPreferExistingTags(false)
        settingsRepo.setLearnFromUserTags(false)
        settingsRepo.setUseImageLabelingForAi(false)
        settingsRepo.setUseSystemEntitiesForAi(false)
        settingsRepo.setUseSemanticEmbeddingsForTags(false)
        settingsRepo.setGeminiNanoMode(GeminiNanoMode.ALWAYS_PREFER)
        settingsRepo.setGeminiNanoVariant(GeminiNanoVariant.FULL_QUALITY)
        settingsRepo.setUseGeminiNanoForTitles(false)
        settingsRepo.setUseGeminiNanoForOcr(false)
        settingsRepo.setUseGeminiNanoForSearch(false)
        settingsRepo.setTitleStyle(TitleStylePreference.DESCRIPTIVE)

        val persisted = settingsRepo.settingsFlow.first()
        assertEquals(6, persisted.maxPredictedTags)
        assertEquals(TagPredictionSensitivity.CONSERVATIVE, persisted.tagSensitivity)
        assertFalse(persisted.allowNewTopicTags)
        assertFalse(persisted.preferExistingTags)
        assertFalse(persisted.learnFromUserTags)
        assertFalse(persisted.useImageLabelingForAi)
        assertFalse(persisted.useSystemEntitiesForAi)
        assertFalse(persisted.useSemanticEmbeddingsForTags)
        assertEquals(GeminiNanoMode.ALWAYS_PREFER, persisted.geminiNanoMode)
        assertEquals(GeminiNanoVariant.FULL_QUALITY, persisted.geminiNanoVariant)
        assertFalse(persisted.useGeminiNanoForTitles)
        assertFalse(persisted.useGeminiNanoForOcr)
        assertFalse(persisted.useGeminiNanoForSearch)
        assertEquals(GeminiNanoFeaturePolicy.OFF, persisted.effectiveGeminiNanoTitlesPolicy)
        assertEquals(GeminiNanoFeaturePolicy.OFF, persisted.effectiveGeminiNanoSearchPolicy)
        assertEquals(GeminiNanoFeaturePolicy.OFF, persisted.effectiveGeminiNanoOcrPolicy)
        assertEquals(TitleStylePreference.DESCRIPTIVE, persisted.titleStyle)
        assertEquals(64, persisted.effectiveMaxTitleLength)
    }
}
