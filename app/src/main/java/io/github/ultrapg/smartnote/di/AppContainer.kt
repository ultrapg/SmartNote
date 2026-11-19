package io.github.ultrapg.smartnote.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.ultrapg.smartnote.ai.AndroidLiteRtRuntimeBridge
import io.github.ultrapg.smartnote.ai.AndroidSystemEntityAnalyzer
import io.github.ultrapg.smartnote.ai.ImageLabelingEngine
import io.github.ultrapg.smartnote.ai.MlKitGeminiNanoAssistant
import io.github.ultrapg.smartnote.ai.MlKitImageLabelingEngine
import io.github.ultrapg.smartnote.ai.OnDeviceGenAiAssistant
import io.github.ultrapg.smartnote.ai.SmartNoteAiEngine
import io.github.ultrapg.smartnote.ai.SmartTitleGenerator
import io.github.ultrapg.smartnote.ai.SystemEntityAnalyzer
import io.github.ultrapg.smartnote.backup.BackupManager
import io.github.ultrapg.smartnote.capture.AttachmentImporter
import io.github.ultrapg.smartnote.capture.NoteShareExporter
import io.github.ultrapg.smartnote.data.local.SmartNoteDatabase
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.operations.SmartNoteOperationsApi
import io.github.ultrapg.smartnote.duplicates.SmartDuplicateDetector
import io.github.ultrapg.smartnote.domain.model.GeminiNanoVariant
import io.github.ultrapg.smartnote.integration.AppShortcutsManager
import io.github.ultrapg.smartnote.integration.widgets.WidgetRefreshHelper
import io.github.ultrapg.smartnote.links.LinkMetadataFetcher
import io.github.ultrapg.smartnote.ocr.MlKitBundledOcrEngine
import io.github.ultrapg.smartnote.ocr.OcrEngine
import io.github.ultrapg.smartnote.ocr.SmartOcrCoordinator
import io.github.ultrapg.smartnote.search.SearchIndexer
import io.github.ultrapg.smartnote.search.SmartSearchEngine
import io.github.ultrapg.smartnote.tags.SmartTaggingCoordinator
import io.github.ultrapg.smartnote.tags.SmartTagsEngine
import io.github.ultrapg.smartnote.ui.editor.NoteAutosaveCoordinator
import io.github.ultrapg.smartnote.worker.NoteProcessingWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val Context.smartNoteDataStore: DataStore<Preferences> by preferencesDataStore(name = "smartnote_settings")

class AppContainer(
    private val context: Context
) {
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            dataStore = context.smartNoteDataStore,
            bootPrefs = context.getSharedPreferences("smartnote_boot_cache", Context.MODE_PRIVATE)
        )
    }

    val database: SmartNoteDatabase by lazy {
        SmartNoteDatabase.create(context)
    }

    val attachmentStorage: AttachmentStorage by lazy {
        AttachmentStorage(context)
    }

    val searchIndexer: SearchIndexer by lazy {
        SearchIndexer(database)
    }

    val ocrEngine: OcrEngine by lazy {
        MlKitBundledOcrEngine(context)
    }

    val systemEntityAnalyzer: SystemEntityAnalyzer by lazy {
        AndroidSystemEntityAnalyzer(context)
    }

    val imageLabelingEngine: ImageLabelingEngine by lazy {
        MlKitImageLabelingEngine(context)
    }

    val geminiNanoAssistant: OnDeviceGenAiAssistant by lazy {
        MlKitGeminiNanoAssistant(applicationScope)
    }

    val smartOcrCoordinator: SmartOcrCoordinator by lazy {
        SmartOcrCoordinator(ocrEngine, geminiNanoAssistant)
    }

    val smartTagsEngine: SmartTagsEngine by lazy {
        SmartTagsEngine()
    }

    val linkMetadataFetcher: LinkMetadataFetcher by lazy {
        LinkMetadataFetcher(settingsRepository)
    }

    val aiEngine: SmartNoteAiEngine by lazy {
        SmartNoteAiEngine(
            database = database,
            settingsRepository = settingsRepository,
            runtimeBridge = AndroidLiteRtRuntimeBridge(context),
            applicationScope = applicationScope,
            geminiNanoAssistant = geminiNanoAssistant,
            imageLabelingEngine = imageLabelingEngine,
            systemEntityAnalyzer = systemEntityAnalyzer
        )
    }

    val smartTaggingCoordinator: SmartTaggingCoordinator by lazy {
        SmartTaggingCoordinator(
            smartTagsEngine = smartTagsEngine,
            aiEngine = aiEngine,
            systemEntityAnalyzer = systemEntityAnalyzer,
            imageLabelingEngine = imageLabelingEngine,
            geminiNanoAssistant = geminiNanoAssistant,
            attachmentStorage = attachmentStorage
        )
    }

    val smartTitleGenerator: SmartTitleGenerator by lazy {
        SmartTitleGenerator(aiEngine, geminiNanoAssistant)
    }

    val appShortcutsManager: AppShortcutsManager by lazy {
        AppShortcutsManager(context)
    }

    val tagRepository: TagRepository by lazy {
        TagRepository(
            database = database,
            searchIndexer = searchIndexer,
            onTagsChanged = { notifyShellChanged() }
        )
    }

    val noteRepository: NoteRepository by lazy {
        NoteRepository(
            database = database,
            attachmentStorage = attachmentStorage,
            searchIndexer = searchIndexer,
            tagRepository = tagRepository,
            onNoteChangedForBackground = { noteId ->
                NoteProcessingWorker.enqueueForNote(context, noteId)
            },
            onNotesUiMutated = { notifyShellChanged() }
        )
    }

    val autosaveCoordinator: NoteAutosaveCoordinator by lazy {
        NoteAutosaveCoordinator(
            noteRepository = noteRepository,
            applicationScope = applicationScope
        )
    }

    val searchEngine: SmartSearchEngine by lazy {
        SmartSearchEngine(
            database = database,
            noteRepository = noteRepository,
            semanticScoreProvider = { query, candidateIds ->
                aiEngine.scoreSemanticSimilarity(query, candidateIds)
            },
            aiQueryExpander = { query, tags ->
                val settings = settingsRepository.currentSnapshot
                if (settings.effectiveUseGeminiNanoForSearch) {
                    geminiNanoAssistant.expandSearchQuery(
                        query = query,
                        availableTags = tags,
                        preferFast = settings.geminiNanoVariant == GeminiNanoVariant.FAST_FIRST
                    )
                } else {
                    emptyList()
                }
            }
        )
    }

    val duplicateDetector: SmartDuplicateDetector by lazy {
        SmartDuplicateDetector(database, noteRepository)
    }

    val attachmentImporter: AttachmentImporter by lazy {
        AttachmentImporter(context, attachmentStorage)
    }

    val noteShareExporter: NoteShareExporter by lazy {
        NoteShareExporter(context, attachmentStorage)
    }

    val operationsApi: SmartNoteOperationsApi by lazy {
        SmartNoteOperationsApi(
            noteRepository = noteRepository,
            tagRepository = tagRepository,
            searchEngine = searchEngine,
            settingsRepository = settingsRepository,
            titleGenerator = smartTitleGenerator
        )
    }

    val backupManager: BackupManager by lazy {
        BackupManager(
            context = context,
            database = database,
            attachmentStorage = attachmentStorage,
            searchIndexer = searchIndexer
        )
    }

    private fun notifyShellChanged() {
        applicationScope.launch {
            val active = noteRepository.getAllSummariesSnapshot(includeArchived = false)
            appShortcutsManager.updateDynamicPinnedShortcuts(active)
            WidgetRefreshHelper.refreshAllWidgets(context)
        }
    }
}
