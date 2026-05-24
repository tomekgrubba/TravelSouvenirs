package com.travelsouvenirs.main.sync

/** Provider-agnostic interface for uploading images to cloud storage. */
interface CloudImageStorage {
    /**
     * Uploads the image file at [localPath] to the cloud.
     * Returns a Pair of: (relative storage path, absolute download URL).
     */
    suspend fun upload(userId: String, firebaseId: String, localPath: String): Pair<String, String>
}
