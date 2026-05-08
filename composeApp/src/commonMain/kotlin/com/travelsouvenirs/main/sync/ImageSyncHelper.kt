package com.travelsouvenirs.main.sync

import dev.gitlive.firebase.storage.FirebaseStorage

class ImageSyncHelper(private val storage: FirebaseStorage) {
    suspend fun upload(userId: String, firebaseId: String, localPath: String): Pair<String, String> {
        val storagePath = "users/$userId/photos/$firebaseId.jpg"
        val ref = storage.reference(storagePath)
        ref.putFile(fileFromPath(localPath))
        val url = ref.getDownloadUrl()
        return storagePath to url
    }
}
