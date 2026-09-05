package com.travelsouvenirs.main.sync

import com.travelsouvenirs.main.data.ItemDao
import com.travelsouvenirs.main.image.ImageStorage

import kotlinx.coroutines.CancellationException

/** Engine interface for syncing missing images. */
interface ImageSyncEngine {
    suspend fun downloadMissingImages()
}

/** Downloads image files that exist remotely but haven't been fetched locally yet. */
class ImageSyncService(
    private val dao: ItemDao,
    private val imageStorage: ImageStorage,
) : ImageSyncEngine {
    override suspend fun downloadMissingImages() {
        val items = dao.getItemsWithMissingLocalPhotos()
        for (entity in items) {
            try {
                val storedPath = imageStorage.localPathForDownload(entity.firebaseId)
                val fullPath = imageStorage.resolvedLocalPath(storedPath)
                downloadUrlToFile(entity.photoStorageUrl, fullPath)
                dao.updatePhotoPath(entity.id, storedPath)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
}
