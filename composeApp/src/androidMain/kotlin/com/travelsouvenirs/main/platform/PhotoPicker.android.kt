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
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.travelsouvenirs.main.image.ImageStorageHelper
import com.travelsouvenirs.main.ui.add.CropActivity
import com.yalantis.ucrop.UCrop
import kotlinx.datetime.LocalDate

private data class ExifData(val lat: Double?, val lng: Double?, val date: LocalDate?)

private fun ExifInterface.parseData(): ExifData {
    val latLng = latLong
    val rawDate = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        ?: getAttribute(ExifInterface.TAG_DATETIME)
    val date = rawDate?.let {
        val parts = it.split(" ")[0].split(":")
        if (parts.size >= 3) runCatching {
            LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }.getOrNull() else null
    }
    return ExifData(latLng?.get(0), latLng?.get(1), date)
}

private fun readExif(context: Context, uri: Uri): ExifData {
    // First pass: setRequireOriginal lifts the GPS redaction applied by the Android Photo Picker
    // (requires ACCESS_MEDIA_LOCATION in manifest). This may throw on some providers.
    val unredactedUri = try { MediaStore.setRequireOriginal(uri) } catch (_: Exception) { null }
    if (unredactedUri != null) {
        try {
            context.contentResolver.openInputStream(unredactedUri)?.use { stream ->
                return ExifInterface(stream).parseData()
            }
        } catch (_: Exception) { }
    }
    // Fallback: original URI — GPS may still be redacted but date will be present.
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).parseData()
        } ?: ExifData(null, null, null)
    } catch (_: Exception) {
        ExifData(null, null, null)
    }
}

@Composable
actual fun rememberPhotoPicker(onResult: (path: String?, exifLat: Double?, exifLng: Double?, exifDate: LocalDate?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    var pendingExifLat by remember { mutableStateOf<Double?>(null) }
    var pendingExifLng by remember { mutableStateOf<Double?>(null) }
    var pendingExifDate by remember { mutableStateOf<LocalDate?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                UCrop.getOutput(data)?.let { uri ->
                    currentOnResult.value(uri.toString(), pendingExifLat, pendingExifLng, pendingExifDate)
                }
            } ?: currentOnResult.value(null, null, null, null)
        } else currentOnResult.value(null, null, null, null)
        pendingExifLat = null
        pendingExifLng = null
        pendingExifDate = null
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
            val exif = readExif(context, uri)
            pendingExifLat = exif.lat
            pendingExifLng = exif.lng
            pendingExifDate = exif.date
            launchCrop(uri)
        } else {
            currentOnResult.value(null, null, null, null)
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
