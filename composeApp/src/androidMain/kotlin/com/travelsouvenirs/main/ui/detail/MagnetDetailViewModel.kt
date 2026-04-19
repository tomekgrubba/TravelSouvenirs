package com.travelsouvenirs.main.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.Magnet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MagnetDetailViewModel(
    private val repository: MagnetRepository,
    private val magnetId: Long
) : ViewModel() {

    val magnet: StateFlow<Magnet?> = repository.getMagnetByIdFlow(magnetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun deleteMagnet(onDeleted: () -> Unit) {
        viewModelScope.launch {
            magnet.value?.let {
                repository.deleteMagnet(it)
                onDeleted()
            }
        }
    }

    class Factory(
        private val repository: MagnetRepository,
        private val magnetId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MagnetDetailViewModel(repository, magnetId) as T
    }
}
