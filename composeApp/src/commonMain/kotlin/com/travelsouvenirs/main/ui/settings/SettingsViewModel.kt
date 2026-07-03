package com.travelsouvenirs.main.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.data.CategoryRepository
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.domain.MAX_CUSTOM_CATEGORIES
import com.travelsouvenirs.main.image.ImageLocationAnalyzer
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.sync.SyncCoordinator
import com.travelsouvenirs.main.util.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val wifiOnlySync: Boolean = false,
    val notes: String = "",
    val modelDownloadable: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val showSignOutWarning: Boolean = false,
)

/** Persists appearance and sync settings; delegates category management to CategoryRepository. */
class SettingsViewModel(
    private val appSettings: AppSettings,
    private val repository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val imageStorage: ImageStorage,
    private val imageLocationAnalyzer: ImageLocationAnalyzer,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            wifiOnlySync = appSettings.wifiOnlySync,
            notes = appSettings.notes,
        )
    )

    init {
        viewModelScope.launch {
            val downloadable = imageLocationAnalyzer.isDownloadable()
            _uiState.update { it.copy(modelDownloadable = downloadable) }
        }
    }
    val uiState: StateFlow<SettingsUiState> = _uiState

    // Eagerly (not WhileSubscribed) so unit tests can read .value without an active collector.
    val wifiOnlySync: StateFlow<Boolean> = _uiState.map { it.wifiOnlySync }
        .stateIn(viewModelScope, SharingStarted.Eagerly, appSettings.wifiOnlySync)
    val notes: StateFlow<String> = _uiState.map { it.notes }
        .stateIn(viewModelScope, SharingStarted.Eagerly, appSettings.notes)

    val customCategories: StateFlow<List<String>> = categoryRepository.categories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val canAddCategory: Boolean get() = customCategories.value.size < MAX_CUSTOM_CATEGORIES

    fun setWifiOnlySync(enabled: Boolean) {
        _uiState.update { it.copy(wifiOnlySync = enabled) }
        appSettings.wifiOnlySync = enabled
    }

    fun onNotesChange(value: String) {
        _uiState.update { it.copy(notes = value) }
        appSettings.notes = value
    }

    /** Returns true if the category was added, false if validation failed. */
    fun addCategory(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val current = customCategories.value
        if (current.size >= MAX_CUSTOM_CATEGORIES) return false
        val isDuplicate = trimmed.equals(DEFAULT_CATEGORY, ignoreCase = true) ||
                current.any { it.equals(trimmed, ignoreCase = true) }
        if (isDuplicate) return false
        viewModelScope.launch { categoryRepository.add(trimmed) }
        return true
    }

    /** Returns true if the category was renamed, false if validation failed. */
    fun renameCategory(oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return false
        val current = customCategories.value
        val isDuplicate = trimmed.equals(DEFAULT_CATEGORY, ignoreCase = true) ||
                current.any { it.equals(trimmed, ignoreCase = true) }
        if (isDuplicate && !trimmed.equals(oldName, ignoreCase = true)) return false

        viewModelScope.launch {
            categoryRepository.rename(oldName, trimmed)
        }
        return true
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            categoryRepository.delete(name)
            repository.reassignCategory(fromCategory = name, toCategory = DEFAULT_CATEGORY)
        }
    }

    fun downloadAiModel() {
        _uiState.update { it.copy(isDownloadingModel = true) }
        viewModelScope.launch {
            imageLocationAnalyzer.download()
            val stillDownloadable = imageLocationAnalyzer.isDownloadable()
            _uiState.update { it.copy(isDownloadingModel = false, modelDownloadable = stillDownloadable) }
        }
    }

    fun signOut(force: Boolean = false) {
        viewModelScope.launch {
            if (!force && repository.hasPendingUploads()) {
                _uiState.update { it.copy(showSignOutWarning = true) }
            } else {
                _uiState.update { it.copy(showSignOutWarning = false) }
                syncCoordinator.cancelAllSyncs()
                repository.deleteAll()
                categoryRepository.setAll(emptyList())
                imageStorage.deleteAllImages()
                appSettings.clear()
                authRepository.signOut()
            }
        }
    }

    fun dismissSignOutWarning() {
        _uiState.update { it.copy(showSignOutWarning = false) }
    }
}
