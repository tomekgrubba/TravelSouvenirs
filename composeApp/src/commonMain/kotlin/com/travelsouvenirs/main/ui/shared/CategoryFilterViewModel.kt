package com.travelsouvenirs.main.ui.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared ViewModel that holds category filter state used by both the List and Map screens.
 * Scoped to MainScreen so both tabs observe the same selection.
 * Only categories that have at least one item assigned are shown in the filter.
 */
class CategoryFilterViewModel(
    private val settings: Settings,
    private val repository: ItemRepository,
) : ViewModel() {

    /** Categories derived from actual items — only non-empty categories are included. */
    val availableCategories: StateFlow<List<String>> = repository.allItems
        .map { items ->
            items.map { it.category }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedWith(compareBy { if (it == DEFAULT_CATEGORY) "" else it.lowercase() })
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Snapshot set of all known categories. */
    val allCategoriesSet: Set<String> get() = availableCategories.value.toSet()

    private val _selectedCategories = MutableStateFlow<Set<String>>(emptySet())
    /** Categories currently visible; all selected by default. */
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    init {
        var previousAvail = emptySet<String>()
        viewModelScope.launch {
            availableCategories.collect { available ->
                val availSet = available.toSet()
                val newCategories = availSet - previousAvail
                _selectedCategories.value = (_selectedCategories.value intersect availSet) union newCategories
                previousAvail = availSet
            }
        }
    }

    /** Toggles a category in or out of the active filter set. */
    fun toggleCategoryFilter(category: String) {
        val updated = _selectedCategories.value.toMutableSet()
        if (category in updated) updated.remove(category) else updated.add(category)
        _selectedCategories.value = updated
    }

    /** No-op: categories are now derived reactively from items. */
    fun refreshCategories() {}
}
