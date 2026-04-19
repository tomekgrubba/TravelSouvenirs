package com.travelsouvenirs.main.image

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ImageStorageHelper {

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
