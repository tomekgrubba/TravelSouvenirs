package com.travelsouvenirs.main.ui.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.CategoryRepository
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.domain.Item
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared ViewModel that holds category filter state used by both the List and Map screens.
 * Scoped to MainScreen so both tabs observe the same selection.
 * Only categories assigned to at least one active item are shown.
 */
class CategoryFilterViewModel(
    private val categoryRepository: CategoryRepository,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    /** Map of category name to the count of items in it. Only active items are counted. */
    val categoryCounts: StateFlow<Map<String, Int>> = itemRepository.allItems.map { items ->
        items.groupBy { it.category }.mapValues { it.value.size }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Categories that have at least one item — DEFAULT_CATEGORY first, then custom alphabetically. */
    val availableCategories: StateFlow<List<String>> = combine(
        itemRepository.allItems,
        categoryRepository.categories,
    ) { items, customCategories ->
        val usedInItems = items.map { it.category }.toSet()
        (listOf(DEFAULT_CATEGORY) + customCategories).filter { it in usedInItems }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
