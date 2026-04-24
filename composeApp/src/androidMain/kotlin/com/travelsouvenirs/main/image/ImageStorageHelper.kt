package com.travelsouvenirs.main.image

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

import java.util.UUID

/** Manages photo files in app-private internal storage. */
object ImageStorageHelper {

    /** Copies the image at [sourceUri] into the app's private photo directory and returns its path. */
    fun copyToInternalStorage(context: Context, sourceUri: Uri): String? {
        val filename = "magnet_${UUID.randomUUID()}.jpg"
        val dir = File(context.filesDir, "magnet_photos")
        dir.mkdirs()
        val destFile = File(dir, filename)
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Creates a new empty file for a camera capture and returns its content URI and File handle. */
    fun createTempUri(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, "temp_photos")
        dir.mkdirs()
        val file = File(dir, "temp_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        return Pair(uri, file)
    }

    /** Deletes all files in the temporary photo directory. */
    fun clearTempFiles(context: Context) {
        val dir = File(context.cacheDir, "temp_photos")
        dir.deleteRecursively()
    }
}
