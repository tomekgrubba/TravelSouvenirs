@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.travelsouvenirs.main.image.IosImageStorage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import kotlinx.coroutines.launch
import com.travelsouvenirs.main.image.IMAGE_JPEG_QUALITY
import com.travelsouvenirs.main.image.IMAGE_MAX_SIDE_PX
import kotlinx.datetime.LocalDate
import platform.posix.time

private data class ExifMetadata(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val date: LocalDate? = null
)

@OptIn(ExperimentalForeignApi::class)
private fun extractExif(nsData: platform.Foundation.NSData): ExifMetadata {
    var latitude: Double? = null
    var longitude: Double? = null
    var date: LocalDate? = null

    val cfData = platform.Foundation.CFBridgingRetain(nsData) as platform.CoreFoundation.CFDataRef
    val source = platform.ImageIO.CGImageSourceCreateWithData(cfData, null)
    if (source != null) {
        val cfDict = platform.ImageIO.CGImageSourceCopyPropertiesAtIndex(source, 0UL, null)
        val properties = cfDict?.let { platform.Foundation.CFBridgingRelease(it) as? platform.Foundation.NSDictionary }
        if (properties != null) {
            val gps = properties.objectForKey(platform.ImageIO.kCGImagePropertyGPSDictionary) as? platform.Foundation.NSDictionary
            if (gps != null) {
                val rawLat = gps.objectForKey(platform.ImageIO.kCGImagePropertyGPSLatitude) as? Double
                val rawLng = gps.objectForKey(platform.ImageIO.kCGImagePropertyGPSLongitude) as? Double
                val latRef = gps.objectForKey(platform.ImageIO.kCGImagePropertyGPSLatitudeRef) as? String
                val lngRef = gps.objectForKey(platform.ImageIO.kCGImagePropertyGPSLongitudeRef) as? String

                if (rawLat != null) {
                    latitude = if (latRef == "S") -rawLat else rawLat
                }
                if (rawLng != null) {
                    longitude = if (lngRef == "W") -rawLng else rawLng
                }
            }

            val exif = properties.objectForKey(platform.ImageIO.kCGImagePropertyExifDictionary) as? platform.Foundation.NSDictionary
            if (exif != null) {
                val dateStr = exif.objectForKey(platform.ImageIO.kCGImagePropertyExifDateTimeOriginal) as? String
                if (dateStr != null) {
                    val parts = dateStr.split(" ")
                    if (parts.isNotEmpty()) {
                        val dateParts = parts[0].split(":")
                        if (dateParts.size == 3) {
                            try {
                                val year = dateParts[0].toInt()
                                val month = dateParts[1].toInt()
                                val day = dateParts[2].toInt()
                                date = LocalDate(year, month, day)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
        platform.CoreFoundation.CFRelease(source)
    }
    platform.CoreFoundation.CFRelease(cfData)

    return ExifMetadata(latitude, longitude, date)
}

@OptIn(ExperimentalForeignApi::class)
private fun resizeUIImage(image: UIImage): UIImage {
    val (w, h) = image.size.useContents { width to height }
    val longest = maxOf(w, h)
    val maxSide = IMAGE_MAX_SIDE_PX.toDouble()
    if (longest <= maxSide) return image
    val scale = maxSide / longest
    val newW = w * scale
    val newH = h * scale
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(newW, newH), false, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, newW, newH))
    val resized = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return resized ?: image
}

@OptIn(ExperimentalForeignApi::class)
private fun activeRootViewController() =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull()
        ?.windows
        ?.filterIsInstance<UIWindow>()
        ?.firstOrNull { it.isKeyWindow() }
        ?.rootViewController

private fun saveJpegToDir(data: platform.Foundation.NSData, dir: String): String {
    val filename = "item_${time(null)}.jpg"
    val destPath = "$dir/$filename"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    NSFileManager.defaultManager.createFileAtPath(destPath, contents = data, attributes = null)
    return destPath
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPhotoPicker(onResult: (path: String?, exifLat: Double?, exifLng: Double?, exifDate: LocalDate?) -> Unit): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val imageStorage = remember { IosImageStorage() }
    val retainedDelegates = remember { mutableSetOf<Any>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember {
        {
            val config = PHPickerConfiguration()
            config.filter = PHPickerFilter.imagesFilter
            config.selectionLimit = 1
            val picker = PHPickerViewController(configuration = config)
            val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                    retainedDelegates.remove(this)
                    picker.dismissViewControllerAnimated(true, null)
                    val result = didFinishPicking.firstOrNull() as? PHPickerResult
                    val provider = result?.itemProvider
                    if (provider != null && provider.hasItemConformingToTypeIdentifier("public.image")) {
                        provider.loadDataRepresentationForTypeIdentifier("public.image") { nsData, _ ->
                            val image = nsData?.let { UIImage(data = it) }
                            val jpegData = image?.let { UIImageJPEGRepresentation(resizeUIImage(it), IMAGE_JPEG_QUALITY / 100.0) }
                            val exif = nsData?.let { extractExif(it) }
                            scope.launch {
                                if (jpegData != null) {
                                    val dir = "${NSHomeDirectory()}/Documents/item_photos"
                                    val destPath = saveJpegToDir(jpegData, dir)
                                    currentOnResult.value(destPath, exif?.latitude, exif?.longitude, exif?.date)
                                } else {
                                    currentOnResult.value(null, null, null, null)
                                }
                            }
                        }
                    } else {
                        scope.launch {
                            currentOnResult.value(null, null, null, null)
                        }
                    }
                }
            }
            retainedDelegates.add(delegate)
            picker.delegate = delegate
            activeRootViewController()
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCameraCapture(onResult: (path: String?, exifLat: Double?, exifLng: Double?) -> Unit): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val retainedDelegates = remember { mutableSetOf<Any>() }
    return remember {
        {
            val picker = UIImagePickerController()
            picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            picker.allowsEditing = true
            val delegate = object : NSObject(),
                UIImagePickerControllerDelegateProtocol,
                UINavigationControllerDelegateProtocol {
                override fun imagePickerController(
                    picker: UIImagePickerController,
                    didFinishPickingMediaWithInfo: Map<Any?, *>
                ) {
                    retainedDelegates.remove(this)
                    picker.dismissViewControllerAnimated(true, null)
                    val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
                        ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage])
                            as? UIImage
                    val data = image?.let { UIImageJPEGRepresentation(resizeUIImage(it), IMAGE_JPEG_QUALITY / 100.0) }
                    if (data != null) {
                        val dir = "${NSHomeDirectory()}/Documents/item_photos"
                        val destPath = saveJpegToDir(data, dir)
                        currentOnResult.value(destPath, null, null)
                    } else {
                        currentOnResult.value(null, null, null)
                    }
                }

                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    retainedDelegates.remove(this)
                    picker.dismissViewControllerAnimated(true, null)
                    currentOnResult.value(null, null, null)
                }
            }
            retainedDelegates.add(delegate)
            picker.delegate = delegate
            activeRootViewController()
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** iOS: no-op — CLLocationManager requests permission automatically on first use. */
@Composable
actual fun rememberLocationPermissionLauncher(onGranted: () -> Unit): () -> Unit {
    val currentOnGranted = rememberUpdatedState(onGranted)
    return remember { { currentOnGranted.value() } }
}
