package com.travelsouvenirs.main.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.Magnet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Drives the list screen; combines live data with a search query to produce a filtered, sorted list. */
class ListViewModel(repository: MagnetRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    /** Current search query entered by the user. */
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Items filtered by [searchQuery] and sorted alphabetically by name. */
    val filteredMagnets: StateFlow<List<Magnet>> = combine(
        repository.allMagnets,
        _searchQuery
    ) { magnets, query ->
        val filtered = if (query.isBlank()) magnets
        else magnets.filter { m ->
            m.name.contains(query, ignoreCase = true) ||
                m.placeName.contains(query, ignoreCase = true) ||
                m.notes.contains(query, ignoreCase = true)
        }
        filtered.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Updates the search query, triggering a new filtered emission. */
    fun onQueryChange(q: String) { _searchQuery.value = q }
}
