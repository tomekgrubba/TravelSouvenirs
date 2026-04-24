package com.travelsouvenirs.main.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.Magnet
import com.travelsouvenirs.main.image.ImageStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the detail screen; observes a single item via Flow so edits reflect immediately. */
class MagnetDetailViewModel(
    private val repository: MagnetRepository,
    private val magnetId: Long,
    private val imageStorage: ImageStorage
) : ViewModel() {

    /** The item being viewed; null until the database emits the first value. */
    val magnet: StateFlow<Magnet?> = repository.getMagnetByIdFlow(magnetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Deletes the current item and its image file, then invokes [onDeleted]. */
    fun deleteMagnet(onDeleted: () -> Unit) {
        viewModelScope.launch {
            magnet.value?.let {
                imageStorage.deleteImage(it.photoPath)
                repository.deleteMagnet(it)
                onDeleted()
            }
        }
    }
}
