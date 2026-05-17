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
                val storedPath = imageStorage.localPathForDownload(entity.firebaseId)
                val fullPath = imageStorage.resolvedLocalPath(storedPath)
                downloadUrlToFile(entity.photoStorageUrl, fullPath)
                dao.insertItem(entity.copy(photoPath = storedPath))
            } catch (_: Exception) { }
        }
    }
}
