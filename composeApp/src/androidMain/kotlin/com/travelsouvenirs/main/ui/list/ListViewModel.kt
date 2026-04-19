package com.travelsouvenirs.main.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.Magnet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ListViewModel(repository: MagnetRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredMagnets: StateFlow<List<Magnet>> = combine(
        repository.allMagnets,
        _searchQuery
    ) { magnets, query ->
        if (query.isBlank()) magnets
        else magnets.filter { m ->
            m.name.contains(query, ignoreCase = true) ||
                m.placeName.contains(query, ignoreCase = true) ||
                m.notes.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(q: String) { _searchQuery.value = q }

    class Factory(private val repo: MagnetRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ListViewModel(repo) as T
    }
}
