package com.travelsouvenirs.main.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.domain.SortOption
import com.travelsouvenirs.main.util.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ViewMode { LIST, GRID }

data class ListUiState(
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.NAME,
    val viewMode: ViewMode = ViewMode.LIST,
)

/** Drives the list screen — applies search query and sort order to the repository stream. */
class ListViewModel(private val appSettings: AppSettings, repository: ItemRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ListUiState(viewMode = runCatching { ViewMode.valueOf(appSettings.viewModeName) }.getOrDefault(ViewMode.LIST))
    )
    val uiState: StateFlow<ListUiState> = _uiState

    /** Items matching the search query, sorted by the current sort option. Category filtering is applied at the screen level using the shared CategoryFilterViewModel. */
    val sortedItems: StateFlow<List<Item>> = combine(
        repository.allItems,
        _uiState
    ) { items, state ->
        val filtered = if (state.searchQuery.isBlank()) items
        else items.filter { m ->
            m.name.contains(state.searchQuery, ignoreCase = true) ||
                m.placeName.contains(state.searchQuery, ignoreCase = true) ||
                m.notes.contains(state.searchQuery, ignoreCase = true)
        }
        when (state.sortOption) {
            SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            SortOption.DATE -> filtered.sortedByDescending { it.dateAcquired }
            SortOption.LOCATION -> filtered.sortedBy { it.placeName.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(q: String) { _uiState.update { it.copy(searchQuery = q) } }
    fun onSortChange(option: SortOption) { _uiState.update { it.copy(sortOption = option) } }
    fun onViewModeChange(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
        appSettings.viewModeName = mode.name
    }
}
