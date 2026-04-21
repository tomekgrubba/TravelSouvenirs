package com.travelsouvenirs.main.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.Magnet
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.location.LocationService
import com.travelsouvenirs.main.location.PlaceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.travelsouvenirs.main.platform.todayLocalDate
import kotlinx.datetime.LocalDate

/**
 * Drives both the Add and Edit screens.
 * When [editId] is non-null, pre-populates fields from the existing item and upserts on save.
 */
class AddMagnetViewModel(
    private val repository: MagnetRepository,
    private val locationService: LocationService,
    private val imageStorage: ImageStorage,
    private val editId: Long? = null
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

    private var searchJob: Job? = null

    init {
        if (editId != null) {
            viewModelScope.launch {
                repository.getMagnetById(editId)?.let { m ->
                    _photoPath.value = m.photoPath
                    _name.value = m.name
                    _notes.value = m.notes
                    _dateAcquired.value = m.dateAcquired
                    _placeName.value = m.placeName
                    _latitude.value = m.latitude
                    _longitude.value = m.longitude
                }
            }
        }
    }

    /** Copies the image at [sourcePath] to internal storage on the IO dispatcher and updates [photoPath]. */
    fun onPhotoSelected(sourcePath: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val path = imageStorage.copyToInternalStorage(sourcePath)
            _photoPath.value = path
        }
    }

    /** Updates the item name field. */
    fun onNameChange(value: String) { _name.value = value }
    /** Updates the notes field. */
    fun onNotesChange(value: String) { _notes.value = value }
    /** Updates the acquisition date. */
    fun onDateChange(date: LocalDate) { _dateAcquired.value = date }

    /** Resets search state and opens the location picker dialog. */
    fun openLocationDialog() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _locationError.value = null
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

    /** Stores the chosen [place] coordinates and name, then closes the dialog. */
    fun onPlaceSelected(place: PlaceResult) {
        _latitude.value = place.latitude
        _longitude.value = place.longitude
        _placeName.value = place.name
        closeLocationDialog()
    }

    /** Requests the device's current location, reverse-geocodes it, and closes the dialog. */
    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _isLocating.value = true
            _locationError.value = null
            try {
                val location = locationService.getCurrentLocation()
                if (location != null) {
                    _latitude.value = location.lat
                    _longitude.value = location.lng
                    _placeName.value = locationService.reverseGeocode(location.lat, location.lng)
                    closeLocationDialog()
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

    /** Persists the item; no-ops if photo or name is missing. Sets [isSaved] to true on success. */
    fun saveMagnet() {
        val path = _photoPath.value ?: return
        if (_name.value.isBlank()) return
        viewModelScope.launch {
            repository.insertMagnet(
                Magnet(
                    id = editId ?: 0,
                    name = _name.value,
                    notes = _notes.value,
                    photoPath = path,
                    latitude = _latitude.value,
                    longitude = _longitude.value,
                    placeName = _placeName.value,
                    dateAcquired = _dateAcquired.value
                )
            )
            _isSaved.value = true
        }
    }
}
