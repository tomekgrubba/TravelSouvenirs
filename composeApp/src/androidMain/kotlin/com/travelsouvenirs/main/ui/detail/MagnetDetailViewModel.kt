package com.travelsouvenirs.main.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.Magnet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MagnetDetailViewModel(
    private val repository: MagnetRepository,
    private val magnetId: Long
) : ViewModel() {

    private val _magnet = MutableStateFlow<Magnet?>(null)
    val magnet: StateFlow<Magnet?> = _magnet.asStateFlow()

    init {
        viewModelScope.launch {
            _magnet.value = repository.getMagnetById(magnetId)
        }
    }

    fun deleteMagnet(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _magnet.value?.let {
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
