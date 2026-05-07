package com.travelsouvenirs.main.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** NavGraph-scoped ViewModel that carries cross-screen snackbar messages. */
class AppViewModel : ViewModel() {
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun showMessage(msg: String) {
        _snackbarMessage.tryEmit(msg)
    }
}
