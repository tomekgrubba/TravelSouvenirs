package com.travelsouvenirs.main.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.platform.MapProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val KEY_NOTES = "notes"
private const val KEY_CATEGORIES = "categories"
private const val MAX_CUSTOM_CATEGORIES = 5

/** Persists notes and custom categories; reassigns item categories on deletion. */
class SettingsViewModel(
    private val settings: Settings,
    private val repository: MagnetRepository
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

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        if (trimmed.equals(DEFAULT_CATEGORY, ignoreCase = true)) return
        val current = _customCategories.value
        if (current.size >= MAX_CUSTOM_CATEGORIES) return
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return
        val updated = current + trimmed
        _customCategories.value = updated
        persistCategories(updated)
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
