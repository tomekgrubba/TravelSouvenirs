package com.travelsouvenirs.main.image

import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

/** iOS implementation of [ImageStorage] that copies image files to app Documents. */
class IosImageStorage : ImageStorage {
    /** Copies the file at [sourcePath] to the app's private photo directory and returns the new path. */
    override suspend fun copyToInternalStorage(sourcePath: String): String {
        val filename = "magnet_${platform.Foundation.NSDate().timeIntervalSince1970.toLong()}.jpg"
        val dir = "${NSHomeDirectory()}/Documents/magnet_photos"
        val destPath = "$dir/$filename"
        val fm = NSFileManager.defaultManager
        fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        fm.copyItemAtPath(sourcePath, toPath = destPath, error = null)
        return destPath
    }

    /** Deletes the image file at [path] if it exists within the app's Documents directory. */
    override suspend fun deleteImage(path: String) {
        if (path.isBlank()) return
        val fm = NSFileManager.defaultManager
        val documentsDir = "${NSHomeDirectory()}/Documents"
        if (path.startsWith(documentsDir) && fm.fileExistsAtPath(path)) {
            fm.removeItemAtPath(path, error = null)
        }
    }
}
