package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.travelsouvenirs.main.image.IosImageStorage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
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
import com.travelsouvenirs.main.image.IMAGE_JPEG_QUALITY
import com.travelsouvenirs.main.image.IMAGE_MAX_SIDE_PX

@OptIn(ExperimentalForeignApi::class)
private fun resizeUIImage(image: platform.UIKit.UIImage): platform.UIKit.UIImage {
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

private fun activeRootViewController() =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull()
        ?.windows
        ?.filterIsInstance<UIWindow>()
        ?.firstOrNull { it.isKeyWindow }
        ?.rootViewController

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPhotoPicker(onResult: (String?) -> Unit): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val imageStorage = remember { IosImageStorage() }
    // Strong references prevent ARC from collecting delegates whose picker.delegate is weak.
    val retainedDelegates = remember { mutableSetOf<Any>() }
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
                    if (provider != null && provider.canLoadObjectOfClass(platform.UIKit.UIImage)) {
                        provider.loadObjectOfClass(platform.UIKit.UIImage) { obj, _ ->
                            val image = obj as? platform.UIKit.UIImage
                            val data = image?.let { UIImageJPEGRepresentation(resizeUIImage(it), IMAGE_JPEG_QUALITY / 100.0) }
                            if (data != null) {
                                val filename = "magnet_${platform.Foundation.NSDate().timeIntervalSince1970.toLong()}.jpg"
                                val dir = "${NSHomeDirectory()}/Documents/magnet_photos"
                                val destPath = "$dir/$filename"
                                platform.Foundation.NSFileManager.defaultManager.let { fm ->
                                    fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
                                    data.writeToFile(destPath, atomically = true)
                                }
                                currentOnResult.value(destPath)
                            } else {
                                currentOnResult.value(null)
                            }
                        }
                    } else {
                        currentOnResult.value(null)
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
actual fun rememberCameraCapture(onResult: (String?) -> Unit): () -> Unit {
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
                            as? platform.UIKit.UIImage
                    val data = image?.let { UIImageJPEGRepresentation(it, 0.85) }
                    if (data != null) {
                        val filename = "magnet_${platform.Foundation.NSDate().timeIntervalSince1970.toLong()}.jpg"
                        val dir = "${NSHomeDirectory()}/Documents/magnet_photos"
                        val destPath = "$dir/$filename"
                        platform.Foundation.NSFileManager.defaultManager.let { fm ->
                            fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
                            data.writeToFile(destPath, atomically = true)
                        }
                        currentOnResult.value(destPath)
                    } else {
                        currentOnResult.value(null)
                    }
                }

                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    retainedDelegates.remove(this)
                    picker.dismissViewControllerAnimated(true, null)
                    currentOnResult.value(null)
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
