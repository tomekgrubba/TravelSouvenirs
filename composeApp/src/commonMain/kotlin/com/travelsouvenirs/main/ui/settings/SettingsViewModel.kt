package com.travelsouvenirs.main.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.domain.MAX_CUSTOM_CATEGORIES
import com.travelsouvenirs.main.platform.MapProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val KEY_NOTES = "notes"
private const val KEY_CATEGORIES = "categories"

/** Persists notes and custom categories; reassigns item categories on deletion. */
class SettingsViewModel(
    private val settings: Settings,
    private val repository: ItemRepository
) : ViewModel() {

    private val _mapProvider = MutableStateFlow(
        MapProviderType.fromString(settings.getStringOrNull(MapProviderType.SETTINGS_KEY))
    )
    val mapProvider: StateFlow<MapProviderType> = _mapProvider.asStateFlow()

    fun setMapProvider(provider: MapProviderType) {
        _mapProvider.value = provider
        settings.putString(MapProviderType.SETTINGS_KEY, provider.name)
    }

    private val _notes = MutableStateFlow(settings.getStringOrNull(KEY_NOTES) ?: "")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _customCategories = MutableStateFlow(loadCategories())
    val customCategories: StateFlow<List<String>> = _customCategories.asStateFlow()

    val canAddCategory: Boolean get() = _customCategories.value.size < MAX_CUSTOM_CATEGORIES

    /** Re-reads categories from persistent storage; call whenever the settings panel becomes visible. */
    fun refreshCategories() {
        _customCategories.value = loadCategories()
    }

    private fun loadCategories(): List<String> {
        val raw = settings.getStringOrNull(KEY_CATEGORIES) ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    private fun persistCategories(list: List<String>) {
        settings.putString(KEY_CATEGORIES, list.joinToString(","))
    }

    fun onNotesChange(value: String) {
        _notes.value = value
        settings.putString(KEY_NOTES, value)
    }

    /** Returns true if the category was added, false if a category with that name already exists. */
    fun addCategory(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val current = _customCategories.value
        if (current.size >= MAX_CUSTOM_CATEGORIES) return false
        val isDuplicate = trimmed.equals(DEFAULT_CATEGORY, ignoreCase = true) ||
                current.any { it.equals(trimmed, ignoreCase = true) }
        if (isDuplicate) return false
        val updated = current + trimmed
        _customCategories.value = updated
        persistCategories(updated)
        return true
    }

    /**
     * Removes [name] from the category list and moves all items using it to [DEFAULT_CATEGORY].
     * The settings update happens immediately; the DB reassignment runs in the background.
     */
    fun deleteCategory(name: String) {
        val updated = _customCategories.value.filter { it != name }
        _customCategories.value = updated
        persistCategories(updated)
        viewModelScope.launch {
            repository.reassignCategory(fromCategory = name, toCategory = DEFAULT_CATEGORY)
        }
    }
}
