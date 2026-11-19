package io.github.ultrapg.smartnote.domain.operations

import io.github.ultrapg.smartnote.ai.SmartTitleGenerator
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NoteSourceType
import io.github.ultrapg.smartnote.domain.model.TagSource
import io.github.ultrapg.smartnote.search.SearchFilterSpec
import io.github.ultrapg.smartnote.search.SmartSearchEngine
import kotlinx.coroutines.flow.first
import java.util.UUID

data class AgentNoteMetadataDto(
    val id: String,
    val title: String,
    val preview: String,
    val tags: List<String>,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val hasChecklist: Boolean,
    val checklistTotal: Int,
    val checklistCompleted: Int,
    val imageCount: Int,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class AgentNoteDetailDto(
    val id: String,
    val title: String,
    val content: String,
    val checklistItems: List<String>,
    val tags: List<String>,
    val urls: List<String>,
    val ocrSummary: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class AgentOperationResult(
    val success: Boolean,
    val message: String,
    val noteId: String? = null
)

class SmartNoteOperationException(
    val code: String,
    override val message: String
) : IllegalArgumentException(message)

class SmartNoteOperationsApi(
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val searchEngine: SmartSearchEngine,
    private val settingsRepository: SettingsRepository,
    private val titleGenerator: SmartTitleGenerator = SmartTitleGenerator()
) {
    suspend fun createNote(
        title: String,
        content: String,
        tags: List<String> = emptyList(),
        isPinned: Boolean = false,
        checklistItems: List<String> = emptyList()
    ): AgentNoteDetailDto {
        val safeTitle = title.trim().take(MAX_TITLE_CHARS)
        val safeContent = content.trim().take(MAX_CONTENT_CHARS)
        val safeItems = checklistItems.map { it.trim().take(400) }.filter { it.isNotBlank() }.take(100)
        if (safeTitle.isBlank() && safeContent.isBlank() && safeItems.isEmpty()) {
            throw SmartNoteOperationException("INVALID_ARGUMENT", "Note cannot be completely empty.")
        }

        val noteId = UUID.randomUUID().toString()
        val blocks = buildList {
            if (safeContent.isNotBlank()) {
                add(
                    NoteBlock(
                        id = UUID.randomUUID().toString(),
                        noteId = noteId,
                        position = size,
                        type = NoteBlockType.TEXT,
                        textContent = safeContent
                    )
                )
            }
            safeItems.forEach { item ->
                add(
                    NoteBlock(
                        id = UUID.randomUUID().toString(),
                        noteId = noteId,
                        position = size,
                        type = NoteBlockType.CHECKLIST,
                        textContent = item,
                        isChecked = false
                    )
                )
            }
        }
        val resolvedTitle = if (safeTitle.isBlank() && settingsRepository.settingsFlow.first().effectiveAutoTitleEnabled) {
            titleGenerator.generateFastTitle(blocks = blocks).take(MAX_TITLE_CHARS)
        } else {
            safeTitle
        }
        noteRepository.createNote(
            id = noteId,
            title = resolvedTitle,
            blocks = blocks,
            initialTags = tags.map { it.trim() }.filter { it.isNotBlank() }.take(10),
            isPinned = isPinned,
            sourceType = NoteSourceType.MANUAL
        )
        return getNote(noteId)
            ?: throw SmartNoteOperationException("NOT_FOUND", "Created note could not be reloaded.")
    }

    suspend fun getNote(
        noteId: String
    ): AgentNoteDetailDto? {
        val validId = validateId(noteId)
        val detail = noteRepository.getNoteDetail(validId) ?: return null
        val textBody = detail.blocks
            .filter { it.type == NoteBlockType.TEXT }
            .joinToString("\n\n") { it.textContent }
        val checklist = detail.blocks
            .filter { it.type == NoteBlockType.CHECKLIST }
            .map { "${if (it.isChecked) "[x]" else "[ ]"} ${it.textContent}" }
        val ocr = detail.attachments
            .mapNotNull { it.ocrText?.takeIf(String::isNotBlank) }
            .joinToString("\n")
            .take(1000)

        return AgentNoteDetailDto(
            id = detail.id,
            title = detail.title,
            content = textBody,
            checklistItems = checklist,
            tags = detail.tags.filter { it.source == TagSource.USER }.map { it.name },
            urls = detail.links.map { it.rawUrl },
            ocrSummary = ocr,
            isPinned = detail.isPinned,
            isArchived = detail.isArchived,
            isDeleted = detail.isDeleted,
            createdAtMs = detail.createdAtMs,
            updatedAtMs = detail.updatedAtMs
        )
    }

    suspend fun listNotes(
        limit: Int = 20,
        offset: Int = 0,
        tagFilter: String? = null,
        pinnedOnly: Boolean = false,
        includeArchived: Boolean = false,
        afterEpochMs: Long? = null,
        beforeEpochMs: Long? = null
    ): List<AgentNoteMetadataDto> {
        val safeLimit = limit.coerceIn(1, 50)
        val safeOffset = offset.coerceAtLeast(0)
        val all = noteRepository.getAllSummariesSnapshot(includeArchived = includeArchived)
            .filter { summary ->
                val matchesTag = tagFilter.isNullOrBlank() ||
                    summary.tags.any { it.name.equals(tagFilter.trim(), ignoreCase = true) }
                val matchesPin = !pinnedOnly || summary.isPinned
                val matchesAfter = afterEpochMs == null || summary.updatedAtMs >= afterEpochMs
                val matchesBefore = beforeEpochMs == null || summary.updatedAtMs <= beforeEpochMs
                matchesTag && matchesPin && matchesAfter && matchesBefore
            }
            .sortedWith(compareByDescending<io.github.ultrapg.smartnote.domain.model.NoteSummary> { it.isPinned }.thenByDescending { it.updatedAtMs })

        return all.drop(safeOffset).take(safeLimit).map(::toMetadataDto)
    }

    suspend fun searchNotes(
        query: String,
        limit: Int = 20,
        tagFilter: String? = null
    ): List<AgentNoteMetadataDto> {
        val safeQuery = query.trim().take(256)
        val safeLimit = limit.coerceIn(1, 50)
        val filter = SearchFilterSpec(
            tagNames = tagFilter?.trim()?.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: emptySet()
        )
        val response = searchEngine.search(
            rawQuery = safeQuery,
            filter = filter,
            enableSemanticRerank = settingsRepository.settingsFlow.first().effectiveSemanticRerankEnabled
        )
        return response.results.take(safeLimit).map { toMetadataDto(it.note) }
    }

    suspend fun updateNote(
        noteId: String,
        newTitle: String? = null,
        newContent: String? = null,
        appendContent: String? = null
    ): AgentNoteDetailDto {
        val detail = noteRepository.getNoteDetail(validateId(noteId))
            ?: throw SmartNoteOperationException("NOT_FOUND", "Note not found.")
        if (detail.isDeleted) {
            throw SmartNoteOperationException("NOTE_IN_TRASH", "Restore note from recycle bin before editing.")
        }

        val resolvedTitle = newTitle?.trim()?.take(MAX_TITLE_CHARS) ?: detail.title
        var blocks = detail.blocks.toMutableList()

        if (newContent != null) {
            val cleaned = newContent.trim().take(MAX_CONTENT_CHARS)
            val nonText = blocks.filter { it.type != NoteBlockType.TEXT }
            val textBlock = NoteBlock(
                id = blocks.firstOrNull { it.type == NoteBlockType.TEXT }?.id ?: UUID.randomUUID().toString(),
                noteId = detail.id,
                position = 0,
                type = NoteBlockType.TEXT,
                textContent = cleaned
            )
            blocks = (listOf(textBlock) + nonText).toMutableList()
        } else if (!appendContent.isNullOrBlank()) {
            val append = appendContent.trim().take(MAX_CONTENT_CHARS)
            val firstTextIdx = blocks.indexOfFirst { it.type == NoteBlockType.TEXT }
            if (firstTextIdx >= 0) {
                val existing = blocks[firstTextIdx]
                val joined = if (existing.textContent.isBlank()) append else "${existing.textContent}\n$append"
                blocks[firstTextIdx] = existing.copy(textContent = joined.take(MAX_CONTENT_CHARS))
            } else {
                blocks.add(
                    0,
                    NoteBlock(
                        id = UUID.randomUUID().toString(),
                        noteId = detail.id,
                        position = 0,
                        type = NoteBlockType.TEXT,
                        textContent = append
                    )
                )
            }
        }

        noteRepository.saveNoteContent(
            noteId = detail.id,
            title = resolvedTitle,
            blocks = blocks,
            isPinned = detail.isPinned,
            scheduleBackgroundWork = true
        )
        return getNote(detail.id)
            ?: throw SmartNoteOperationException("NOT_FOUND", "Updated note could not be reloaded.")
    }

    suspend fun deleteNoteToRecycleBin(
        noteId: String,
        confirmedByUser: Boolean = true
    ): AgentOperationResult {
        if (!confirmedByUser) {
            throw SmartNoteOperationException(
                "CONFIRMATION_REQUIRED",
                "Deleting a note requires explicit user confirmation."
            )
        }
        val validId = validateId(noteId)
        val existing = noteRepository.getNoteDetail(validId)
            ?: throw SmartNoteOperationException("NOT_FOUND", "Note not found.")
        if (existing.isDeleted) {
            return AgentOperationResult(true, "Note is already in the Recycle Bin.", validId)
        }
        noteRepository.moveToRecycleBin(validId)
        return AgentOperationResult(true, "Moved note to SmartNote Recycle Bin (recoverable for 30 days).", validId)
    }

    suspend fun manageNoteTags(
        noteId: String,
        addTags: List<String> = emptyList(),
        removeTags: List<String> = emptyList()
    ): AgentNoteMetadataDto {
        val validId = validateId(noteId)
        val detail = noteRepository.getNoteDetail(validId)
            ?: throw SmartNoteOperationException("NOT_FOUND", "Note not found.")

        for (tagName in addTags.take(10)) {
            if (tagName.isNotBlank()) tagRepository.addUserTagToNote(validId, tagName)
        }
        if (removeTags.isNotEmpty()) {
            val lowerRemoves = removeTags.map { it.trim().lowercase() }.toSet()
            detail.tags.filter { it.name.lowercase() in lowerRemoves }.forEach { tag ->
                tagRepository.dismissOrRemoveTagFromNote(validId, tag.tagId)
            }
        }
        val updated = noteRepository.getAllSummariesSnapshot(includeArchived = true)
            .firstOrNull { it.id == validId }
            ?: throw SmartNoteOperationException("NOT_FOUND", "Note not found after tag update.")
        return toMetadataDto(updated)
    }

    suspend fun setNoteArchived(
        noteId: String,
        archived: Boolean
    ): AgentOperationResult {
        val validId = validateId(noteId)
        noteRepository.getNoteDetail(validId)
            ?: throw SmartNoteOperationException("NOT_FOUND", "Note not found.")
        noteRepository.setArchived(validId, archived)
        return AgentOperationResult(true, if (archived) "Note archived." else "Note unarchived.", validId)
    }

    suspend fun setNotePinned(
        noteId: String,
        pinned: Boolean
    ): AgentOperationResult {
        val validId = validateId(noteId)
        noteRepository.getNoteDetail(validId)
            ?: throw SmartNoteOperationException("NOT_FOUND", "Note not found.")
        noteRepository.setPinned(validId, pinned)
        return AgentOperationResult(true, if (pinned) "Note pinned." else "Note unpinned.", validId)
    }

    suspend fun getNoteMetadata(
        noteId: String
    ): AgentNoteMetadataDto? {
        val validId = validateId(noteId)
        val summary = noteRepository.getAllSummariesSnapshot(includeArchived = true)
            .firstOrNull { it.id == validId } ?: return null
        return toMetadataDto(summary)
    }

    private fun validateId(raw: String): String {
        val clean = raw.trim()
        if (clean.isBlank() || clean.length > 64 || !clean.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            throw SmartNoteOperationException("INVALID_ARGUMENT", "Invalid note ID format.")
        }
        return clean
    }

    private fun toMetadataDto(summary: io.github.ultrapg.smartnote.domain.model.NoteSummary): AgentNoteMetadataDto {
        return AgentNoteMetadataDto(
            id = summary.id,
            title = summary.title,
            preview = summary.previewText.take(280),
            tags = summary.tags.map { it.name },
            isPinned = summary.isPinned,
            isArchived = summary.isArchived,
            isDeleted = summary.isDeleted,
            hasChecklist = summary.hasChecklist,
            checklistTotal = summary.checklistTotalCount,
            checklistCompleted = summary.checklistCheckedCount,
            imageCount = summary.imageCount,
            createdAtMs = summary.createdAtMs,
            updatedAtMs = summary.updatedAtMs
        )
    }

    companion object {
        const val MAX_TITLE_CHARS = 300
        const val MAX_CONTENT_CHARS = 50_000
    }
}
