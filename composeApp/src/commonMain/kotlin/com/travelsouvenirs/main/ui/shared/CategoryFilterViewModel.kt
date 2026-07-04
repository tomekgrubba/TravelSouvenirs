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

    private val _selectedCategory = MutableStateFlow<String?>(null) // null means "All"
    /** The currently selected category, or null if "All" is selected. */
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    /** Set of selected categories. Keeps compatibility with map rendering and filter checks. */
    val selectedCategories: StateFlow<Set<String>> = combine(
        _selectedCategory,
        availableCategories
    ) { selected, available ->
        if (selected == null) {
            available.toSet()
        } else {
            setOf(selected)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var isExplicitSelection = false

    init {
        viewModelScope.launch {
            availableCategories.collect { available ->
                val current = _selectedCategory.value
                if (available.size == 1) {
                    _selectedCategory.value = available.first()
                } else if (available.size > 1 && !isExplicitSelection) {
                    _selectedCategory.value = null
                } else if (current != null && current !in available) {
                    _selectedCategory.value = null
                    isExplicitSelection = false
                }
            }
        }
    }

    /** Selects a category. Pass null to select "All". */
    fun selectCategory(category: String?) {
        _selectedCategory.value = category
        isExplicitSelection = category != null
    }

    /** Toggles category filter (kept for basic compatibility, behaves as selectCategory/deselect). */
    fun toggleCategoryFilter(category: String) {
        if (_selectedCategory.value == category) {
            selectCategory(null)
        } else {
            selectCategory(category)
        }
    }

    /** No-op: categories are now derived reactively from items. */
    fun refreshCategories() {}

    /** Returns items whose category is selected or not in the known category set. */
    fun filterItems(items: List<Item>): List<Item> {
        val selected = _selectedCategory.value
        if (selected == null) return items
        return items.filter { it.category == selected }
    }
}
