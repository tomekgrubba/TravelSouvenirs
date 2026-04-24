package com.travelsouvenirs.main.image

import android.content.Context
import android.net.Uri
import java.io.File

/** Android implementation of [ImageStorage] that copies images into app-private storage. */
class AndroidImageStorage(private val context: Context) : ImageStorage {
    /** Parses [sourcePath] as a content URI and copies the image to the app's private photo directory. */
    override suspend fun copyToInternalStorage(sourcePath: String): String? =
        ImageStorageHelper.copyToInternalStorage(context, Uri.parse(sourcePath))

    /** Deletes the image file at [path] if it exists and is inside app-private storage. */
    override suspend fun deleteImage(path: String) {
        if (path.isBlank()) return
        val file = File(path)
        if (file.exists() && file.absolutePath.startsWith(context.filesDir.absolutePath)) {
            file.delete()
        }
    }
}
