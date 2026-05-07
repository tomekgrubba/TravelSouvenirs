package com.travelsouvenirs.main.ui.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.CategoryRepository
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.domain.Item
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
 * Category list comes from the Room categories table, not from items.
 */
class CategoryFilterViewModel(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    /** Categories from the Room table — DEFAULT_CATEGORY prepended, then custom in alphabetical order. */
    val availableCategories: StateFlow<List<String>> = categoryRepository.categories
        .map { custom -> listOf(DEFAULT_CATEGORY) + custom }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(DEFAULT_CATEGORY))

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

    /** Returns items whose category is selected or not in the known category set. */
    fun filterItems(items: List<Item>): List<Item> =
        items.filter { m ->
            m.category in selectedCategories.value || m.category !in allCategoriesSet
        }
}
