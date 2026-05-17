package com.travelsouvenirs.main.image

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
class IosImageStorage : ImageStorage {

    // Store only the filename — never the absolute path, which includes a data-container UUID
    // that changes on every app reinstall. resolvedLocalPath() rebuilds the full path at runtime.

    override suspend fun copyToInternalStorage(sourcePath: String): String {
        val filename = "item_${time(null)}.jpg"
        val fm = NSFileManager.defaultManager
        fm.createDirectoryAtPath(photosDir(), withIntermediateDirectories = true, attributes = null, error = null)
        fm.copyItemAtPath(sourcePath, toPath = "${photosDir()}/$filename", error = null)
        return filename
    }

    override fun localPathForDownload(firebaseId: String): String = "item_$firebaseId.jpg"

    override fun resolvedLocalPath(storedPath: String): String {
        // Extract just the filename — handles old absolute paths (with stale UUID) and new filenames.
        val filename = storedPath.substringAfterLast("/")
        val dir = photosDir()
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        return "$dir/$filename"
    }

    override suspend fun deleteImage(path: String) {
        if (path.isBlank()) return
        val fullPath = resolvedLocalPath(path)
        val fm = NSFileManager.defaultManager
        if (fm.fileExistsAtPath(fullPath)) fm.removeItemAtPath(fullPath, error = null)
    }

    override suspend fun deleteAllImages() {
        NSFileManager.defaultManager.removeItemAtPath(photosDir(), error = null)
    }

    private fun photosDir() = "${NSHomeDirectory()}/Documents/item_photos"
}
