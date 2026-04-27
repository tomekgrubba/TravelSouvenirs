package com.travelsouvenirs.main.sync

import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL

actual fun readLocalFileBytes(path: String): ByteArray {
    val data = NSData.dataWithContentsOfFile(path)
        ?: error("Could not read file at $path")
    return ByteArray(data.length.toInt()).also { bytes ->
        bytes.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
}

actual fun localFileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

actual suspend fun downloadUrlToFile(url: String, localPath: String) {
    val nsUrl = NSURL.URLWithString(url) ?: error("Invalid URL: $url")
    val data = NSData.dataWithContentsOfURL(nsUrl) ?: error("Failed to download: $url")
    data.writeToFile(localPath, atomically = true)
}
