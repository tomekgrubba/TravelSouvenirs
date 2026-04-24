package com.travelsouvenirs.main.ui.shared

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KEY_CATEGORIES = "categories"

/**
 * Shared ViewModel that holds category filter state used by both the List and Map screens.
 * Scoped to MainScreen so both tabs observe the same selection.
 */
class CategoryFilterViewModel(private val settings: Settings) : ViewModel() {

    private val _availableCategories = MutableStateFlow(loadCategories())
    /** All selectable categories: Default + any custom ones from Settings. */
    val availableCategories: StateFlow<List<String>> = _availableCategories.asStateFlow()

    /** Snapshot set of all known categories, updated on refresh. */
    val allCategoriesSet: Set<String> get() = _availableCategories.value.toSet()

    private val _selectedCategories = MutableStateFlow<Set<String>>(allCategoriesSet)
    /** Categories currently visible; all selected by default. */
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    /** Toggles a category in or out of the active filter set. */
    fun toggleCategoryFilter(category: String) {
        val updated = _selectedCategories.value.toMutableSet()
        if (category in updated) updated.remove(category) else updated.add(category)
        _selectedCategories.value = updated
    }

    /**
     * Re-reads categories from [Settings] and updates the available list.
     * New categories are automatically added to the selected set so they appear immediately.
     */
    fun refreshCategories() {
        val fresh = loadCategories()
        _availableCategories.value = fresh
        // Add any new categories to the selected set so they aren't hidden by default.
        val currentSelection = _selectedCategories.value.toMutableSet()
        currentSelection.addAll(fresh)
        _selectedCategories.value = currentSelection
    }

    private fun loadCategories(): List<String> = buildList {
        add(DEFAULT_CATEGORY)
        val raw = settings.getStringOrNull(KEY_CATEGORIES) ?: ""
        addAll(raw.split(",").filter { it.isNotBlank() })
    }
}
