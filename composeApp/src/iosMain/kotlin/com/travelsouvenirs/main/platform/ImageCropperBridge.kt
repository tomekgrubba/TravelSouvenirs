package com.travelsouvenirs.main.platform

/**
 * A bridge interface that allows the Swift side (which has access to TOCropViewController Pod)
 * to perform advanced image cropping (rotation, aspect ratio changes) and return the cropped file path to Kotlin.
 */
interface PlatformImageCropper {
    fun cropImage(
        imagePath: String,
        onSuccess: (String) -> Unit,
        onCancel: () -> Unit
    )
}

object ImageCropperRegistry {
    var cropper: PlatformImageCropper? = null
}
