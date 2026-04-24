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
class CategoryFilterViewModel(settings: Settings) : ViewModel() {

    /** All selectable categories: Default + any custom ones from Settings. */
    val availableCategories: List<String> = buildList {
        add(DEFAULT_CATEGORY)
        val raw = settings.getStringOrNull(KEY_CATEGORIES) ?: ""
        addAll(raw.split(",").filter { it.isNotBlank() })
    }

    val allCategoriesSet: Set<String> = availableCategories.toSet()

    private val _selectedCategories = MutableStateFlow(allCategoriesSet)
    /** Categories currently visible; all selected by default. */
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    /** Toggles a category in or out of the active filter set. */
    fun toggleCategoryFilter(category: String) {
        val updated = _selectedCategories.value.toMutableSet()
        if (category in updated) updated.remove(category) else updated.add(category)
        _selectedCategories.value = updated
    }
}
