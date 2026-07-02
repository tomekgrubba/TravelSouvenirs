package com.travelsouvenirs.main.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

import com.travelsouvenirs.main.domain.LatLon

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** NavGraph-scoped ViewModel that carries cross-screen snackbar messages and camera position zoom requests. */
class AppViewModel : ViewModel() {
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _targetCameraLocation = MutableStateFlow<LatLon?>(null)
    val targetCameraLocation: StateFlow<LatLon?> = _targetCameraLocation.asStateFlow()

    fun showMessage(msg: String) {
        _snackbarMessage.tryEmit(msg)
    }

    fun zoomToLocation(location: LatLon) {
        _targetCameraLocation.value = location
    }

    fun clearTargetCameraLocation() {
        _targetCameraLocation.value = null
    }
}
