package io.github.ultrapg.smartnote.ui.editor

import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class PendingNoteDraft(
    val noteId: String,
    val title: String,
    val blocks: List<NoteBlock>,
    val isPinned: Boolean,
    val revisionToken: Long
)

enum class AutosaveStatus {
    IDLE,
    PENDING,
    SAVING,
    SAVED,
    ERROR
}

class NoteAutosaveCoordinator(
    private val noteRepository: NoteRepository,
    private val applicationScope: CoroutineScope,
    private val debounceMs: Long = 400L
) {
    private val pendingDrafts = ConcurrentHashMap<String, PendingNoteDraft>()
    private val debounceJobs = ConcurrentHashMap<String, Job>()
    private val saveMutex = Mutex()

    private val _statusFlow = MutableStateFlow(AutosaveStatus.IDLE)
    val statusFlow: StateFlow<AutosaveStatus> = _statusFlow.asStateFlow()

    fun scheduleSave(draft: PendingNoteDraft) {
        pendingDrafts[draft.noteId] = draft
        _statusFlow.value = AutosaveStatus.PENDING
        debounceJobs.remove(draft.noteId)?.cancel()
        val job = applicationScope.launch {
            delay(debounceMs)
            persistLatest(draft.noteId)
        }
        debounceJobs[draft.noteId] = job
    }

    fun flushAsync(noteId: String) {
        debounceJobs.remove(noteId)?.cancel()
        if (!pendingDrafts.containsKey(noteId)) return
        applicationScope.launch {
            withContext(NonCancellable) {
                persistLatest(noteId)
            }
        }
    }

    suspend fun flushNow(noteId: String) {
        debounceJobs.remove(noteId)?.cancel()
        withContext(NonCancellable) {
            persistLatest(noteId)
        }
    }

    fun discardPending(noteId: String) {
        debounceJobs.remove(noteId)?.cancel()
        pendingDrafts.remove(noteId)
    }

    private suspend fun persistLatest(noteId: String) {
        saveMutex.withLock {
            val draft = pendingDrafts[noteId] ?: return
            _statusFlow.value = AutosaveStatus.SAVING
            runCatching {
                noteRepository.saveNoteContent(
                    noteId = draft.noteId,
                    title = draft.title,
                    blocks = draft.blocks,
                    isPinned = draft.isPinned,
                    scheduleBackgroundWork = true
                )
            }.onSuccess {
                // Only clear if no newer edit arrived while saving
                val latest = pendingDrafts[noteId]
                if (latest == null || latest.revisionToken == draft.revisionToken) {
                    pendingDrafts.remove(noteId)
                    _statusFlow.value = AutosaveStatus.SAVED
                }
            }.onFailure {
                _statusFlow.value = AutosaveStatus.ERROR
            }
        }
    }
}
