package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.travelsouvenirs.main.image.IosImageStorage
import kotlinx.cinterop.ExperimentalForeignApi
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
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPhotoPicker(onResult: (String?) -> Unit): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val imageStorage = remember { IosImageStorage() }
    return remember {
        {
            val config = PHPickerConfiguration()
            config.filter = PHPickerFilter.imagesFilter
            config.selectionLimit = 1
            val picker = PHPickerViewController(configuration = config)
            val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                    picker.dismissViewControllerAnimated(true, null)
                    val result = didFinishPicking.firstOrNull() as? PHPickerResult
                    val provider = result?.itemProvider
                    if (provider != null && provider.canLoadObjectOfClass(platform.UIKit.UIImage)) {
                        provider.loadObjectOfClass(platform.UIKit.UIImage) { obj, _ ->
                            val image = obj as? platform.UIKit.UIImage
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
                    } else {
                        currentOnResult.value(null)
                    }
                }
            }
            picker.delegate = delegate
            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCameraCapture(onResult: (String?) -> Unit): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
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
                    picker.dismissViewControllerAnimated(true, null)
                    currentOnResult.value(null)
                }
            }
            picker.delegate = delegate
            UIApplication.sharedApplication.keyWindow?.rootViewController
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
