package com.travelsouvenirs.main.image

/** Platform-agnostic interface for storing photo files in app-private storage. */
interface ImageStorage {
    /**
     * Copies the image at [sourcePath] into app-private storage and returns its absolute path.
     * On Android [sourcePath] is a Uri string; on iOS it is a file-system path.
     */
    suspend fun copyToInternalStorage(sourcePath: String): String?
}
