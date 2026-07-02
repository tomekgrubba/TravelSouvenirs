package com.travelsouvenirs.main.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.CategoryRepository
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.domain.MAX_CUSTOM_CATEGORIES
import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.domain.usecase.SaveItemUseCase
import com.travelsouvenirs.main.image.ImageLocationAnalyzer
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.location.LocationService
import com.travelsouvenirs.main.location.PlaceResult
import com.travelsouvenirs.main.platform.todayLocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class AddItemUiState(
    val photoPath: String? = null,
    val name: String = "",
    val notes: String = "",
    val dateAcquired: LocalDate = todayLocalDate(),
    val category: String = DEFAULT_CATEGORY,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeName: String = "",
    val isSaved: Boolean = false,
    // Location picker dialog state
    val showLocationDialog: Boolean = false,
    val pendingLat: Double? = null,
    val pendingLng: Double? = null,
    val cameraMoveId: Int = 0,
    val searchQuery: String = "",
    val searchResults: List<PlaceResult> = emptyList(),
    val isSearching: Boolean = false,
    val isLocating: Boolean = false,
    val locationError: String? = null,
    // Category state
    val availableCategories: List<String> = listOf(DEFAULT_CATEGORY),
    // AI suggestion dialog
    val aiSuggestedPlace: String? = null,
    val pendingExifDate: LocalDate? = null,
)

/**
 * Drives both the Add and Edit screens.
 * When [editId] is non-null, pre-populates fields from the existing item and upserts on save.
 */
class AddItemViewModel(
    repository: ItemRepository,
    private val locationService: LocationService,
    imageStorage: ImageStorage,
    private val editId: Long? = null,
    private val categoryRepository: CategoryRepository,
    private val imageLocationAnalyzer: ImageLocationAnalyzer,
) : ViewModel() {

    private val saveItem = SaveItemUseCase(repository)
    private val imagePicker = ImagePickerHandler(imageStorage)

    private val _uiState = MutableStateFlow(AddItemUiState())
    val uiState: StateFlow<AddItemUiState> = _uiState

    val canAddCategory: Boolean get() =
        _uiState.value.availableCategories.count { it != DEFAULT_CATEGORY } < MAX_CUSTOM_CATEGORIES

    // True once the user has explicitly picked a date; prevents EXIF prefill from overwriting it.
    private var dateSetByUser = editId != null

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            categoryRepository.categories.collect { customCategories ->
                _uiState.update { state ->
                    val available = listOf(DEFAULT_CATEGORY) + customCategories
                    val selectedCategory = when {
                        state.category in available -> state.category
                        customCategories.size == 1 && editId == null -> customCategories[0]
                        else -> DEFAULT_CATEGORY
                    }
                    state.copy(availableCategories = available, category = selectedCategory)
                }
            }
        }
        if (editId != null) {
            viewModelScope.launch {
                repository.getItemById(editId)?.let { item ->
                    imagePicker.originalPhotoPath = item.photoPath
                    _uiState.update { state ->
                        state.copy(
                            photoPath = item.photoPath,
                            name = item.name,
                            notes = item.notes,
                            dateAcquired = item.dateAcquired,
                            placeName = item.placeName,
                            latitude = item.latitude,
                            longitude = item.longitude,
                            category = item.category,
                        )
                    }
                }
            }
        }
    }

    fun onPhotoSelected(sourcePath: String, exifLat: Double? = null, exifLng: Double? = null, exifDate: LocalDate? = null) {
        imagePicker.onPhotoSelected(sourcePath) { path ->
            _uiState.update { it.copy(photoPath = path) }
            if (path != null) {
                viewModelScope.launch {
                    val result = imageLocationAnalyzer.analyze(path, exifLat, exifLng)
                    if (result != null) {
                        _uiState.update { it.copy(aiSuggestedPlace = result, pendingExifDate = exifDate) }
                    } else {
                        if (exifLat != null && exifLng != null) prefillLocationFromExif(exifLat, exifLng)
                        exifDate?.let { prefillDateFromExif(it) }
                    }
                }
            }
        }
    }

    fun onAcceptAiSuggestion() {
        val state = _uiState.value
        val place = state.aiSuggestedPlace ?: return
        _uiState.update { it.copy(
            name = if (it.name.isBlank()) place.split(", ").first() else it.name,
            aiSuggestedPlace = null,
            pendingExifDate = null,
        ) }
        state.pendingExifDate?.let { prefillDateFromExif(it) }
        viewModelScope.launch {
            val results = runCatching { locationService.searchByName(place) }.getOrNull()
            val first = results?.firstOrNull()
            if (first != null) {
                val resolved = runCatching { locationService.reverseGeocode(first.latitude, first.longitude) }.getOrElse { first.name }
                _uiState.update { it.copy(latitude = first.latitude, longitude = first.longitude, placeName = resolved) }
            } else if (state.placeName.isBlank()) {
                _uiState.update { it.copy(placeName = place) }
            }
        }
    }

    fun onDismissAiSuggestion() {
        _uiState.update { it.copy(aiSuggestedPlace = null, pendingExifDate = null) }
    }

    fun prefillLocationFromExif(lat: Double, lng: Double) {
        val state = _uiState.value
        if (state.latitude != 0.0 || state.longitude != 0.0 || state.placeName.isNotBlank()) return
        viewModelScope.launch {
            runCatching {
                val resolved = locationService.reverseGeocode(lat, lng)
                _uiState.update { it.copy(latitude = lat, longitude = lng, placeName = resolved) }
            }
        }
    }

    fun onNameChange(value: String) { _uiState.update { it.copy(name = value) } }
    fun onNotesChange(value: String) { _uiState.update { it.copy(notes = value) } }
    fun onDateChange(date: LocalDate) {
        dateSetByUser = true
        _uiState.update { it.copy(dateAcquired = date) }
    }
    fun onCategoryChange(value: String) { _uiState.update { it.copy(category = value) } }

    fun prefillDateFromExif(date: LocalDate) {
        if (dateSetByUser) return
        _uiState.update { it.copy(dateAcquired = date) }
    }

    fun addCategoryOnTheFly(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.contains(',')) return false
        val custom = _uiState.value.availableCategories.filter { it != DEFAULT_CATEGORY }
        if (custom.size >= MAX_CUSTOM_CATEGORIES) return false
        val isDuplicate = trimmed.equals(DEFAULT_CATEGORY, ignoreCase = true) ||
            _uiState.value.availableCategories.any { it.equals(trimmed, ignoreCase = true) }
        if (isDuplicate) return false
        _uiState.update { it.copy(category = trimmed) }
        viewModelScope.launch { categoryRepository.add(trimmed) }
        return true
    }

    fun openLocationDialog() {
        val state = _uiState.value
        _uiState.update { it.copy(
            searchQuery = "",
            searchResults = emptyList(),
            locationError = null,
            showLocationDialog = true,
        ) }
        if (state.latitude != 0.0 && state.longitude != 0.0) {
            _uiState.update { it.copy(
                pendingLat = state.latitude,
                pendingLng = state.longitude,
                cameraMoveId = state.cameraMoveId + 1,
            ) }
        } else {
            _uiState.update { it.copy(pendingLat = null, pendingLng = null) }
            viewModelScope.launch {
                _uiState.update { it.copy(isLocating = true) }
                runCatching {
                    locationService.getCurrentLocation()?.let { loc ->
                        _uiState.update { it.copy(
                            pendingLat = loc.lat,
                            pendingLng = loc.lng,
                            cameraMoveId = it.cameraMoveId + 1,
                        ) }
                    }
                }
                _uiState.update { it.copy(isLocating = false) }
            }
        }
    }

    fun closeLocationDialog() {
        _uiState.update { it.copy(showLocationDialog = false) }
        searchJob?.cancel()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _uiState.update { it.copy(isSearching = true) }
            val results = locationService.searchByName(query)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun onPlaceSelected(place: PlaceResult) {
        _uiState.update { it.copy(
            pendingLat = place.latitude,
            pendingLng = place.longitude,
            searchQuery = "",
            searchResults = emptyList(),
            cameraMoveId = it.cameraMoveId + 1,
            name = if (it.name.isBlank()) place.name.split(", ").first() else it.name,
        ) }
    }

    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, locationError = null) }
            try {
                val location = locationService.getCurrentLocation()
                if (location != null) {
                    _uiState.update { it.copy(
                        pendingLat = location.lat,
                        pendingLng = location.lng,
                        cameraMoveId = it.cameraMoveId + 1,
                    ) }
                } else {
                    _uiState.update { it.copy(locationError = "Could not get location. Try again.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(locationError = "Location error: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLocating = false) }
            }
        }
    }

    fun onPendingLocationChanged(lat: Double, lng: Double) {
        _uiState.update { it.copy(pendingLat = lat, pendingLng = lng) }
    }

    fun onRemovePhoto() {
        _uiState.update { it.copy(photoPath = null) }
    }

    fun confirmLocation() {
        val state = _uiState.value
        val lat = state.pendingLat ?: return
        val lng = state.pendingLng ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            try {
                val resolved = locationService.reverseGeocode(lat, lng)
                _uiState.update { it.copy(
                    latitude = lat,
                    longitude = lng,
                    placeName = resolved,
                    name = if (it.name.isBlank()) resolved.split(", ").first() else it.name,
                ) }
            } finally {
                _uiState.update { it.copy(isLocating = false) }
            }
            closeLocationDialog()
        }
    }

    fun saveItem() {
        val state = _uiState.value
        val path = state.photoPath ?: return
        viewModelScope.launch {
            val trimmedNotes = state.notes.trim()
            val cleanNotes = trimmedNotes.replace(Regex("(\\r?\\n){3,}"), "\n\n")
            val saved = saveItem(Item(
                id = editId ?: 0,
                name = state.name,
                notes = cleanNotes,
                photoPath = path,
                latitude = state.latitude,
                longitude = state.longitude,
                placeName = state.placeName,
                dateAcquired = state.dateAcquired,
                category = state.category,
            ))
            if (saved) {
                imagePicker.cleanupOnSave(path)
                _uiState.update { it.copy(isSaved = true) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (!_uiState.value.isSaved) {
            imagePicker.cleanupOrphansAndClose()
        } else {
            imagePicker.close()
        }
    }
}
