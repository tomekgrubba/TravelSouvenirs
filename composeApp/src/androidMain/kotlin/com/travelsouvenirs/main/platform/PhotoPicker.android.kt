package com.travelsouvenirs.main.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.travelsouvenirs.main.image.ImageStorageHelper
import com.travelsouvenirs.main.ui.add.CropActivity
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

private const val TAG = "TS_PhotoPicker"

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

/**
 * Copies a content URI (which may be cloud-backed or temporarily-scoped) to a local temp file
 * so that UCrop — running in a separate Activity — can reliably open it.
 *
 * When the source is a cloud-only Google Photos item, the first `openInputStream` sometimes
 * returns null or throws before the download completes. Opening an asset file descriptor tends
 * to prod the provider into fetching the bytes, so we retry once after that warm-up.
 */
private fun copyUriToTemp(context: Context, uri: Uri): Uri? {
    tryCopy(context, uri)?.let { return it }
    try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.close()
    } catch (_: Exception) { }
    return tryCopy(context, uri)
}

private fun tryCopy(context: Context, uri: Uri): Uri? {
    return try {
        val (tempUri, tempFile) = ImageStorageHelper.createTempUri(context)
        val stream = context.contentResolver.openInputStream(uri)
        if (stream == null) {
            Log.w(TAG, "tryCopy: openInputStream returned null for $uri")
            return null
        }
        stream.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        Log.d(TAG, "tryCopy: success, ${tempFile.length()} bytes -> $tempUri")
        tempUri
    } catch (e: Exception) {
        Log.w(TAG, "tryCopy: exception copying $uri", e)
        null
    }
}

@Composable
actual fun rememberPhotoPicker(onResult: (path: String?, exifLat: Double?, exifLng: Double?, exifDate: LocalDate?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
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

    // PickVisualMedia uses the OS System Photo Picker on Android 13+ (MediaStore-backed, does
    // not route through Google Photos directly). It returns a null URI both for genuine
    // cancels and for silent failures (e.g. cloud-only or edited Google Photos items it can't
    // resolve after the user tapped one). We can't tell the two apart from the API, but if
    // the picker was open long enough to browse and tap (~2s+), we assume a failure and show
    // a hint. Fast dismissals stay silent to avoid nagging on intentional cancels.
    //
    // We render an AlertDialog rather than a Toast because Toasts from apps that just lost
    // foreground (e.g. because the user immediately reopened the picker) are silently killed
    // by NotificationService on Android 12+.
    var launchedAtMillis by remember { mutableStateOf(0L) }
    var showFailureDialog by remember { mutableStateOf(false) }

    if (showFailureDialog) {
        AlertDialog(
            onDismissRequest = { showFailureDialog = false },
            title = { Text("Photo couldn't be loaded") },
            text = {
                Text(
                    "This photo is stored in the cloud or hasn't finished syncing. " +
                        "Open it in Google Photos first (to trigger a download), then reselect it here."
                )
            },
            confirmButton = {
                TextButton(onClick = { showFailureDialog = false }) { Text("OK") }
            }
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val elapsed = System.currentTimeMillis() - launchedAtMillis
        Log.d(TAG, "PickVisualMedia callback: uri=$uri elapsedMs=$elapsed")
        if (uri != null) {
            // Read EXIF first (while we still hold the content URI permission).
            val exif = readExif(context, uri)
            pendingExifLat = exif.lat
            pendingExifLng = exif.lng
            pendingExifDate = exif.date
            Log.d(TAG, "EXIF: lat=${exif.lat} lng=${exif.lng} date=${exif.date}")
            // Copy to a local temp file so UCrop (separate Activity) gets a stable URI.
            scope.launch {
                val localUri = withContext(Dispatchers.IO) { copyUriToTemp(context, uri) }
                if (localUri != null) {
                    launchCrop(localUri)
                } else {
                    Log.e(TAG, "Copy failed after retry — showing dialog")
                    showFailureDialog = true
                    pendingExifLat = null
                    pendingExifLng = null
                    pendingExifDate = null
                    currentOnResult.value(null, null, null, null)
                }
            }
        } else {
            Log.w(TAG, "PickVisualMedia returned null URI after ${elapsed}ms")
            if (elapsed > 2000) {
                showFailureDialog = true
            }
            currentOnResult.value(null, null, null, null)
        }
    }

    return remember {
        {
            Log.d(TAG, "Launching PickVisualMedia")
            launchedAtMillis = System.currentTimeMillis()
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
}

@Composable
actual fun rememberCameraCapture(onResult: (path: String?, exifLat: Double?, exifLng: Double?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
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

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraOutputUri?.let { uri ->
                val exif = readExif(context, uri)
                pendingExifLat = exif.lat
                pendingExifLng = exif.lng
                launchCrop(uri)
            }
        } else currentOnResult.value(null, null, null)
        cameraOutputUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, _) = ImageStorageHelper.createTempUri(context)
            cameraOutputUri = uri
            takePictureLauncher.launch(uri)
        } else currentOnResult.value(null, null, null)
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
