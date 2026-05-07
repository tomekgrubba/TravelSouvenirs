package com.travelsouvenirs.main.sync

import com.travelsouvenirs.main.data.ItemDao
import com.travelsouvenirs.main.image.ImageStorage

/** Downloads image files that exist remotely but haven't been fetched locally yet. */
class ImageSyncService(
    private val dao: ItemDao,
    private val imageStorage: ImageStorage,
) {
    suspend fun downloadMissingImages() {
        val items = dao.getItemsWithMissingLocalPhotos()
        for (entity in items) {
            try {
                val localPath = imageStorage.localPathForDownload(entity.firebaseId)
                downloadUrlToFile(entity.photoStorageUrl, localPath)
                dao.insertItem(entity.copy(photoPath = localPath))
            } catch (_: Exception) { }
        }
    }
}
