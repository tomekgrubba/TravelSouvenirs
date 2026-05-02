package com.travelsouvenirs.main.sync

import dev.gitlive.firebase.storage.Data
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun readLocalFileBytes(path: String): ByteArray = File(path).readBytes()

actual fun localFileExists(path: String): Boolean = File(path).exists()

actual suspend fun downloadUrlToFile(url: String, localPath: String) {
    withContext(Dispatchers.IO) {
        val bytes = URL(url).readBytes()
        File(localPath).writeBytes(bytes)
    }
}

actual fun ByteArray.toFirebaseData(): Data = this as Data
