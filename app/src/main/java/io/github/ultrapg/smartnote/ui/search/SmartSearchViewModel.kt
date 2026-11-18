package io.github.ultrapg.smartnote.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.ultrapg.smartnote.data.preferences.SettingsRepository
import io.github.ultrapg.smartnote.data.repository.TagRepository
import io.github.ultrapg.smartnote.domain.model.TagInfo
import io.github.ultrapg.smartnote.search.SearchDateFilter
import io.github.ultrapg.smartnote.search.SearchFilterSpec
import io.github.ultrapg.smartnote.search.SmartSearchEngine
import io.github.ultrapg.smartnote.search.SmartSearchResultItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SmartSearchUiState(
    val query: String = "",
    val filter: SearchFilterSpec = SearchFilterSpec(),
    val results: List<SmartSearchResultItem> = emptyList(),
    val didYouMeanSuggestion: String? = null,
    val recentSearches: List<String> = emptyList(),
    val recentSearchesEnabled: Boolean = true,
    val availableTags: List<TagInfo> = emptyList(),
    val durationMs: Long = 0L,
    val isSearching: Boolean = false
)

@OptIn(FlowPreview::class)
class SmartSearchViewModel(
    private val searchEngine: SmartSearchEngine,
    private val tagRepository: TagRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(SearchFilterSpec())
    private val searchResultState = MutableStateFlow(SearchOutcome())

    private data class SearchOutcome(
        val results: List<SmartSearchResultItem> = emptyList(),
        val didYouMean: String? = null,
        val durationMs: Long = 0L,
        val isSearching: Boolean = false
    )

    val uiState: StateFlow<SmartSearchUiState> = combine(
        queryFlow,
        filterFlow,
        searchResultState,
        searchEngine.observeRecentSearches(),
        tagRepository.observeAllTags(),
        settingsRepository.settingsFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val query = values[0] as String
        val filter = values[1] as SearchFilterSpec
        val outcome = values[2] as SearchOutcome
        @Suppress("UNCHECKED_CAST")
        val recent = values[3] as List<String>
        @Suppress("UNCHECKED_CAST")
        val tags = values[4] as List<TagInfo>
        val settings = values[5] as io.github.ultrapg.smartnote.domain.model.AppSettings

        SmartSearchUiState(
            query = query,
            filter = filter,
            results = outcome.results,
            didYouMeanSuggestion = outcome.didYouMean,
            recentSearches = if (settings.recentSearchesEnabled) recent else emptyList(),
            recentSearchesEnabled = settings.recentSearchesEnabled,
            availableTags = tags,
            durationMs = outcome.durationMs,
            isSearching = outcome.isSearching
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SmartSearchUiState()
    )

    init {
        viewModelScope.launch {
            combine(
                queryFlow.debounce(90L),
                filterFlow,
                settingsRepository.settingsFlow.map { it.effectiveSemanticRerankEnabled }.distinctUntilChanged()
            ) { q, f, semantic -> Triple(q, f, semantic) }
                .collect { (query, filter, semantic) ->
                    searchResultState.update { it.copy(isSearching = true) }
                    val response = searchEngine.search(
                        rawQuery = query,
                        filter = filter,
                        enableSemanticRerank = semantic
                    )
                    searchResultState.value = SearchOutcome(
                        results = response.results,
                        didYouMean = response.didYouMeanSuggestion,
                        durationMs = response.durationMs,
                        isSearching = false
                    )
                }
        }
    }

    fun updateQuery(newQuery: String) {
        queryFlow.value = newQuery
    }

    fun submitSearch() {
        val clean = queryFlow.value.trim()
        if (clean.length < 2) return
        viewModelScope.launch {
            if (uiState.value.recentSearchesEnabled) {
                searchEngine.recordRecentSearch(clean)
            }
        }
    }

    fun applyRecentSearch(query: String) {
        queryFlow.value = query
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            searchEngine.deleteRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            searchEngine.clearRecentSearches()
        }
    }

    fun applyDidYouMean(suggestion: String) {
        queryFlow.value = suggestion
    }

    fun toggleTagFilter(tagName: String) {
        filterFlow.update { cur ->
            val next = if (tagName in cur.tagNames) cur.tagNames - tagName else cur.tagNames + tagName
            cur.copy(tagNames = next)
        }
    }

    fun togglePinnedFilter() {
        filterFlow.update { it.copy(pinnedOnly = !it.pinnedOnly) }
    }

    fun toggleImageFilter() {
        filterFlow.update { it.copy(hasImageOnly = !it.hasImageOnly) }
    }

    fun toggleOcrFilter() {
        filterFlow.update { it.copy(hasOcrOnly = !it.hasOcrOnly) }
    }

    fun toggleChecklistFilter() {
        filterFlow.update { it.copy(hasChecklistOnly = !it.hasChecklistOnly) }
    }

    fun toggleLinkFilter() {
        filterFlow.update { it.copy(hasLinkOnly = !it.hasLinkOnly) }
    }

    fun setDateFilter(dateFilter: SearchDateFilter) {
        filterFlow.update { it.copy(dateFilter = dateFilter) }
    }

    class Factory(
        private val searchEngine: SmartSearchEngine,
        private val tagRepository: TagRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SmartSearchViewModel(searchEngine, tagRepository, settingsRepository) as T
        }
    }
}
