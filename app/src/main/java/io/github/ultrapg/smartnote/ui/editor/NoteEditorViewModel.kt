package io.github.ultrapg.smartnote.ui.editor

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.ultrapg.smartnote.ai.SmartTitleGenerator
import io.github.ultrapg.smartnote.capture.AttachmentImporter
import io.github.ultrapg.smartnote.capture.NoteShareExporter
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.AttachmentInfo
import io.github.ultrapg.smartnote.domain.model.DuplicatePairInfo
import io.github.ultrapg.smartnote.domain.model.LinkPreviewInfo
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NoteDetail
import io.github.ultrapg.smartnote.domain.model.NoteSourceType
import io.github.ultrapg.smartnote.domain.model.NoteTag
import io.github.ultrapg.smartnote.domain.model.ReminderInfo
import io.github.ultrapg.smartnote.domain.model.RepeatRule
import io.github.ultrapg.smartnote.duplicates.SmartDuplicateDetector
import io.github.ultrapg.smartnote.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val reminder: ReminderInfo? = null,
    val duplicateMatches: List<DuplicatePairInfo> = emptyList(),
    val autosaveStatus: AutosaveStatus = AutosaveStatus.IDLE,
    val suggestedTitle: String? = null,
    val autoTitleEnabled: Boolean = false,
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
    private val reminderScheduler: ReminderScheduler,
    private val duplicateDetector: SmartDuplicateDetector,
    private val settingsRepository: SettingsRepository,
    private val smartTitleGenerator: SmartTitleGenerator = SmartTitleGenerator()
) : ViewModel() {

    private val activeNoteId: String = savedStateHandle.get<String>(KEY_NOTE_ID)
        ?: initialNoteId
        ?: UUID.randomUUID().toString()

    private var editRevision = 0L
    private var hasUserEditedLocally = false
    private var userManuallyClearedTitle = false
    private var autoTitleEnabledCache = settingsRepository.currentSnapshot.effectiveAutoTitleEnabled
    private var lastDupeCheckRevision: Long = -1L
    private var lastDupeEnabled: Boolean = false
    private var cachedDupes: List<DuplicatePairInfo> = emptyList()
    private val defaultSourceType: NoteSourceType =
        if (fromQuickTile) NoteSourceType.QUICK_TILE else NoteSourceType.MANUAL
    private var persistedInDb: Boolean = (initialNoteId != null)
    private var hasDiscardedBlankDraft: Boolean = false
    private val activeImportsCount = AtomicInteger(0)

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
                autoTitleEnabled = autoTitleEnabledCache,
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
            autoTitleEnabled = autoTitleEnabledCache,
            suggestedTitle = computeSuggestedTitle(
                enabled = autoTitleEnabledCache,
                title = restoredTitle,
                blocks = listOf(initialBlock),
                attachments = emptyList(),
                links = emptyList()
            ),
            isLoaded = true
        )
    }

    private fun initializeNote() {
        viewModelScope.launch {
            combine(
                noteRepository.observeNoteDetail(activeNoteId),
                autosaveCoordinator.statusFlow,
                settingsRepository.settingsFlow
            ) { detail, saveStatus, settings ->
                Triple(detail, saveStatus, settings)
            }.collect { (detail, saveStatus, settings) ->
                if (detail == null) {
                    val autoTitleActive = settings.effectiveAutoTitleEnabled
                    autoTitleEnabledCache = autoTitleActive
                    _uiState.update { cur ->
                        if (!cur.isLoaded) {
                            cur
                        } else {
                            val candidateTitle = computeSuggestedTitle(
                                enabled = autoTitleActive,
                                title = cur.title,
                                blocks = cur.blocks,
                                attachments = cur.attachments.values.toList(),
                                links = cur.links
                            )
                            cur.copy(
                                autosaveStatus = saveStatus,
                                suggestedTitle = candidateTitle,
                                autoTitleEnabled = autoTitleActive
                            )
                        }
                    }
                    return@collect
                }
                persistedInDb = true
                val autoTitleActive = settings.effectiveAutoTitleEnabled
                val dupeActive = settings.effectiveDuplicateDetectionEnabled
                autoTitleEnabledCache = autoTitleActive
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
                        autoTitleActive = autoTitleActive
                    )
                }
            }
        }
    }

    private fun NoteEditorUiState.withDetailSnapshot(
        detail: NoteDetail,
        dupes: List<DuplicatePairInfo>,
        saveStatus: AutosaveStatus,
        autoTitleActive: Boolean
    ): NoteEditorUiState {
        val attMap = detail.attachments.associateBy { it.id }
        // Preserve active local typing buffer while only refreshing metadata/OCR/tags/links
        if (hasUserEditedLocally && isLoaded && saveStatus == AutosaveStatus.PENDING) {
            val candidateTitle = computeSuggestedTitle(
                enabled = autoTitleActive,
                title = title,
                blocks = blocks,
                attachments = attMap.values.toList(),
                links = detail.links
            )
            return copy(
                isPinned = detail.isPinned,
                isArchived = detail.isArchived,
                isDeleted = detail.isDeleted,
                tags = detail.tags,
                attachments = attMap,
                links = detail.links,
                reminder = detail.reminder,
                duplicateMatches = dupes,
                autosaveStatus = saveStatus,
                suggestedTitle = candidateTitle,
                autoTitleEnabled = autoTitleActive
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
            attachments = attMap.values.toList(),
            links = detail.links
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
            reminder = detail.reminder,
            duplicateMatches = dupes,
            autosaveStatus = saveStatus,
            suggestedTitle = candidateTitle,
            autoTitleEnabled = autoTitleActive,
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
                autoTitleActive = autoTitleEnabledCache
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
            val suggestion = computeSuggestedTitle(
                enabled = state.autoTitleEnabled,
                title = newTitle,
                blocks = state.blocks,
                attachments = state.attachments.values.toList(),
                links = state.links
            )
            state.copy(title = newTitle, suggestedTitle = suggestion)
        }
        enqueueAutosave()
    }

    fun applySuggestedTitle() {
        val candidate = _uiState.value.suggestedTitle?.takeIf { it.isNotBlank() } ?: return
        userManuallyClearedTitle = false
        updateTitle(candidate)
    }

    fun generateSmartTitleNow() {
        if (!autoTitleEnabledCache) return
        val state = _uiState.value
        viewModelScope.launch {
            val ocrTexts = state.attachments.values.mapNotNull { it.ocrText?.takeIf(String::isNotBlank) }
            val generated = smartTitleGenerator.generateSmartTitle(
                blocks = state.blocks,
                ocrTexts = ocrTexts,
                links = state.links,
                allowNeuralScoring = autoTitleEnabledCache
            )
            if (generated.isNotBlank()) {
                userManuallyClearedTitle = false
                updateTitle(generated)
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
            val suggestion = computeSuggestedTitle(
                enabled = state.autoTitleEnabled,
                title = state.title,
                blocks = updated,
                attachments = state.attachments.values.toList(),
                links = state.links
            )
            state.copy(blocks = updated, suggestedTitle = suggestion)
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
        viewModelScope.launch {
            prepareNoteForForeignMutation()
            tagRepository.addUserTagToNote(activeNoteId, rawName)
        }
    }

    fun confirmSuggestedTag(tagId: String) {
        viewModelScope.launch {
            tagRepository.confirmSuggestedTag(activeNoteId, tagId)
        }
    }

    fun dismissOrRemoveTag(tagId: String) {
        viewModelScope.launch {
            tagRepository.dismissOrRemoveTagFromNote(activeNoteId, tagId)
        }
    }

    fun saveReminder(triggerAtMs: Long, repeatRule: RepeatRule, useLiveUpdate: Boolean) {
        activeImportsCount.incrementAndGet()
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    prepareNoteForForeignMutation()
                    reminderScheduler.scheduleOrUpdateReminder(activeNoteId, triggerAtMs, repeatRule, useLiveUpdate)
                    refreshUiFromDetailSnapshot()
                }
            } finally {
                activeImportsCount.decrementAndGet()
            }
        }
    }

    fun removeReminder() {
        viewModelScope.launch {
            reminderScheduler.cancelReminderForNote(activeNoteId)
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
            state.tags.isEmpty() &&
            state.reminder == null
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
        private val reminderScheduler: ReminderScheduler,
        private val duplicateDetector: SmartDuplicateDetector,
        private val settingsRepository: SettingsRepository,
        private val smartTitleGenerator: SmartTitleGenerator = SmartTitleGenerator()
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
                reminderScheduler = reminderScheduler,
                duplicateDetector = duplicateDetector,
                settingsRepository = settingsRepository,
                smartTitleGenerator = smartTitleGenerator
            ) as T
        }
    }
}
