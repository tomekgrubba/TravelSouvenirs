package com.travelsouvenirs.main.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.domain.Magnet
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.location.LocationService
import com.travelsouvenirs.main.location.PlaceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.travelsouvenirs.main.platform.todayLocalDate
import kotlinx.datetime.LocalDate

private const val KEY_CATEGORIES = "categories"

/**
 * Drives both the Add and Edit screens.
 * When [editId] is non-null, pre-populates fields from the existing item and upserts on save.
 */
class AddMagnetViewModel(
    private val repository: MagnetRepository,
    private val locationService: LocationService,
    private val imageStorage: ImageStorage,
    private val editId: Long? = null,
    settings: Settings
) : ViewModel() {

    private val _photoPath = MutableStateFlow<String?>(null)
    val photoPath: StateFlow<String?> = _photoPath.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _dateAcquired: MutableStateFlow<LocalDate> = MutableStateFlow(todayLocalDate())
    val dateAcquired: StateFlow<LocalDate> = _dateAcquired.asStateFlow()

    private val _latitude = MutableStateFlow(0.0)
    private val _longitude = MutableStateFlow(0.0)

    private val _placeName = MutableStateFlow("")
    val placeName: StateFlow<String> = _placeName.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _showLocationDialog = MutableStateFlow(false)
    val showLocationDialog: StateFlow<Boolean> = _showLocationDialog.asStateFlow()

    // Staged pin coordinates inside the location dialog — committed only on Confirm.
    private val _pendingLat = MutableStateFlow<Double?>(null)
    val pendingLat: StateFlow<Double?> = _pendingLat.asStateFlow()

    private val _pendingLng = MutableStateFlow<Double?>(null)
    val pendingLng: StateFlow<Double?> = _pendingLng.asStateFlow()

    // Incremented on GPS fetch or search selection to trigger a camera animation in the map.
    // NOT incremented on drag or tap — so the camera never snaps back mid-drag.
    private val _cameraMoveId = MutableStateFlow(0)
    val cameraMoveId: StateFlow<Int> = _cameraMoveId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PlaceResult>>(emptyList())
    val searchResults: StateFlow<List<PlaceResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _locationError = MutableStateFlow<String?>(null)
    val locationError: StateFlow<String?> = _locationError.asStateFlow()

    /** All selectable categories: Default + user-defined custom categories. */
    val availableCategories: List<String> = buildList {
        add(DEFAULT_CATEGORY)
        val raw = settings.getStringOrNull(KEY_CATEGORIES) ?: ""
        addAll(raw.split(",").filter { it.isNotBlank() })
    }

    private val _category = MutableStateFlow(resolveDefaultCategory(availableCategories, editId))
    /** Currently selected category for this item. */
    val category: StateFlow<String> = _category.asStateFlow()

    private var searchJob: Job? = null
    private val cleanupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Tracks all image files copied during this session so orphans can be cleaned up.
    private val copiedPhotoPaths = mutableSetOf<String>()
    // The original photo path when editing — must never be deleted by this ViewModel.
    private var originalPhotoPath: String? = null

    init {
        if (editId != null) {
            viewModelScope.launch {
                repository.getMagnetById(editId)?.let { m ->
                    _photoPath.value = m.photoPath
                    originalPhotoPath = m.photoPath
                    _name.value = m.name
                    _notes.value = m.notes
                    _dateAcquired.value = m.dateAcquired
                    _placeName.value = m.placeName
                    _latitude.value = m.latitude
                    _longitude.value = m.longitude
                    _category.value = m.category
                }
            }
        }
    }

    /** Copies the image at [sourcePath] to internal storage on the IO dispatcher and updates [photoPath]. */
    fun onPhotoSelected(sourcePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = imageStorage.copyToInternalStorage(sourcePath)
            if (path != null) {
                copiedPhotoPaths.add(path)
            }
            _photoPath.value = path
        }
    }

    /** Updates the item name field. */
    fun onNameChange(value: String) { _name.value = value }
    /** Updates the notes field. */
    fun onNotesChange(value: String) { _notes.value = value }
    /** Updates the acquisition date. */
    fun onDateChange(date: LocalDate) { _dateAcquired.value = date }
    /** Updates the selected category. */
    fun onCategoryChange(value: String) { _category.value = value }

    /**
     * Opens the unified location dialog. Pre-populates the pending pin from the committed
     * location if one exists; otherwise tries GPS silently (no error shown on failure).
     */
    fun openLocationDialog() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _locationError.value = null
        val lat = _latitude.value
        val lng = _longitude.value
        if (lat != 0.0 && lng != 0.0) {
            _pendingLat.value = lat
            _pendingLng.value = lng
            _cameraMoveId.value++
        } else {
            _pendingLat.value = null
            _pendingLng.value = null
            viewModelScope.launch {
                _isLocating.value = true
                try {
                    val loc = locationService.getCurrentLocation()
                    if (loc != null) {
                        _pendingLat.value = loc.lat
                        _pendingLng.value = loc.lng
                        _cameraMoveId.value++
                    }
                } catch (_: Exception) {
                    // Silently ignored — user can tap the map or search instead
                } finally {
                    _isLocating.value = false
                }
            }
        }
        _showLocationDialog.value = true
    }

    /** Closes the location picker dialog and cancels any in-flight search. */
    fun closeLocationDialog() {
        _showLocationDialog.value = false
        searchJob?.cancel()
    }

    /** Debounces geocoder search by 400 ms; clears results when query is fewer than 2 characters. */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _isSearching.value = true
            _searchResults.value = locationService.searchByName(query)
            _isSearching.value = false
        }
    }

    /**
     * Places the pending pin at the selected [place] and animates the camera there.
     * Does not close the dialog — the user must tap Confirm.
     */
    fun onPlaceSelected(place: PlaceResult) {
        _pendingLat.value = place.latitude
        _pendingLng.value = place.longitude
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _cameraMoveId.value++
        if (_name.value.isBlank()) _name.value = place.name.split(", ").first()
    }

    /**
     * Fetches the device's current GPS location and places the pending pin there.
     * Animates the camera to the new position. Shows an error if location is unavailable.
     * Does not close the dialog — the user must tap Confirm.
     */
    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _isLocating.value = true
            _locationError.value = null
            try {
                val location = locationService.getCurrentLocation()
                if (location != null) {
                    _pendingLat.value = location.lat
                    _pendingLng.value = location.lng
                    _cameraMoveId.value++
                } else {
                    _locationError.value = "Could not get location. Try again."
                }
            } catch (e: Exception) {
                _locationError.value = "Location error: ${e.message}"
            } finally {
                _isLocating.value = false
            }
        }
    }

    /**
     * Updates the pending pin position from a map tap or drag.
     * Does NOT increment [_cameraMoveId] so the camera does not snap back.
     */
    fun onPendingLocationChanged(lat: Double, lng: Double) {
        _pendingLat.value = lat
        _pendingLng.value = lng
    }

    /**
     * Reverse-geocodes the pending pin, commits it to the form, and closes the dialog.
     */
    fun confirmLocation() {
        val lat = _pendingLat.value ?: return
        val lng = _pendingLng.value ?: return
        viewModelScope.launch {
            _isLocating.value = true
            try {
                val resolved = locationService.reverseGeocode(lat, lng)
                _latitude.value = lat
                _longitude.value = lng
                _placeName.value = resolved
                if (_name.value.isBlank()) _name.value = resolved.split(", ").first()
            } finally {
                _isLocating.value = false
            }
            closeLocationDialog()
        }
    }

    /** Persists the item; no-ops if photo or name is missing. Sets [isSaved] to true on success. */
    fun saveMagnet() {
        val path = _photoPath.value ?: return
        if (_name.value.isBlank()) return
        viewModelScope.launch {
            // Clean up any previously-copied photos that were replaced before saving.
            copiedPhotoPaths
                .filter { it != path }
                .forEach { imageStorage.deleteImage(it) }
            copiedPhotoPaths.clear()

            // If editing and the user picked a new photo, delete the old one.
            val orig = originalPhotoPath
            if (orig != null && orig != path) {
                imageStorage.deleteImage(orig)
            }

            repository.insertMagnet(
                Magnet(
                    id = editId ?: 0,
                    name = _name.value,
                    notes = _notes.value,
                    photoPath = path,
                    latitude = _latitude.value,
                    longitude = _longitude.value,
                    placeName = _placeName.value,
                    dateAcquired = _dateAcquired.value,
                    category = _category.value
                )
            )
            _isSaved.value = true
        }
    }

    /**
     * Called when the ViewModel is destroyed (user navigated away).
     * Deletes any copied photos that were never saved to the database.
     */
    override fun onCleared() {
        super.onCleared()
        if (!_isSaved.value && copiedPhotoPaths.isNotEmpty()) {
            val paths = copiedPhotoPaths.toList()
            cleanupScope.launch {
                paths.forEach { path ->
                    try { imageStorage.deleteImage(path) } catch (_: Exception) { }
                }
                cleanupScope.cancel()
            }
        } else {
            cleanupScope.cancel()
        }
    }
}

private fun resolveDefaultCategory(available: List<String>, editId: Long?): String {
    if (editId != null) return DEFAULT_CATEGORY  // will be overwritten by init block
    val custom = available.filter { it != DEFAULT_CATEGORY }
    return if (custom.size == 1) custom[0] else DEFAULT_CATEGORY
}
