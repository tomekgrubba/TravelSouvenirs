package com.travelsouvenirs.main.image

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Manages photo files in app-private internal storage. */
object ImageStorageHelper {

    /** Copies the image at [sourceUri] into the app's private photo directory and returns its path. */
    fun copyToInternalStorage(context: Context, sourceUri: Uri): String {
        val filename = "magnet_${System.currentTimeMillis()}.jpg"
        val dir = File(context.filesDir, "magnet_photos")
        dir.mkdirs()
        val destFile = File(dir, filename)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return destFile.absolutePath
    }

    /** Creates a new empty file for a camera capture and returns its content URI and File handle. */
    fun createCameraOutputUri(context: Context): Pair<Uri, File> {
        val dir = File(context.filesDir, "magnet_photos")
        dir.mkdirs()
        val file = File(dir, "camera_temp_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        return Pair(uri, file)
    }
}
