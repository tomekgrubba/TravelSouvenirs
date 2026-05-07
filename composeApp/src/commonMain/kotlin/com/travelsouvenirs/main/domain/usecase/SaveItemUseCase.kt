package com.travelsouvenirs.main.domain.usecase

import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.Item

class SaveItemUseCase(private val repository: ItemRepository) {
    suspend operator fun invoke(item: Item): Boolean {
        if (item.name.isBlank() || item.photoPath.isBlank()) return false
        repository.insertItem(item)
        return true
    }
}
