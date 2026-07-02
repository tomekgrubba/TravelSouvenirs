package com.travelsouvenirs.main.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

import com.travelsouvenirs.main.domain.LatLon

/** NavGraph-scoped ViewModel that carries cross-screen snackbar messages and camera position zoom requests. */
class AppViewModel : ViewModel() {
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    var targetCameraLocation: LatLon? = null
        private set

    fun showMessage(msg: String) {
        _snackbarMessage.tryEmit(msg)
    }

    fun zoomToLocation(location: LatLon) {
        targetCameraLocation = location
    }

    fun clearTargetCameraLocation() {
        targetCameraLocation = null
    }
}
