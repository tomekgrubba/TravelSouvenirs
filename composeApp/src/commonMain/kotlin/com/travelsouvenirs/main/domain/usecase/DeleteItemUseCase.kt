package com.travelsouvenirs.main.domain.usecase

import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.image.ImageStorage

class DeleteItemUseCase(
    private val repository: ItemRepository,
    private val imageStorage: ImageStorage,
) {
    suspend operator fun invoke(item: Item) {
        imageStorage.deleteImage(item.photoPath)
        repository.deleteItem(item)
    }
}
