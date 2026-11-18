package io.github.ultrapg.smartnote.ui.overview

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.NoteRepository
import io.github.ultrapg.smartnote.domain.model.NoteFilterType
import io.github.ultrapg.smartnote.domain.model.NoteSortOrder
import io.github.ultrapg.smartnote.domain.model.NoteSummary
import io.github.ultrapg.smartnote.domain.model.NotesLayoutMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class NotesOverviewUiState(
    val pinnedNotes: List<NoteSummary> = emptyList(),
    val otherNotes: List<NoteSummary> = emptyList(),
    val layoutMode: NotesLayoutMode = NotesLayoutMode.STAGGERED_CARDS,
    val sortOrder: NoteSortOrder = NoteSortOrder.UPDATED_DESC,
    val filterType: NoteFilterType = NoteFilterType.ALL,
    val selectedIds: Set<String> = emptySet(),
    val activeTagName: String? = null,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesOverviewViewModel(
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val tagIdFilter: String? = null,
    private val tagNameFilter: String? = null
) : ViewModel() {

    private val sortOrderFlow = MutableStateFlow(NoteSortOrder.UPDATED_DESC)
    private val filterTypeFlow = MutableStateFlow(NoteFilterType.ALL)
    private val selectedIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    private val notesFlow = combine(sortOrderFlow, filterTypeFlow) { sort, filter ->
        sort to filter
    }.flatMapLatest { (sort, filter) ->
        noteRepository.observeActiveNotes(
            sortOrder = sort,
            filterType = filter,
            tagId = tagIdFilter
        )
    }

    val uiState: StateFlow<NotesOverviewUiState> = combine(
        notesFlow,
        settingsRepository.settingsFlow,
        sortOrderFlow,
        filterTypeFlow,
        selectedIdsFlow
    ) { notes, settings, sort, filter, selected ->
        val pinned = notes.filter { it.isPinned }
        val others = notes.filterNot { it.isPinned }
        NotesOverviewUiState(
            pinnedNotes = pinned,
            otherNotes = others,
            layoutMode = settings.notesLayout,
            sortOrder = sort,
            filterType = filter,
            selectedIds = selected.intersect(notes.map { it.id }.toSet()),
            activeTagName = tagNameFilter,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = NotesOverviewUiState(activeTagName = tagNameFilter)
    )

    fun setSortOrder(order: NoteSortOrder) {
        sortOrderFlow.value = order
    }

    fun setFilterType(type: NoteFilterType) {
        filterTypeFlow.value = type
    }

    fun toggleLayoutMode() {
        val current = uiState.value.layoutMode
        val next = if (current == NotesLayoutMode.STAGGERED_CARDS) {
            NotesLayoutMode.COMPACT_LIST
        } else {
            NotesLayoutMode.STAGGERED_CARDS
        }
        viewModelScope.launch {
            settingsRepository.setNotesLayout(next)
        }
    }

    fun toggleSelection(noteId: String) {
        val current = selectedIdsFlow.value
        selectedIdsFlow.value = if (noteId in current) current - noteId else current + noteId
    }

    fun clearSelection() {
        selectedIdsFlow.value = emptySet()
    }

    fun togglePin(noteId: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            noteRepository.setPinned(noteId, !currentlyPinned)
        }
    }

    fun archiveNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.setArchived(noteId, true)
        }
    }

    fun unarchiveNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.setArchived(noteId, false)
        }
    }

    fun moveToRecycleBin(noteId: String) {
        viewModelScope.launch {
            noteRepository.moveToRecycleBin(noteId)
        }
    }

    fun restoreFromRecycleBin(noteId: String) {
        viewModelScope.launch {
            noteRepository.restoreFromRecycleBin(noteId)
        }
    }

    fun batchSetPinned(pinned: Boolean) {
        val ids = selectedIdsFlow.value.toList()
        clearSelection()
        viewModelScope.launch {
            ids.forEach { noteRepository.setPinned(it, pinned) }
        }
    }

    fun batchArchive() {
        val ids = selectedIdsFlow.value.toList()
        clearSelection()
        viewModelScope.launch {
            ids.forEach { noteRepository.setArchived(it, true) }
        }
    }

    fun batchMoveToRecycleBin() {
        val ids = selectedIdsFlow.value.toList()
        clearSelection()
        viewModelScope.launch {
            ids.forEach { noteRepository.moveToRecycleBin(it) }
        }
    }

    class Factory(
        private val noteRepository: NoteRepository,
        private val settingsRepository: SettingsRepository,
        private val tagIdFilter: String? = null,
        private val tagNameFilter: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NotesOverviewViewModel(noteRepository, settingsRepository, tagIdFilter, tagNameFilter) as T
        }
    }
}
