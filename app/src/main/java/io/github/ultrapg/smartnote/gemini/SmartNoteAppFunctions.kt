package io.github.ultrapg.smartnote.gemini

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionDisabledException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import io.github.ultrapg.smartnote.domain.model.RepeatRule
import io.github.ultrapg.smartnote.domain.operations.AgentNoteDetailDto
import io.github.ultrapg.smartnote.domain.operations.AgentNoteMetadataDto
import io.github.ultrapg.smartnote.domain.operations.SmartNoteOperationException
import io.github.ultrapg.smartnote.domain.operations.SmartNoteOperationsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

@AppFunctionSerializable
data class AppFunctionNoteSummary(
    val id: String,
    val title: String,
    val preview: String,
    val tags: List<String>,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val hasChecklist: Boolean,
    val reminderIso: String,
    val updatedAtMs: Long
)

@AppFunctionSerializable
data class AppFunctionNoteDetail(
    val id: String,
    val title: String,
    val content: String,
    val checklistItems: List<String>,
    val tags: List<String>,
    val urls: List<String>,
    val ocrSummary: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val reminderIso: String,
    val updatedAtMs: Long
)

@AppFunctionSerializable
data class AppFunctionActionStatus(
    val success: Boolean,
    val message: String,
    val noteId: String
)

class SmartNoteAppFunctions(
    private val operationsApi: SmartNoteOperationsApi
) {

    /**
     * Creates a new offline note in SmartNote.
     *
     * @param appFunctionContext System invocation context.
     * @param title Optional title of the note.
     * @param content Main body text of the note.
     * @param tags Optional tags to attach to the note.
     * @param isPinned Whether to pin the new note.
     * @return The newly created note details.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createNote(
        appFunctionContext: AppFunctionContext,
        title: String,
        content: String,
        tags: List<String>,
        isPinned: Boolean
    ): AppFunctionNoteDetail = runGuarded {
        operationsApi.createNote(
            title = title,
            content = content,
            tags = tags,
            isPinned = isPinned
        ).toAppFunctionDetail()
    }

    /**
     * Retrieves the full text, checklist items, tags, URLs, and OCR summary of a specific note.
     *
     * @param appFunctionContext System invocation context.
     * @param noteId Unique note ID.
     * @return Full note detail.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getNote(
        appFunctionContext: AppFunctionContext,
        noteId: String
    ): AppFunctionNoteDetail = runGuarded {
        operationsApi.getNote(noteId)?.toAppFunctionDetail()
            ?: throw SmartNoteOperationException("NOT_FOUND", "Note not found: $noteId")
    }

    /**
     * Lists notes with optional tag filtering, pinned filtering, and pagination.
     *
     * @param appFunctionContext System invocation context.
     * @param limit Maximum number of notes to return (1..50).
     * @param offset Number of notes to skip.
     * @param tagFilter Optional tag name filter, or empty string for all tags.
     * @param pinnedOnly True to list only pinned notes.
     * @return Matching note metadata summaries.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listNotes(
        appFunctionContext: AppFunctionContext,
        limit: Int,
        offset: Int,
        tagFilter: String,
        pinnedOnly: Boolean
    ): List<AppFunctionNoteSummary> = runGuarded {
        operationsApi.listNotes(
            limit = limit,
            offset = offset,
            tagFilter = tagFilter.takeIf { it.isNotBlank() },
            pinnedOnly = pinnedOnly
        ).map { it.toAppFunctionSummary() }
    }

    /**
     * Searches notes offline across titles, bodies, checklists, tags, URLs, and OCR screenshot text.
     *
     * @param appFunctionContext System invocation context.
     * @param query Natural keywords or #tag filter.
     * @param limit Maximum results to return (1..50).
     * @return Ranked matching notes.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchNotes(
        appFunctionContext: AppFunctionContext,
        query: String,
        limit: Int
    ): List<AppFunctionNoteSummary> = runGuarded {
        operationsApi.searchNotes(query = query, limit = limit)
            .map { it.toAppFunctionSummary() }
    }

    /**
     * Updates an existing note's title or content.
     *
     * @param appFunctionContext System invocation context.
     * @param noteId Unique note ID.
     * @param newTitle Replacement title, or empty string to keep existing title.
     * @param newContent Replacement body text, or empty string when only appending.
     * @param appendContent Additional text appended to the end of the note.
     * @return Updated note details.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun updateNote(
        appFunctionContext: AppFunctionContext,
        noteId: String,
        newTitle: String,
        newContent: String,
        appendContent: String
    ): AppFunctionNoteDetail = runGuarded {
        operationsApi.updateNote(
            noteId = noteId,
            newTitle = newTitle.takeIf { it.isNotBlank() },
            newContent = newContent.takeIf { it.isNotBlank() },
            appendContent = appendContent.takeIf { it.isNotBlank() }
        ).toAppFunctionDetail()
    }

    /**
     * Moves a note safely to the 30-day Recycle Bin after explicit confirmation.
     *
     * @param appFunctionContext System invocation context.
     * @param noteId Unique note ID.
     * @param confirmedByUser Explicit user confirmation flag.
     * @return Operation status.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun deleteNote(
        appFunctionContext: AppFunctionContext,
        noteId: String,
        confirmedByUser: Boolean
    ): AppFunctionActionStatus = runGuarded {
        val res = operationsApi.deleteNoteToRecycleBin(noteId, confirmedByUser)
        AppFunctionActionStatus(res.success, res.message, res.noteId.orEmpty())
    }

    /**
     * Adds or removes tags on an existing note.
     *
     * @param appFunctionContext System invocation context.
     * @param noteId Unique note ID.
     * @param addTags Tag names to add.
     * @param removeTags Tag names to remove.
     * @return Updated note metadata summary.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun manageNoteTags(
        appFunctionContext: AppFunctionContext,
        noteId: String,
        addTags: List<String>,
        removeTags: List<String>
    ): AppFunctionNoteSummary = runGuarded {
        operationsApi.manageNoteTags(noteId, addTags, removeTags).toAppFunctionSummary()
    }

    /**
     * Schedules or clears a local reminder on a note.
     *
     * @param appFunctionContext System invocation context.
     * @param noteId Unique note ID.
     * @param localDateTimeIso ISO-8601 local date-time (e.g. 2026-11-19T09:00), or empty to clear.
     * @param repeatRule Repeat interval: NONE, DAILY, WEEKLY, MONTHLY, or WEEKDAYS.
     * @return Reminder status.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun setNoteReminder(
        appFunctionContext: AppFunctionContext,
        noteId: String,
        localDateTimeIso: String,
        repeatRule: String
    ): AppFunctionActionStatus = runGuarded {
        val triggerMs = if (localDateTimeIso.isBlank()) {
            null
        } else {
            runCatching {
                LocalDateTime.parse(localDateTimeIso.trim())
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrElse {
                throw SmartNoteOperationException(
                    "INVALID_ARGUMENT",
                    "Invalid ISO local date-time '$localDateTimeIso'. Expected yyyy-MM-ddTHH:mm."
                )
            }
        }
        val rule = runCatching { RepeatRule.valueOf(repeatRule.uppercase()) }
            .getOrDefault(RepeatRule.NONE)
        val res = operationsApi.setNoteReminder(noteId, triggerMs, rule)
        AppFunctionActionStatus(res.success, res.message, res.noteId.orEmpty())
    }

    /**
     * Archives or unarchives a note.
     *
     * @param appFunctionContext System invocation context.
     * @param noteId Unique note ID.
     * @param archived True to archive, false to unarchive.
     * @return Action status.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun setNoteArchived(
        appFunctionContext: AppFunctionContext,
        noteId: String,
        archived: Boolean
    ): AppFunctionActionStatus = runGuarded {
        val res = operationsApi.setNoteArchived(noteId, archived)
        AppFunctionActionStatus(res.success, res.message, res.noteId.orEmpty())
    }

    /**
     * Pins or unpins a note.
     *
     * @param appFunctionContext System invocation context.
     * @param noteId Unique note ID.
     * @param pinned True to pin, false to unpin.
     * @return Action status.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun setNotePinned(
        appFunctionContext: AppFunctionContext,
        noteId: String,
        pinned: Boolean
    ): AppFunctionActionStatus = runGuarded {
        val res = operationsApi.setNotePinned(noteId, pinned)
        AppFunctionActionStatus(res.success, res.message, res.noteId.orEmpty())
    }

    /**
     * Retrieves lightweight metadata for a note.
     *
     * @param appFunctionContext System invocation context.
     * @param noteId Unique note ID.
     * @return Note metadata summary.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getNoteMetadata(
        appFunctionContext: AppFunctionContext,
        noteId: String
    ): AppFunctionNoteSummary = runGuarded {
        operationsApi.getNoteMetadata(noteId)?.toAppFunctionSummary()
            ?: throw SmartNoteOperationException("NOT_FOUND", "Note not found: $noteId")
    }

    private suspend inline fun <T> runGuarded(crossinline block: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            ensureActive()
            try {
                block()
            } catch (ex: SmartNoteOperationException) {
                throw when (ex.code) {
                    "INTEGRATION_DISABLED" -> AppFunctionDisabledException(ex.message)
                    "NOT_FOUND" -> AppFunctionElementNotFoundException(ex.message)
                    "INVALID_ARGUMENT", "CONFIRMATION_REQUIRED", "NOTE_IN_TRASH" ->
                        AppFunctionInvalidArgumentException(ex.message)
                    else -> AppFunctionAppUnknownException(ex.message)
                }
            }
        }
    }

    private fun AgentNoteMetadataDto.toAppFunctionSummary() = AppFunctionNoteSummary(
        id = id,
        title = title,
        preview = preview,
        tags = tags,
        isPinned = isPinned,
        isArchived = isArchived,
        hasChecklist = hasChecklist,
        reminderIso = reminderIso.orEmpty(),
        updatedAtMs = updatedAtMs
    )

    private fun AgentNoteDetailDto.toAppFunctionDetail() = AppFunctionNoteDetail(
        id = id,
        title = title,
        content = content,
        checklistItems = checklistItems,
        tags = tags,
        urls = urls,
        ocrSummary = ocrSummary,
        isPinned = isPinned,
        isArchived = isArchived,
        reminderIso = reminderIso.orEmpty(),
        updatedAtMs = updatedAtMs
    )
}
