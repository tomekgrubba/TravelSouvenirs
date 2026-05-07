package com.travelsouvenirs.main.domain.usecase

import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.domain.SortOption

object FilterItemsUseCase {
    operator fun invoke(
        items: List<Item>,
        selectedCategories: Set<String>,
        allCategoriesSet: Set<String>,
        searchQuery: String,
        sortOption: SortOption,
    ): List<Item> {
        val categoryFiltered = items.filter { m ->
            m.category in selectedCategories || m.category !in allCategoriesSet
        }
        val searchFiltered = if (searchQuery.isBlank()) categoryFiltered
        else categoryFiltered.filter { m ->
            m.name.contains(searchQuery, ignoreCase = true) ||
                m.placeName.contains(searchQuery, ignoreCase = true) ||
                m.notes.contains(searchQuery, ignoreCase = true)
        }
        return when (sortOption) {
            SortOption.NAME -> searchFiltered.sortedBy { it.name.lowercase() }
            SortOption.DATE -> searchFiltered.sortedByDescending { it.dateAcquired }
            SortOption.LOCATION -> searchFiltered.sortedBy { it.placeName.lowercase() }
        }
    }
}
