package com.travelsouvenirs.main.ui.add

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.Magnet
import com.travelsouvenirs.main.image.ImageStorageHelper
import com.travelsouvenirs.main.location.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class AddMagnetViewModel(private val repository: MagnetRepository) : ViewModel() {

    private val _photoPath = MutableStateFlow<String?>(null)
    val photoPath: StateFlow<String?> = _photoPath.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _dateAcquired = MutableStateFlow(
        Clock.System.todayIn(TimeZone.currentSystemDefault())
    )
    val dateAcquired: StateFlow<LocalDate> = _dateAcquired.asStateFlow()

    private val _latitude = MutableStateFlow(0.0)
    private val _longitude = MutableStateFlow(0.0)

    private val _placeName = MutableStateFlow("")
    val placeName: StateFlow<String> = _placeName.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _locationError = MutableStateFlow<String?>(null)
    val locationError: StateFlow<String?> = _locationError.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    fun onPhotoSelected(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = ImageStorageHelper.copyToInternalStorage(context, uri)
            _photoPath.value = path
        }
    }

    fun onNameChange(value: String) { _name.value = value }
    fun onNotesChange(value: String) { _notes.value = value }
    fun onDateChange(date: LocalDate) { _dateAcquired.value = date }
    fun onPlaceNameChange(value: String) { _placeName.value = value }

    fun fetchCurrentLocation(context: Context) {
        viewModelScope.launch {
            _isLocating.value = true
            _locationError.value = null
            try {
                val helper = LocationHelper(context)
                val location = helper.getCurrentLocation()
                if (location != null) {
                    _latitude.value = location.first
                    _longitude.value = location.second
                    _placeName.value = helper.reverseGeocode(location.first, location.second)
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

    fun saveMagnet() {
        val path = _photoPath.value ?: return
        if (_name.value.isBlank()) return
        viewModelScope.launch {
            repository.insertMagnet(
                Magnet(
                    id = 0,
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

    class Factory(private val repository: MagnetRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddMagnetViewModel(repository) as T
    }
}
