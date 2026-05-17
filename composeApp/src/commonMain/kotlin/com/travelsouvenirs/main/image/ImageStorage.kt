package com.travelsouvenirs.main.image

/** Platform-agnostic interface for storing photo files in app-private storage. */
interface ImageStorage {
    /**
     * Copies the image at [sourcePath] into app-private storage and returns its absolute path.
     * On Android [sourcePath] is a Uri string; on iOS it is a file-system path.
     */
    suspend fun copyToInternalStorage(sourcePath: String): String?

    /**
     * Deletes the image file at [path] from app-private storage.
     * No-ops silently if the file does not exist or the path is null/blank.
     */
    suspend fun deleteImage(path: String)

    /**
     * Returns the value to store in the database for an image that will be downloaded to [firebaseId].
     * On Android this is the full absolute path; on iOS it is just the filename, so the path
     * remains valid across app reinstalls (which rotate the data-container UUID).
     */
    fun localPathForDownload(firebaseId: String): String

    /**
     * Resolves [storedPath] (as returned by [copyToInternalStorage] or [localPathForDownload])
     * to the actual full file-system path needed to read/write the file.
     * Default: identity — the stored path is already absolute (Android).
     * iOS overrides this to prepend the current NSHomeDirectory().
     */
    fun resolvedLocalPath(storedPath: String): String = storedPath

    /** Deletes all locally stored item photos (used on sign-out to clear account data). */
    suspend fun deleteAllImages()
}
