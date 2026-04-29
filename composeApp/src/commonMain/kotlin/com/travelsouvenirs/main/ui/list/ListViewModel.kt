package com.travelsouvenirs.main.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.Item
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SortOption { NAME, DATE, LOCATION }
enum class ViewMode { LIST, GRID }

private const val KEY_VIEW_MODE = "list_view_mode"

/** Drives the list screen — applies search query and sort order to the repository stream. */
class ListViewModel(private val settings: Settings, repository: ItemRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NAME)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _viewMode = MutableStateFlow(
        runCatching { ViewMode.valueOf(settings.getString(KEY_VIEW_MODE, ViewMode.LIST.name)) }
            .getOrDefault(ViewMode.LIST)
    )
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    /** Items matching the search query, sorted by the current sort option. Category filtering is applied at the screen level using the shared CategoryFilterViewModel. */
    val sortedItems: StateFlow<List<Item>> = combine(
        repository.allItems,
        _searchQuery,
        _sortOption
    ) { items, query, sort ->
        val filtered = if (query.isBlank()) items
        else items.filter { m ->
            m.name.contains(query, ignoreCase = true) ||
                m.placeName.contains(query, ignoreCase = true) ||
                m.notes.contains(query, ignoreCase = true)
        }
        when (sort) {
            SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            SortOption.DATE -> filtered.sortedByDescending { it.dateAcquired }
            SortOption.LOCATION -> filtered.sortedBy { it.placeName.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(q: String) { _searchQuery.value = q }
    fun onSortChange(option: SortOption) { _sortOption.value = option }
    fun onViewModeChange(mode: ViewMode) {
        _viewMode.value = mode
        settings.putString(KEY_VIEW_MODE, mode.name)
    }
}
