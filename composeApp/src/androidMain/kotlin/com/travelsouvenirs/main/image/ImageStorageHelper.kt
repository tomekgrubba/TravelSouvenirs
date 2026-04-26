package com.travelsouvenirs.main.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Manages photo files in app-private internal storage. */
object ImageStorageHelper {

    /** Decodes, resizes to ≤[IMAGE_MAX_SIDE_PX]px on the longest side, and saves as [IMAGE_JPEG_QUALITY]% JPEG. */
    fun copyToInternalStorage(context: Context, sourceUri: Uri): String? {
        val filename = "item_${UUID.randomUUID()}.jpg"
        val dir = File(context.filesDir, "item_photos")
        dir.mkdirs()
        val destFile = File(dir, filename)
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val original = BitmapFactory.decodeStream(input) ?: return null
                val resized = scaledDown(original)
                destFile.outputStream().use { out ->
                    resized.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, out)
                }
                if (resized !== original) resized.recycle()
                original.recycle()
            }
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun scaledDown(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= IMAGE_MAX_SIDE_PX) return src
        val scale = IMAGE_MAX_SIDE_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
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
