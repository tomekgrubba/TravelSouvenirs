package com.travelsouvenirs.main.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.travelsouvenirs.main.image.ImageStorageHelper
import com.travelsouvenirs.main.ui.add.CropActivity
import com.yalantis.ucrop.UCrop

private fun readExifGps(context: Context, uri: Uri): Pair<Double?, Double?> {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val latLng = exif.latLong
            if (latLng != null) Pair(latLng[0], latLng[1]) else Pair(null, null)
        } ?: Pair(null, null)
    } catch (_: Exception) {
        Pair(null, null)
    }
}

@Composable
actual fun rememberPhotoPicker(onResult: (path: String?, exifLat: Double?, exifLng: Double?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    var pendingExifLat by remember { mutableStateOf<Double?>(null) }
    var pendingExifLng by remember { mutableStateOf<Double?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                UCrop.getOutput(data)?.let { uri ->
                    currentOnResult.value(uri.toString(), pendingExifLat, pendingExifLng)
                }
            } ?: currentOnResult.value(null, null, null)
        } else currentOnResult.value(null, null, null)
        pendingExifLat = null
        pendingExifLng = null
    }

    fun launchCrop(sourceUri: Uri) {
        val (destUri, _) = ImageStorageHelper.createTempUri(context)
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setToolbarColor(0xFF1C1C1E.toInt())
            setStatusBarColor(0xFF1C1C1E.toInt())
            setToolbarWidgetColor(android.graphics.Color.WHITE)
            setActiveControlsWidgetColor(0xFFBBBBBB.toInt())
            setDimmedLayerColor(0xCC000000.toInt())
        }
        val intent = UCrop.of(sourceUri, destUri)
            .withOptions(options)
            .getIntent(context)
            .setClass(context, CropActivity::class.java)
        cropLauncher.launch(intent)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val (lat, lng) = readExifGps(context, uri)
            pendingExifLat = lat
            pendingExifLng = lng
            launchCrop(uri)
        } else {
            currentOnResult.value(null, null, null)
        }
    }

    return remember {
        {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
}

@Composable
actual fun rememberCameraCapture(onResult: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                UCrop.getOutput(data)?.let { uri ->
                    currentOnResult.value(uri.toString())
                }
            } ?: currentOnResult.value(null)
        } else currentOnResult.value(null)
    }

    fun launchCrop(sourceUri: Uri) {
        val (destUri, _) = ImageStorageHelper.createTempUri(context)
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setToolbarColor(0xFF1C1C1E.toInt())
            setStatusBarColor(0xFF1C1C1E.toInt())
            setToolbarWidgetColor(android.graphics.Color.WHITE)
            setActiveControlsWidgetColor(0xFFBBBBBB.toInt())
            setDimmedLayerColor(0xCC000000.toInt())
        }
        val intent = UCrop.of(sourceUri, destUri)
            .withOptions(options)
            .getIntent(context)
            .setClass(context, CropActivity::class.java)
        cropLauncher.launch(intent)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraOutputUri?.let { launchCrop(it) } else currentOnResult.value(null)
        cameraOutputUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, _) = ImageStorageHelper.createTempUri(context)
            cameraOutputUri = uri
            takePictureLauncher.launch(uri)
        } else currentOnResult.value(null)
    }

    return remember {
        {
            ImageStorageHelper.clearTempFiles(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PERMISSION_GRANTED) {
                val (uri, _) = ImageStorageHelper.createTempUri(context)
                cameraOutputUri = uri
                takePictureLauncher.launch(uri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

@Composable
actual fun rememberLocationPermissionLauncher(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnGranted = rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) currentOnGranted.value()
    }

    return remember {
        {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                currentOnGranted.value()
            } else {
                launcher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }
}
