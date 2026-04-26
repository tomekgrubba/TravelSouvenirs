package com.travelsouvenirs.main.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.image.ImageStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the detail screen; observes a single item via Flow so edits reflect immediately. */
class ItemDetailViewModel(
    private val repository: ItemRepository,
    private val itemId: Long,
    private val imageStorage: ImageStorage
) : ViewModel() {

    /** The item being viewed; null until the database emits the first value. */
    val item: StateFlow<Item?> = repository.getItemByIdFlow(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Deletes the current item and its image file, then invokes [onDeleted]. */
    fun deleteItem(onDeleted: () -> Unit) {
        viewModelScope.launch {
            item.value?.let {
                imageStorage.deleteImage(it.photoPath)
                repository.deleteItem(it)
                onDeleted()
            }
        }
    }
}
