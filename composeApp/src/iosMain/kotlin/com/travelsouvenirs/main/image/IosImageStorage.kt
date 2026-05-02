package com.travelsouvenirs.main.image

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
class IosImageStorage : ImageStorage {
    override suspend fun copyToInternalStorage(sourcePath: String): String {
        val filename = "item_${time(null)}.jpg"
        val dir = "${NSHomeDirectory()}/Documents/item_photos"
        val destPath = "$dir/$filename"
        val fm = NSFileManager.defaultManager
        fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        fm.copyItemAtPath(sourcePath, toPath = destPath, error = null)
        return destPath
    }

    override suspend fun deleteImage(path: String) {
        if (path.isBlank()) return
        val fm = NSFileManager.defaultManager
        val documentsDir = "${NSHomeDirectory()}/Documents"
        if (path.startsWith(documentsDir) && fm.fileExistsAtPath(path)) {
            fm.removeItemAtPath(path, error = null)
        }
    }

    override fun localPathForDownload(firebaseId: String): String {
        val dir = "${NSHomeDirectory()}/Documents/item_photos"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir, withIntermediateDirectories = true, attributes = null, error = null
        )
        return "$dir/item_$firebaseId.jpg"
    }
}
