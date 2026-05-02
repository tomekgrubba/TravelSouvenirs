@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.travelsouvenirs.main.sync

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.memcpy
import platform.posix.open
import platform.posix.time
import platform.posix.write
import dev.gitlive.firebase.storage.Data

actual fun readLocalFileBytes(path: String): ByteArray {
    val data = NSData.dataWithContentsOfFile(path)
        ?: error("Could not read file at $path")
    return ByteArray(data.length.toInt()).also { bytes ->
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
}

actual fun localFileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

actual suspend fun downloadUrlToFile(url: String, localPath: String) {
    val nsUrl = NSURL.URLWithString(url) ?: error("Invalid URL: $url")
    val data = NSData.dataWithContentsOfURL(nsUrl) ?: error("Failed to download: $url")
    NSFileManager.defaultManager.createFileAtPath(localPath, contents = data, attributes = null)
}

actual fun ByteArray.toFirebaseData(): dev.gitlive.firebase.storage.Data {
    val tempPath = "${NSTemporaryDirectory()}fb_${time(null)}_$size.bin"
    usePinned { pinned ->
        val fd = open(tempPath, O_WRONLY or O_CREAT or O_TRUNC, 420u)
        if (fd >= 0) {
            write(fd, pinned.addressOf(0), size.toULong())
            close(fd)
        }
    }
    return (NSData.dataWithContentsOfFile(tempPath) ?: NSData()) as Data
}
