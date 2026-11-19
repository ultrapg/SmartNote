package io.github.ultrapg.smartnote.ui.editor

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.ultrapg.smartnote.ai.DetectedImageLabel
import io.github.ultrapg.smartnote.ai.ImageLabelingAvailability
import io.github.ultrapg.smartnote.ai.ImageLabelingEngine
import io.github.ultrapg.smartnote.ai.OnDeviceGenAiAssistant
import io.github.ultrapg.smartnote.ai.SmartNoteAiEngine
import io.github.ultrapg.smartnote.ai.SmartTitleGenerator
import io.github.ultrapg.smartnote.ai.SystemEntityAnalyzer
import io.github.ultrapg.smartnote.ai.SystemEntityAvailability
import io.github.ultrapg.smartnote.capture.AttachmentImporter
import io.github.ultrapg.smartnote.capture.NoteShareExporter
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.AttachmentStorage
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.AttachmentInfo
import io.github.ultrapg.smartnote.domain.model.DuplicatePairInfo
import io.github.ultrapg.smartnote.domain.model.GeminiNanoFeaturePolicy
import io.github.ultrapg.smartnote.domain.model.GeminiNanoVariant
import io.github.ultrapg.smartnote.domain.model.LinkPreviewInfo
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NoteDetail
import io.github.ultrapg.smartnote.domain.model.NoteSourceType
import io.github.ultrapg.smartnote.domain.model.NoteTag
import io.github.ultrapg.smartnote.domain.model.OcrStatus
import io.github.ultrapg.smartnote.duplicates.SmartDuplicateDetector
import io.github.ultrapg.smartnote.links.DetectedLink
import io.github.ultrapg.smartnote.links.SmartLinkDetector
import io.github.ultrapg.smartnote.ocr.SafeOcrBitmapLoader
import io.github.ultrapg.smartnote.ocr.SmartOcrCoordinator
import io.github.ultrapg.smartnote.tags.SmartTagSuggestion
import io.github.ultrapg.smartnote.tags.SmartTaggingCoordinator
import io.github.ultrapg.smartnote.tags.SmartTagsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Immutable
data class NoteEditorUiState(
    val noteId: String = "",
    val title: String = "",
    val blocks: List<NoteBlock> = emptyList(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val tags: List<NoteTag> = emptyList(),
    val attachments: Map<String, AttachmentInfo> = emptyMap(),
    val links: List<LinkPreviewInfo> = emptyList(),
    val duplicateMatches: List<DuplicatePairInfo> = emptyList(),
    val autosaveStatus: AutosaveStatus = AutosaveStatus.IDLE,
    val suggestedTitle: String? = null,
    val aiFeaturesEnabled: Boolean = true,
    val autoTitleEnabled: Boolean = false,
    val smartTagsEnabled: Boolean = true,
    val maxPredictedTags: Int = 3,
    val availableTags: List<String> = emptyList(),
    val liveTagSuggestions: List<SmartTagSuggestion> = emptyList(),
    val isGeneratingTags: Boolean = false,
    val rescanningOcrAttachmentId: String? = null,
    val feedbackMessageEvent: Pair<Int, String>? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val isLoaded: Boolean = false
)

class NoteEditorViewModel(
    private val initialNoteId: String?,
    private val startChecklist: Boolean,
    private val fromQuickTile: Boolean,
    private val savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val autosaveCoordinator: NoteAutosaveCoordinator,
    private val attachmentImporter: AttachmentImporter,
    private val noteShareExporter: NoteShareExporter,
    private val duplicateDetector: SmartDuplicateDetector,
    private val settingsRepository: SettingsRepository,
    private val smartTitleGenerator: SmartTitleGenerator = SmartTitleGenerator(),
    private val smartTagsEngine: SmartTagsEngine = SmartTagsEngine(),
    private val aiEngine: SmartNoteAiEngine? = null,
    private val attachmentStorage: AttachmentStorage? = null,
    private val systemEntityAnalyzer: SystemEntityAnalyzer? = null,
    private val imageLabelingEngine: ImageLabelingEngine? = null,
    private val geminiNanoAssistant: OnDeviceGenAiAssistant? = null,
    private val smartTaggingCoordinator: SmartTaggingCoordinator? = null,
    private val smartOcrCoordinator: SmartOcrCoordinator? = null
) : ViewModel() {

    private val activeNoteId: String = savedStateHandle.get<String>(KEY_NOTE_ID)
        ?: initialNoteId
        ?: UUID.randomUUID().toString()

    private val resolvedCoordinator: SmartTaggingCoordinator by lazy {
        smartTaggingCoordinator ?: SmartTaggingCoordinator(
            smartTagsEngine = smartTagsEngine,
            aiEngine = aiEngine,
            systemEntityAnalyzer = systemEntityAnalyzer,
            imageLabelingEngine = imageLabelingEngine,
            geminiNanoAssistant = geminiNanoAssistant,
            attachmentStorage = attachmentStorage
        )
    }

    private var editRevision = 0L
    private var hasUserEditedLocally = false
    private var userManuallyClearedTitle = false
    private var latestSettingsCache = settingsRepository.currentSnapshot
    private var aiFeaturesEnabledCache = latestSettingsCache.aiFeaturesEnabled
    private var autoTitleEnabledCache = latestSettingsCache.effectiveAutoTitleEnabled
    private var smartTagsEnabledCache = latestSettingsCache.effectiveSmartTagsEnabled
    private var availableTagsCache: List<String> = emptyList()
    private var lastDupeCheckRevision: Long = -1L
    private var lastDupeEnabled: Boolean = false
    private var cachedDupes: List<DuplicatePairInfo> = emptyList()
    private val defaultSourceType: NoteSourceType =
        if (fromQuickTile) NoteSourceType.QUICK_TILE else NoteSourceType.MANUAL
    private var persistedInDb: Boolean = (initialNoteId != null)
    private var hasDiscardedBlankDraft: Boolean = false
    private val activeImportsCount = AtomicInteger(0)
    private var feedbackSeq = 0

    private val _uiState = MutableStateFlow(buildInitialUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    init {
        savedStateHandle[KEY_NOTE_ID] = activeNoteId
        if (initialNoteId == null && !isUiStateCompletelyBlank(_uiState.value)) {
            enqueueAutosave()
        }
        initializeNote()
    }

    private fun buildInitialUiState(): NoteEditorUiState {
        if (initialNoteId != null) {
            return NoteEditorUiState(
                noteId = activeNoteId,
                aiFeaturesEnabled = aiFeaturesEnabledCache,
                autoTitleEnabled = autoTitleEnabledCache,
                smartTagsEnabled = smartTagsEnabledCache,
                maxPredictedTags = latestSettingsCache.effectiveMaxPredictedTags,
                isLoaded = false
            )
        }
        val restoredTitle = savedStateHandle.get<String>(KEY_DRAFT_TITLE).orEmpty()
        val restoredBody = savedStateHandle.get<String>(KEY_DRAFT_FIRST_TEXT).orEmpty()
        val initialBlock = NoteBlock(
            id = UUID.randomUUID().toString(),
            noteId = activeNoteId,
            position = 0,
            type = if (startChecklist) NoteBlockType.CHECKLIST else NoteBlockType.TEXT,
            textContent = restoredBody
        )
        return NoteEditorUiState(
            noteId = activeNoteId,
            title = restoredTitle,
            blocks = listOf(initialBlock),
            aiFeaturesEnabled = aiFeaturesEnabledCache,
            autoTitleEnabled = autoTitleEnabledCache,
            smartTagsEnabled = smartTagsEnabledCache,
            maxPredictedTags = latestSettingsCache.effectiveMaxPredictedTags,
            suggestedTitle = computeSuggestedTitle(
                enabled = autoTitleEnabledCache,
                title = restoredTitle,
                blocks = listOf(initialBlock),
                attachments = emptyList(),
                links = emptyList()
            ),
            liveTagSuggestions = computeLiveTagSuggestions(
                enabled = aiFeaturesEnabledCache,
                title = restoredTitle,
                blocks = listOf(initialBlock),
                attachments = emptyList(),
                links = emptyList(),
                existingNoteTags = emptyList(),
                vocabulary = availableTagsCache
            ),
            isLoaded = true
        )
    }

    private fun initializeNote() {
        viewModelScope.launch {
            combine(
                noteRepository.observeNoteDetail(activeNoteId),
                autosaveCoordinator.statusFlow,
                settingsRepository.settingsFlow,
                tagRepository.observeAllTags()
            ) { detail, saveStatus, settings, allTags ->
                QuadSnapshot(detail, saveStatus, settings, allTags.map { it.name })
            }.collect { (detail, saveStatus, settings, allTagNames) ->
                availableTagsCache = allTagNames
                latestSettingsCache = settings
                val aiActive = settings.aiFeaturesEnabled
                val autoTitleActive = settings.effectiveAutoTitleEnabled
                val smartTagsActive = settings.effectiveSmartTagsEnabled
                val maxTags = settings.effectiveMaxPredictedTags
                aiFeaturesEnabledCache = aiActive
                autoTitleEnabledCache = autoTitleActive
                smartTagsEnabledCache = smartTagsActive

                if (detail == null) {
                    _uiState.update { cur ->
                        if (!cur.isLoaded) {
                            cur
                        } else {
                            val atts = cur.attachments.values.toList()
                            val candidateTitle = computeSuggestedTitle(
                                enabled = autoTitleActive,
                                title = cur.title,
                                blocks = cur.blocks,
                                attachments = atts,
                                links = cur.links
                            )
                            val tagSug = computeLiveTagSuggestions(
                                enabled = aiActive,
                                title = cur.title,
                                blocks = cur.blocks,
                                attachments = atts,
                                links = cur.links,
                                existingNoteTags = cur.tags,
                                vocabulary = allTagNames
                            )
                            cur.copy(
                                autosaveStatus = saveStatus,
                                suggestedTitle = candidateTitle,
                                aiFeaturesEnabled = aiActive,
                                autoTitleEnabled = autoTitleActive,
                                smartTagsEnabled = smartTagsActive,
                                maxPredictedTags = maxTags,
                                availableTags = allTagNames,
                                liveTagSuggestions = tagSug
                            )
                        }
                    }
                    return@collect
                }
                persistedInDb = true
                val dupeActive = settings.effectiveDuplicateDetectionEnabled
                if (!dupeActive) {
                    cachedDupes = emptyList()
                    lastDupeCheckRevision = detail.contentRevision
                    lastDupeEnabled = false
                } else if (detail.contentRevision != lastDupeCheckRevision || !lastDupeEnabled) {
                    cachedDupes = withContext(Dispatchers.IO) {
                        runCatching { duplicateDetector.findDuplicatesForNote(activeNoteId) }.getOrDefault(emptyList())
                    }
                    lastDupeCheckRevision = detail.contentRevision
                    lastDupeEnabled = true
                }
                val dupes = cachedDupes
                _uiState.update { cur ->
                    cur.withDetailSnapshot(
                        detail = detail,
                        dupes = dupes,
                        saveStatus = saveStatus,
                        aiFeaturesActive = aiActive,
                        autoTitleActive = autoTitleActive,
                        smartTagsActive = smartTagsActive,
                        maxPredictedTags = maxTags,
                        availableTags = allTagNames
                    )
                }
            }
        }
    }

    private data class QuadSnapshot(
        val detail: NoteDetail?,
        val saveStatus: AutosaveStatus,
        val settings: io.github.ultrapg.smartnote.domain.model.AppSettings,
        val allTagNames: List<String>
    )

    private fun NoteEditorUiState.withDetailSnapshot(
        detail: NoteDetail,
        dupes: List<DuplicatePairInfo>,
        saveStatus: AutosaveStatus,
        aiFeaturesActive: Boolean,
        autoTitleActive: Boolean,
        smartTagsActive: Boolean,
        maxPredictedTags: Int,
        availableTags: List<String>
    ): NoteEditorUiState {
        val attMap = detail.attachments.associateBy { it.id }
        val atts = attMap.values.toList()
        // Preserve active local typing buffer while only refreshing metadata/OCR/tags/links
        if (hasUserEditedLocally && isLoaded && saveStatus == AutosaveStatus.PENDING) {
            val candidateTitle = computeSuggestedTitle(
                enabled = autoTitleActive,
                title = title,
                blocks = blocks,
                attachments = atts,
                links = detail.links
            )
            val tagSug = computeLiveTagSuggestions(
                enabled = aiFeaturesActive,
                title = title,
                blocks = blocks,
                attachments = atts,
                links = detail.links,
                existingNoteTags = detail.tags,
                vocabulary = availableTags
            )
            return copy(
                isPinned = detail.isPinned,
                isArchived = detail.isArchived,
                isDeleted = detail.isDeleted,
                tags = detail.tags,
                attachments = attMap,
                links = detail.links,
                duplicateMatches = dupes,
                autosaveStatus = saveStatus,
                suggestedTitle = candidateTitle,
                aiFeaturesEnabled = aiFeaturesActive,
                autoTitleEnabled = autoTitleActive,
                smartTagsEnabled = smartTagsActive,
                maxPredictedTags = maxPredictedTags,
                availableTags = availableTags,
                liveTagSuggestions = tagSug
            )
        }
        val resolvedTitle = if (hasUserEditedLocally) title else detail.title
        val resolvedBlocks = if (hasUserEditedLocally) blocks else detail.blocks.ifEmpty {
            listOf(
                NoteBlock(
                    id = UUID.randomUUID().toString(),
                    noteId = detail.id,
                    position = 0,
                    type = NoteBlockType.TEXT
                )
            )
        }
        val candidateTitle = computeSuggestedTitle(
            enabled = autoTitleActive,
            title = resolvedTitle,
            blocks = resolvedBlocks,
            attachments = atts,
            links = detail.links
        )
        val tagSug = computeLiveTagSuggestions(
            enabled = aiFeaturesActive,
            title = resolvedTitle,
            blocks = resolvedBlocks,
            attachments = atts,
            links = detail.links,
            existingNoteTags = detail.tags,
            vocabulary = availableTags
        )
        return NoteEditorUiState(
            noteId = detail.id,
            title = resolvedTitle,
            blocks = resolvedBlocks,
            isPinned = detail.isPinned,
            isArchived = detail.isArchived,
            isDeleted = detail.isDeleted,
            tags = detail.tags,
            attachments = attMap,
            links = detail.links,
            duplicateMatches = dupes,
            autosaveStatus = saveStatus,
            suggestedTitle = candidateTitle,
            aiFeaturesEnabled = aiFeaturesActive,
            autoTitleEnabled = autoTitleActive,
            smartTagsEnabled = smartTagsActive,
            maxPredictedTags = maxPredictedTags,
            availableTags = availableTags,
            liveTagSuggestions = tagSug,
            isGeneratingTags = isGeneratingTags,
            rescanningOcrAttachmentId = rescanningOcrAttachmentId,
            feedbackMessageEvent = feedbackMessageEvent,
            updatedAtMs = detail.updatedAtMs,
            isLoaded = true
        )
    }

    private suspend fun refreshUiFromDetailSnapshot() {
        val detail = noteRepository.getNoteDetail(activeNoteId) ?: return
        persistedInDb = true
        hasDiscardedBlankDraft = false
        _uiState.update { cur ->
            cur.withDetailSnapshot(
                detail = detail,
                dupes = cachedDupes,
                saveStatus = autosaveCoordinator.statusFlow.value,
                aiFeaturesActive = aiFeaturesEnabledCache,
                autoTitleActive = autoTitleEnabledCache,
                smartTagsActive = smartTagsEnabledCache,
                maxPredictedTags = latestSettingsCache.effectiveMaxPredictedTags,
                availableTags = availableTagsCache
            )
        }
    }

    fun updateTitle(newTitle: String) {
        val prevTitle = _uiState.value.title
        if (prevTitle.isNotBlank() && newTitle.isBlank()) {
            userManuallyClearedTitle = true
        } else if (newTitle.isNotBlank()) {
            userManuallyClearedTitle = false
        }
        hasUserEditedLocally = true
        savedStateHandle[KEY_DRAFT_TITLE] = newTitle
        _uiState.update { state ->
            val atts = state.attachments.values.toList()
            val suggestion = computeSuggestedTitle(
                enabled = state.autoTitleEnabled,
                title = newTitle,
                blocks = state.blocks,
                attachments = atts,
                links = state.links
            )
            val tagSug = computeLiveTagSuggestions(
                enabled = state.aiFeaturesEnabled,
                title = newTitle,
                blocks = state.blocks,
                attachments = atts,
                links = state.links,
                existingNoteTags = state.tags,
                vocabulary = state.availableTags
            )
            state.copy(
                title = newTitle,
                suggestedTitle = suggestion,
                liveTagSuggestions = tagSug
            )
        }
        enqueueAutosave()
    }

    fun applySuggestedTitle() {
        val candidate = _uiState.value.suggestedTitle?.takeIf { it.isNotBlank() } ?: return
        userManuallyClearedTitle = false
        updateTitle(candidate)
    }

    fun generateSmartTitleNow() {
        if (!aiFeaturesEnabledCache) return
        val state = _uiState.value
        viewModelScope.launch {
            val settings = runCatching { settingsRepository.settingsFlow.first() }
                .getOrDefault(latestSettingsCache)
            if (!settings.aiFeaturesEnabled) return@launch
            val preferFast = settings.geminiNanoVariant == GeminiNanoVariant.FAST_FIRST
            val titlePolicy = settings.effectiveGeminiNanoTitlesPolicy
            val allowTitleNano = titlePolicy != GeminiNanoFeaturePolicy.OFF
            val forceTitleNano = titlePolicy == GeminiNanoFeaturePolicy.FORCE
            if (allowTitleNano) {
                geminiNanoAssistant?.triggerBackgroundDownloadIfNeeded(preferFast)
            }
            val ocrTexts = state.attachments.values.mapNotNull { it.ocrText?.takeIf(String::isNotBlank) }
            val candidates = withContext(Dispatchers.IO) {
                val combinedText = buildNoteTextForAnalysis(title = "", blocks = state.blocks, ocrTexts = ocrTexts)
                val systemEntities = if (
                    settings.effectiveUseSystemEntities &&
                    combinedText.isNotBlank() &&
                    systemEntityAnalyzer?.checkAvailability() == SystemEntityAvailability.AVAILABLE
                ) {
                    systemEntityAnalyzer.extractEntities(combinedText)
                } else {
                    emptyList()
                }
                val imageLabels = if (settings.effectiveUseImageLabeling) {
                    collectImageLabels(state.attachments.values.toList())
                } else {
                    emptyList()
                }
                val userProseChars = state.blocks.sumOf { it.textContent.trim().length }
                val primaryAttachment = state.blocks
                    .firstOrNull { it.type == NoteBlockType.IMAGE }
                    ?.attachmentId
                    ?.let { state.attachments[it] }
                    ?: state.attachments.values.firstOrNull()
                val primaryBitmap = if (
                    allowTitleNano &&
                    geminiNanoAssistant != null &&
                    attachmentStorage != null &&
                    primaryAttachment != null &&
                    (forceTitleNano || userProseChars < 160)
                ) {
                    SafeOcrBitmapLoader.decodeUprightDownsampledBitmap(
                        attachmentStorage.resolvePrivateFile(primaryAttachment.relativeFilePath),
                        maxDimPx = 768
                    )
                } else {
                    null
                }
                try {
                    smartTitleGenerator.generateTitleCandidates(
                        blocks = state.blocks,
                        ocrTexts = ocrTexts,
                        links = state.links,
                        allowNeuralScoring = settings.aiFeaturesEnabled,
                        systemEntities = systemEntities,
                        imageLabels = imageLabels.map { it.text },
                        primaryImageBitmap = primaryBitmap,
                        allowGeminiNano = allowTitleNano,
                        forceGeminiNano = forceTitleNano,
                        preferFastGemini = preferFast,
                        maxTitleLength = settings.effectiveMaxTitleLength
                    )
                } finally {
                    if (primaryBitmap != null && !primaryBitmap.isRecycled) {
                        primaryBitmap.recycle()
                    }
                }
            }
            if (candidates.isEmpty()) return@launch
            val currentClean = state.title.trim()
            val chosen = if (SmartTitleGenerator.isPlaceholderOrBlankTitle(currentClean)) {
                candidates.first()
            } else {
                val matchIdx = candidates.indexOfFirst { it.equals(currentClean, ignoreCase = true) }
                if (matchIdx >= 0 && candidates.size > 1) {
                    candidates[(matchIdx + 1) % candidates.size]
                } else {
                    candidates.firstOrNull { !it.equals(currentClean, ignoreCase = true) } ?: candidates.first()
                }
            }
            if (chosen.isNotBlank()) {
                userManuallyClearedTitle = false
                updateTitle(chosen)
            }
        }
    }

    /**
     * Ensures the note ends with an editable block so tapping empty space below the note
     * focuses cleanly at the bottom (reusing a trailing text/blank-checklist block or adding one).
     */
    fun ensureTrailingEditableBlock(): String {
        val state = _uiState.value
        val last = state.blocks.lastOrNull()
        if (last != null) {
            when (last.type) {
                NoteBlockType.TEXT -> return last.id
                NoteBlockType.CHECKLIST -> if (last.textContent.isBlank()) return last.id
                NoteBlockType.IMAGE, NoteBlockType.LINK -> Unit
            }
        }
        val nextType = if (last?.type == NoteBlockType.CHECKLIST) {
            NoteBlockType.CHECKLIST
        } else {
            NoteBlockType.TEXT
        }
        val newBlock = NoteBlock(
            id = UUID.randomUUID().toString(),
            noteId = activeNoteId,
            position = state.blocks.size,
            type = nextType,
            textContent = ""
        )
        val shouldPersistNewBlock = state.blocks.isNotEmpty()
        if (shouldPersistNewBlock) {
            hasUserEditedLocally = true
        }
        _uiState.update { cur ->
            val currentLast = cur.blocks.lastOrNull()
            if (currentLast != null && currentLast.type == NoteBlockType.TEXT) {
                cur
            } else {
                cur.copy(blocks = reindex(cur.blocks + newBlock))
            }
        }
        val resolvedId = _uiState.value.blocks.lastOrNull()?.id ?: newBlock.id
        if (shouldPersistNewBlock) {
            enqueueAutosave()
        }
        return resolvedId
    }

    fun updateBlockText(blockId: String, newText: String) {
        hasUserEditedLocally = true
        _uiState.update { state ->
            val updated = state.blocks.map { blk ->
                if (blk.id == blockId) blk.copy(textContent = newText) else blk
            }
            updated.firstOrNull { it.type == NoteBlockType.TEXT }?.let {
                savedStateHandle[KEY_DRAFT_FIRST_TEXT] = it.textContent
            }
            val atts = state.attachments.values.toList()
            val suggestion = computeSuggestedTitle(
                enabled = state.autoTitleEnabled,
                title = state.title,
                blocks = updated,
                attachments = atts,
                links = state.links
            )
            val tagSug = computeLiveTagSuggestions(
                enabled = state.aiFeaturesEnabled,
                title = state.title,
                blocks = updated,
                attachments = atts,
                links = state.links,
                existingNoteTags = state.tags,
                vocabulary = state.availableTags
            )
            state.copy(
                blocks = updated,
                suggestedTitle = suggestion,
                liveTagSuggestions = tagSug
            )
        }
        enqueueAutosave()
    }

    fun toggleChecklistChecked(blockId: String) {
        hasUserEditedLocally = true
        _uiState.update { state ->
            state.copy(
                blocks = state.blocks.map { blk ->
                    if (blk.id == blockId && blk.type == NoteBlockType.CHECKLIST) {
                        blk.copy(isChecked = !blk.isChecked)
                    } else {
                        blk
                    }
                }
            )
        }
        enqueueAutosave()
    }

    fun insertChecklistItemAfter(afterBlockId: String? = null) {
        hasUserEditedLocally = true
        _uiState.update { state ->
            val list = state.blocks.toMutableList()
            val newBlock = NoteBlock(
                id = UUID.randomUUID().toString(),
                noteId = activeNoteId,
                position = list.size,
                type = NoteBlockType.CHECKLIST,
                textContent = ""
            )
            val idx = afterBlockId?.let { id -> list.indexOfFirst { it.id == id } } ?: -1
            if (idx >= 0) {
                list.add(idx + 1, newBlock)
            } else {
                list.add(newBlock)
            }
            state.copy(blocks = reindex(list))
        }
        enqueueAutosave()
    }

    fun deleteChecklistItem(blockId: String) {
        hasUserEditedLocally = true
        _uiState.update { state ->
            val remaining = state.blocks.filterNot { it.id == blockId }.toMutableList()
            if (remaining.isEmpty()) {
                remaining += NoteBlock(
                    id = UUID.randomUUID().toString(),
                    noteId = activeNoteId,
                    position = 0,
                    type = NoteBlockType.TEXT
                )
            }
            state.copy(blocks = reindex(remaining))
        }
        enqueueAutosave()
    }

    fun moveChecklistItem(blockId: String, delta: Int) {
        hasUserEditedLocally = true
        _uiState.update { state ->
            val list = state.blocks.toMutableList()
            val idx = list.indexOfFirst { it.id == blockId }
            val targetIdx = idx + delta
            if (idx >= 0 && targetIdx in list.indices && list[targetIdx].type == NoteBlockType.CHECKLIST) {
                val moved = list.removeAt(idx)
                list.add(targetIdx, moved)
            }
            state.copy(blocks = reindex(list))
        }
        enqueueAutosave()
    }

    /**
     * Non-destructively toggles between prose TEXT lines and CHECKLIST items.
     */
    fun convertSelectionOrToggleChecklistMode() {
        hasUserEditedLocally = true
        _uiState.update { state ->
            val hasChecklists = state.blocks.any { it.type == NoteBlockType.CHECKLIST }
            val newBlocks = if (hasChecklists) {
                val mediaBlocks = state.blocks.filter { it.type == NoteBlockType.IMAGE || it.type == NoteBlockType.LINK }
                val mergedText = state.blocks
                    .filter { it.type == NoteBlockType.TEXT || it.type == NoteBlockType.CHECKLIST }
                    .joinToString("\n") { it.textContent }
                val textBlock = NoteBlock(
                    id = UUID.randomUUID().toString(),
                    noteId = activeNoteId,
                    position = 0,
                    type = NoteBlockType.TEXT,
                    textContent = mergedText
                )
                reindex(listOf(textBlock) + mediaBlocks)
            } else {
                val expanded = mutableListOf<NoteBlock>()
                for (blk in state.blocks) {
                    if (blk.type == NoteBlockType.TEXT) {
                        val lines = blk.textContent.lines().ifEmpty { listOf("") }
                        lines.forEach { line ->
                            expanded += NoteBlock(
                                id = UUID.randomUUID().toString(),
                                noteId = activeNoteId,
                                position = expanded.size,
                                type = NoteBlockType.CHECKLIST,
                                textContent = line
                            )
                        }
                    } else {
                        expanded += blk
                    }
                }
                reindex(expanded)
            }
            state.copy(blocks = newBlocks)
        }
        enqueueAutosave()
    }

    fun wrapOrInsertMarkdownSnippet(prefix: String, suffix: String = "") {
        hasUserEditedLocally = true
        _uiState.update { state ->
            val targetIdx = state.blocks.indexOfLast { it.type == NoteBlockType.TEXT }
            val updatedBlocks = if (targetIdx >= 0) {
                val blk = state.blocks[targetIdx]
                val nextText = if (suffix.isEmpty()) {
                    if (blk.textContent.isBlank() || blk.textContent.endsWith("\n")) {
                        "${blk.textContent}$prefix"
                    } else {
                        "${blk.textContent}\n$prefix"
                    }
                } else {
                    "${blk.textContent}${if (blk.textContent.isEmpty() || blk.textContent.endsWith(" ") || blk.textContent.endsWith("\n")) "" else " "}$prefix$suffix"
                }
                state.blocks.mapIndexed { i, b -> if (i == targetIdx) b.copy(textContent = nextText) else b }
            } else {
                state.blocks + NoteBlock(
                    id = UUID.randomUUID().toString(),
                    noteId = activeNoteId,
                    position = state.blocks.size,
                    type = NoteBlockType.TEXT,
                    textContent = "$prefix$suffix"
                )
            }
            state.copy(blocks = reindex(updatedBlocks))
        }
        enqueueAutosave()
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        activeImportsCount.incrementAndGet()
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    runCatching {
                        prepareNoteForForeignMutation()
                        hasUserEditedLocally = false
                        var importedAny = false
                        for (uri in uris) {
                            val att = attachmentImporter.importUriDirectlyToNote(activeNoteId, uri) ?: continue
                            noteRepository.addAttachmentToNote(activeNoteId, att, insertImageBlock = true)
                            importedAny = true
                        }
                        if (importedAny) {
                            persistedInDb = true
                            hasDiscardedBlankDraft = false
                        }
                        refreshUiFromDetailSnapshot()
                    }
                }
            } finally {
                activeImportsCount.decrementAndGet()
            }
        }
    }

    fun removeImageBlock(blockId: String, attachmentId: String?) {
        viewModelScope.launch {
            flushAutosaveNow()
            noteRepository.removeAttachmentBlock(activeNoteId, blockId, attachmentId)
            hasUserEditedLocally = false
        }
    }

    fun togglePin() {
        val next = !_uiState.value.isPinned
        _uiState.update { it.copy(isPinned = next) }
        viewModelScope.launch {
            prepareNoteForForeignMutation()
            noteRepository.setPinned(activeNoteId, next)
        }
    }

    fun toggleArchive() {
        val next = !_uiState.value.isArchived
        viewModelScope.launch {
            prepareNoteForForeignMutation()
            noteRepository.setArchived(activeNoteId, next)
        }
    }

    fun duplicateCurrentNote(onDuplicated: (String) -> Unit) {
        viewModelScope.launch {
            prepareNoteForForeignMutation()
            val copyId = noteRepository.duplicateNote(activeNoteId)
            if (copyId != null) onDuplicated(copyId)
        }
    }

    fun moveToRecycleBin(onMoved: () -> Unit) {
        viewModelScope.launch {
            autosaveCoordinator.discardPending(activeNoteId)
            if (persistedInDb) {
                noteRepository.moveToRecycleBin(activeNoteId)
            }
            onMoved()
        }
    }

    fun shareCurrentNote() {
        viewModelScope.launch {
            prepareNoteForForeignMutation()
            noteRepository.getNoteDetail(activeNoteId)?.let { detail ->
                noteShareExporter.shareNote(detail)
            }
        }
    }

    fun addUserTag(rawName: String) {
        activeImportsCount.incrementAndGet()
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    prepareNoteForForeignMutation()
                    tagRepository.addUserTagToNote(activeNoteId, rawName)
                    refreshUiFromDetailSnapshot()
                }
            } finally {
                activeImportsCount.decrementAndGet()
            }
        }
    }

    fun applyAutoTagsNow() {
        val state = _uiState.value
        if (!state.aiFeaturesEnabled || state.isGeneratingTags) return
        val hasContent = state.title.isNotBlank() ||
            state.blocks.any { it.textContent.isNotBlank() || !it.linkUrl.isNullOrBlank() } ||
            state.attachments.values.any { !it.ocrText.isNullOrBlank() || it.isScreenshot } ||
            state.links.isNotEmpty()
        if (!hasContent) return

        activeImportsCount.incrementAndGet()
        _uiState.update { it.copy(isGeneratingTags = true) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val settings = runCatching { settingsRepository.settingsFlow.first() }
                        .getOrDefault(latestSettingsCache)
                    if (!settings.aiFeaturesEnabled) return@withContext
                    val currentState = _uiState.value
                    val existingTagNames = currentState.tags.mapTo(HashSet()) { it.name }
                    val generated = withContext(Dispatchers.IO) {
                        val ocrTexts = currentState.attachments.values.mapNotNull { it.ocrText?.takeIf(String::isNotBlank) }
                        val detectedLinks = extractDetectedLinks(currentState.links, currentState.blocks)
                        val dismissedNames = tagRepository.getDismissedTagNamesForNote(activeNoteId)
                        val summaries = noteRepository.getAllSummariesSnapshot(includeArchived = true)
                        resolvedCoordinator.generateTagsForNote(
                            noteId = activeNoteId,
                            title = currentState.title,
                            blocks = currentState.blocks,
                            ocrTexts = ocrTexts,
                            links = detectedLinks,
                            attachments = currentState.attachments.values.toList(),
                            existingNoteTags = existingTagNames,
                            dismissedTagNames = dismissedNames,
                            existingUserVocabulary = currentState.availableTags,
                            allSummaries = summaries,
                            settings = settings,
                            isExplicitManualTrigger = true
                        ).suggestions
                    }

                    if (generated.isNotEmpty()) {
                        prepareNoteForForeignMutation()
                        generated.forEach { sug ->
                            tagRepository.addGeneratedTagToNote(activeNoteId, sug.name, sug.confidence)
                        }
                        refreshUiFromDetailSnapshot()
                    } else {
                        emitFeedback("No confident tags found for this note")
                    }
                }
            } finally {
                _uiState.update { it.copy(isGeneratingTags = false) }
                activeImportsCount.decrementAndGet()
            }
        }
    }

    fun confirmSuggestedTag(tagId: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                tagRepository.confirmSuggestedTag(activeNoteId, tagId)
                refreshUiFromDetailSnapshot()
            }
        }
    }

    fun dismissOrRemoveTag(tagId: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                tagRepository.dismissOrRemoveTagFromNote(activeNoteId, tagId)
                refreshUiFromDetailSnapshot()
            }
        }
    }

    fun dismissDuplicatePair(pair: DuplicatePairInfo) {
        viewModelScope.launch {
            duplicateDetector.dismissDuplicatePair(pair.noteA.id, pair.noteB.id)
            _uiState.update { state ->
                state.copy(duplicateMatches = state.duplicateMatches.filterNot { it.pairKey == pair.pairKey })
            }
        }
    }

    fun rescanAttachmentOcr(attachmentId: String) {
        val coordinator = smartOcrCoordinator ?: return
        val storage = attachmentStorage ?: return
        val attachment = _uiState.value.attachments[attachmentId] ?: return
        if (_uiState.value.rescanningOcrAttachmentId != null) return

        _uiState.update { it.copy(rescanningOcrAttachmentId = attachmentId) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    val settings = runCatching { settingsRepository.settingsFlow.first() }
                        .getOrDefault(latestSettingsCache)
                    val file = storage.resolvePrivateFile(attachment.relativeFilePath)
                    val outcome = withContext(Dispatchers.IO) {
                        coordinator.recognizeFileWithOptionalAi(
                            file = file,
                            settings = settings,
                            forceAiAssist = true
                        )
                    }
                    val status = when {
                        !outcome.succeeded -> OcrStatus.FAILED
                        outcome.text.isBlank() -> OcrStatus.EMPTY
                        else -> OcrStatus.COMPLETED
                    }
                    noteRepository.updateAttachmentOcrResult(
                        noteId = activeNoteId,
                        attachmentId = attachmentId,
                        recognizedText = outcome.text,
                        confidence = outcome.confidence,
                        status = status
                    )
                    refreshUiFromDetailSnapshot()
                    val msg = when {
                        !outcome.succeeded -> "Could not read text from image"
                        outcome.text.isBlank() -> "No readable text detected"
                        outcome.usedGeminiNano -> "OCR updated with Gemini Nano"
                        else -> "OCR text updated"
                    }
                    emitFeedback(msg)
                }
            } finally {
                _uiState.update { state ->
                    if (state.rescanningOcrAttachmentId == attachmentId) {
                        state.copy(rescanningOcrAttachmentId = null)
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun insertOcrTextIntoNote(rawText: String) {
        val clean = rawText.trim()
        if (clean.isBlank()) return
        hasUserEditedLocally = true
        _uiState.update { state ->
            val currentBlocks = state.blocks.toMutableList()
            val last = currentBlocks.lastOrNull()
            if (last != null && last.type == NoteBlockType.TEXT && last.textContent.isBlank()) {
                currentBlocks[currentBlocks.lastIndex] = last.copy(textContent = clean)
            } else {
                currentBlocks += NoteBlock(
                    id = UUID.randomUUID().toString(),
                    noteId = activeNoteId,
                    position = currentBlocks.size,
                    type = NoteBlockType.TEXT,
                    textContent = clean
                )
            }
            val updated = reindex(currentBlocks)
            updated.firstOrNull { it.type == NoteBlockType.TEXT }?.let {
                savedStateHandle[KEY_DRAFT_FIRST_TEXT] = it.textContent
            }
            val atts = state.attachments.values.toList()
            val suggestion = computeSuggestedTitle(
                enabled = state.autoTitleEnabled,
                title = state.title,
                blocks = updated,
                attachments = atts,
                links = state.links
            )
            val tagSug = computeLiveTagSuggestions(
                enabled = state.aiFeaturesEnabled,
                title = state.title,
                blocks = updated,
                attachments = atts,
                links = state.links,
                existingNoteTags = state.tags,
                vocabulary = state.availableTags
            )
            state.copy(
                blocks = updated,
                suggestedTitle = suggestion,
                liveTagSuggestions = tagSug
            )
        }
        enqueueAutosave()
        emitFeedback("Inserted extracted text into note")
    }

    private fun emitFeedback(message: String) {
        feedbackSeq++
        val event = feedbackSeq to message
        _uiState.update { it.copy(feedbackMessageEvent = event) }
    }

    fun flushPendingEditsOnly() {
        val state = _uiState.value
        if (isUiStateCompletelyBlank(state)) {
            autosaveCoordinator.discardPending(activeNoteId)
            return
        }
        hasDiscardedBlankDraft = false
        applyAutoTitleBeforeFlushIfNeeded()
        autosaveCoordinator.flushAsync(activeNoteId)
    }

    fun flushAutosaveAsync() {
        flushPendingEditsOnly()
    }

    fun finishEditorSession() {
        val state = _uiState.value
        if (isUiStateCompletelyBlank(state)) {
            if (persistedInDb && !hasDiscardedBlankDraft) {
                hasDiscardedBlankDraft = true
                autosaveCoordinator.discardAndDeleteIfBlankAsync(activeNoteId)
            } else {
                autosaveCoordinator.discardPending(activeNoteId)
            }
            return
        }
        hasDiscardedBlankDraft = false
        applyAutoTitleBeforeFlushIfNeeded()
        autosaveCoordinator.flushAsync(activeNoteId)
    }

    suspend fun flushAutosaveNow() {
        val state = _uiState.value
        if (isUiStateCompletelyBlank(state)) {
            autosaveCoordinator.discardPending(activeNoteId)
            if (persistedInDb) {
                hasDiscardedBlankDraft = true
                noteRepository.discardIfCompletelyBlank(activeNoteId)
                persistedInDb = false
            }
            return
        }
        hasDiscardedBlankDraft = false
        applyAutoTitleBeforeFlushIfNeeded()
        autosaveCoordinator.flushNow(activeNoteId)
        persistedInDb = true
    }

    private suspend fun prepareNoteForForeignMutation() {
        val state = _uiState.value
        if (!isUiStateCompletelyBlank(state)) {
            hasDiscardedBlankDraft = false
            applyAutoTitleBeforeFlushIfNeeded()
            autosaveCoordinator.flushNow(activeNoteId)
            persistedInDb = true
        } else {
            autosaveCoordinator.discardPending(activeNoteId)
        }
        if (!persistedInDb || noteRepository.getNoteDetail(activeNoteId) == null) {
            val latest = _uiState.value
            noteRepository.createNote(
                id = activeNoteId,
                title = latest.title,
                blocks = latest.blocks,
                isPinned = latest.isPinned,
                sourceType = defaultSourceType,
                scheduleBackgroundWork = false
            )
            persistedInDb = true
            hasDiscardedBlankDraft = false
        }
    }

    private fun isUiStateCompletelyBlank(state: NoteEditorUiState): Boolean {
        if (activeImportsCount.get() > 0) return false
        return !state.isPinned &&
            !state.isArchived &&
            !state.isDeleted &&
            state.title.isBlank() &&
            state.blocks.all {
                it.textContent.isBlank() && it.attachmentId.isNullOrBlank() && it.linkUrl.isNullOrBlank()
            } &&
            state.attachments.isEmpty() &&
            state.links.isEmpty() &&
            state.tags.isEmpty()
    }

    private fun applyAutoTitleBeforeFlushIfNeeded() {
        val state = _uiState.value
        if (!autoTitleEnabledCache || userManuallyClearedTitle || state.title.isNotBlank()) return
        val hasContent = state.blocks.any { it.textContent.isNotBlank() || !it.linkUrl.isNullOrBlank() } ||
            state.attachments.values.any { !it.ocrText.isNullOrBlank() }
        if (!hasContent) return

        val candidate = smartTitleGenerator.generateFastTitle(
            blocks = state.blocks,
            ocrTexts = state.attachments.values.mapNotNull { it.ocrText?.takeIf(String::isNotBlank) },
            links = state.links
        )
        if (candidate.isBlank()) return

        hasUserEditedLocally = true
        savedStateHandle[KEY_DRAFT_TITLE] = candidate
        _uiState.update { it.copy(title = candidate, suggestedTitle = null) }
        editRevision++
        autosaveCoordinator.scheduleSave(
            PendingNoteDraft(
                noteId = activeNoteId,
                title = candidate,
                blocks = state.blocks,
                isPinned = state.isPinned,
                revisionToken = editRevision,
                sourceType = defaultSourceType
            )
        )
    }

    private fun computeLiveTagSuggestions(
        enabled: Boolean,
        title: String,
        blocks: List<NoteBlock>,
        attachments: List<AttachmentInfo>,
        links: List<LinkPreviewInfo>,
        existingNoteTags: List<NoteTag>,
        vocabulary: List<String>
    ): List<SmartTagSuggestion> {
        if (!enabled) return emptyList()
        val hasAnyContent = title.isNotBlank() ||
            blocks.any { it.textContent.isNotBlank() || !it.linkUrl.isNullOrBlank() } ||
            attachments.any { !it.ocrText.isNullOrBlank() || it.isScreenshot } ||
            links.isNotEmpty()
        if (!hasAnyContent) return emptyList()

        val ocrTexts = attachments.mapNotNull { it.ocrText?.takeIf(String::isNotBlank) }
        val detectedLinks = extractDetectedLinks(links, blocks)

        val existingLower = existingNoteTags.map { it.name.lowercase(Locale.ROOT) }.toSet()
        return smartTagsEngine.suggestTags(
            title = title,
            blocks = blocks,
            ocrTexts = ocrTexts,
            links = detectedLinks,
            hasScreenshot = attachments.any { it.isScreenshot },
            existingUserVocabulary = vocabulary,
            maxTags = latestSettingsCache.effectiveMaxPredictedTags,
            minConfidence = SmartTagsEngine.minConfidenceFor(latestSettingsCache.tagSensitivity),
            preferExistingTags = latestSettingsCache.preferExistingTags
        ).filter { it.name.lowercase(Locale.ROOT) !in existingLower }
    }

    private suspend fun collectImageLabels(attachments: List<AttachmentInfo>): List<DetectedImageLabel> {
        val storage = attachmentStorage ?: return emptyList()
        val labeler = imageLabelingEngine ?: return emptyList()
        if (attachments.isEmpty()) return emptyList()
        if (labeler.checkAvailability() == ImageLabelingAvailability.UNAVAILABLE) return emptyList()
        val merged = LinkedHashMap<String, DetectedImageLabel>()
        for (att in attachments.take(2)) {
            val file = storage.resolvePrivateFile(att.relativeFilePath)
            labeler.labelImageFile(file).forEach { lbl ->
                val key = lbl.text.lowercase(Locale.ROOT)
                val prev = merged[key]
                if (prev == null || lbl.confidence > prev.confidence) {
                    merged[key] = lbl
                }
            }
        }
        return merged.values.sortedByDescending { it.confidence }.take(6)
    }

    private fun buildNoteTextForAnalysis(
        title: String,
        blocks: List<NoteBlock>,
        ocrTexts: List<String>
    ): String = buildString {
        if (title.isNotBlank()) append(title).append('\n')
        blocks.forEach { if (it.textContent.isNotBlank()) append(it.textContent).append('\n') }
        ocrTexts.forEach { if (it.isNotBlank()) append(it).append('\n') }
    }.trim()

    private fun extractDetectedLinks(
        links: List<LinkPreviewInfo>,
        blocks: List<NoteBlock>
    ): List<DetectedLink> = buildList {
        links.forEach { l -> add(DetectedLink(l.rawUrl, l.canonicalUrl, l.domain)) }
        blocks.forEach { blk ->
            when (blk.type) {
                NoteBlockType.TEXT, NoteBlockType.CHECKLIST -> addAll(SmartLinkDetector.extractLinks(blk.textContent))
                NoteBlockType.LINK -> blk.linkUrl?.let { SmartLinkDetector.parseSingleUrl(it)?.let(::add) }
                NoteBlockType.IMAGE -> Unit
            }
        }
    }.distinctBy { it.canonicalUrl }

    private fun computeSuggestedTitle(
        enabled: Boolean,
        title: String,
        blocks: List<NoteBlock>,
        attachments: List<AttachmentInfo>,
        links: List<LinkPreviewInfo>
    ): String? {
        if (!enabled || title.isNotBlank()) return null
        val candidate = smartTitleGenerator.generateFastTitle(
            blocks = blocks,
            ocrTexts = attachments.mapNotNull { it.ocrText?.takeIf(String::isNotBlank) },
            links = links
        )
        return candidate.takeIf { it.isNotBlank() }
    }

    private fun enqueueAutosave() {
        val state = _uiState.value
        if (!persistedInDb && isUiStateCompletelyBlank(state)) {
            autosaveCoordinator.discardPending(activeNoteId)
            return
        }
        hasDiscardedBlankDraft = false
        editRevision++
        autosaveCoordinator.scheduleSave(
            PendingNoteDraft(
                noteId = activeNoteId,
                title = state.title,
                blocks = state.blocks,
                isPinned = state.isPinned,
                revisionToken = editRevision,
                sourceType = defaultSourceType
            )
        )
    }

    private fun reindex(blocks: List<NoteBlock>): List<NoteBlock> =
        blocks.mapIndexed { index, block -> block.copy(noteId = activeNoteId, position = index) }

    override fun onCleared() {
        super.onCleared()
        finishEditorSession()
    }

    companion object {
        private const val KEY_NOTE_ID = "editor_note_id"
        private const val KEY_DRAFT_TITLE = "editor_draft_title"
        private const val KEY_DRAFT_FIRST_TEXT = "editor_draft_first_text"
    }

    class Factory(
        private val initialNoteId: String?,
        private val startChecklist: Boolean,
        private val fromQuickTile: Boolean,
        private val noteRepository: NoteRepository,
        private val tagRepository: TagRepository,
        private val autosaveCoordinator: NoteAutosaveCoordinator,
        private val attachmentImporter: AttachmentImporter,
        private val noteShareExporter: NoteShareExporter,
        private val duplicateDetector: SmartDuplicateDetector,
        private val settingsRepository: SettingsRepository,
        private val smartTitleGenerator: SmartTitleGenerator = SmartTitleGenerator(),
        private val smartTagsEngine: SmartTagsEngine = SmartTagsEngine(),
        private val aiEngine: SmartNoteAiEngine? = null,
        private val attachmentStorage: AttachmentStorage? = null,
        private val systemEntityAnalyzer: SystemEntityAnalyzer? = null,
        private val imageLabelingEngine: ImageLabelingEngine? = null,
        private val geminiNanoAssistant: OnDeviceGenAiAssistant? = null,
        private val smartTaggingCoordinator: SmartTaggingCoordinator? = null,
        private val smartOcrCoordinator: SmartOcrCoordinator? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: androidx.lifecycle.viewmodel.CreationExtras
        ): T {
            val handle = extras.createSavedStateHandle()
            return NoteEditorViewModel(
                initialNoteId = initialNoteId,
                startChecklist = startChecklist,
                fromQuickTile = fromQuickTile,
                savedStateHandle = handle,
                noteRepository = noteRepository,
                tagRepository = tagRepository,
                autosaveCoordinator = autosaveCoordinator,
                attachmentImporter = attachmentImporter,
                noteShareExporter = noteShareExporter,
                duplicateDetector = duplicateDetector,
                settingsRepository = settingsRepository,
                smartTitleGenerator = smartTitleGenerator,
                smartTagsEngine = smartTagsEngine,
                aiEngine = aiEngine,
                attachmentStorage = attachmentStorage,
                systemEntityAnalyzer = systemEntityAnalyzer,
                imageLabelingEngine = imageLabelingEngine,
                geminiNanoAssistant = geminiNanoAssistant,
                smartTaggingCoordinator = smartTaggingCoordinator,
                smartOcrCoordinator = smartOcrCoordinator
            ) as T
        }
    }
}
