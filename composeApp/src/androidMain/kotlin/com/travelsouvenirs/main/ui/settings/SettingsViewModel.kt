package com.travelsouvenirs.main.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "settings"
private const val KEY_NOTES = "notes"

/** Persists a free-form notes string in SharedPreferences. */
class SettingsViewModel(context: Context) : ViewModel() {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _notes = MutableStateFlow(prefs.getString(KEY_NOTES, "") ?: "")
    /** Current notes text. */
    val notes: StateFlow<String> = _notes.asStateFlow()

    /** Updates the notes text and persists it immediately. */
    fun onNotesChange(value: String) {
        _notes.value = value
        prefs.edit().putString(KEY_NOTES, value).apply()
    }

    /** Factory that passes [Context] to [SettingsViewModel]. */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(context) as T
    }
}
