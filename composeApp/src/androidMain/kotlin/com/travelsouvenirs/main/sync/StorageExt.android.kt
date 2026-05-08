package com.travelsouvenirs.main.sync

import android.net.Uri
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.File
import java.io.File as JFile
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun readLocalFileBytes(path: String): ByteArray = JFile(path).readBytes()

actual fun localFileExists(path: String): Boolean = JFile(path).exists()

actual suspend fun downloadUrlToFile(url: String, localPath: String) {
    withContext(Dispatchers.IO) {
        URL(url).openStream().buffered().use { input ->
            JFile(localPath).outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

actual fun ByteArray.toFirebaseData(): Data = this as Data

actual fun fileFromPath(path: String): File = File(Uri.fromFile(JFile(path)))
