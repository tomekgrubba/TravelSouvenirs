package com.travelsouvenirs.main.sync

import dev.gitlive.firebase.storage.FirebaseStorage

class ImageSyncHelper(private val storage: FirebaseStorage) : CloudImageStorage {
    override suspend fun upload(userId: String, firebaseId: String, localPath: String): Pair<String, String> {
        val storagePath = "users/$userId/photos/$firebaseId.jpg"
        val ref = storage.reference(storagePath)
        ref.putFile(fileFromPath(localPath))
        val url = ref.getDownloadUrl()
        return storagePath to url
    }

    override suspend fun delete(storagePath: String) {
        if (storagePath.isNotBlank()) {
            try {
                storage.reference(storagePath).delete()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Ignore if already deleted or not found
            }
        }
    }
}
