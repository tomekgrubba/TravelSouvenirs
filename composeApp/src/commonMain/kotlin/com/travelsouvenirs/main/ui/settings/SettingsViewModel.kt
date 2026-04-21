package com.travelsouvenirs.main.ui.settings

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KEY_NOTES = "notes"

/** Persists a free-form notes string via the multiplatform-settings [Settings] interface. */
class SettingsViewModel(private val settings: Settings) : ViewModel() {

    private val _notes = MutableStateFlow(settings.getStringOrNull(KEY_NOTES) ?: "")
    /** Current notes text. */
    val notes: StateFlow<String> = _notes.asStateFlow()

    /** Updates the notes text and persists it immediately. */
    fun onNotesChange(value: String) {
        _notes.value = value
        settings.putString(KEY_NOTES, value)
    }
}
