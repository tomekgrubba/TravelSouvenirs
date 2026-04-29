package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

/**
 * Returns a launcher that opens the system photo gallery.
 * [onResult] receives the internal-storage path and, when available, GPS coordinates and the
 * capture date extracted from the original image EXIF data before cropping strips them.
 */
@Composable
expect fun rememberPhotoPicker(onResult: (path: String?, exifLat: Double?, exifLng: Double?, exifDate: LocalDate?) -> Unit): () -> Unit

/** Returns a launcher that opens the camera; result is the internal-storage path after cropping. */
@Composable
expect fun rememberCameraCapture(onResult: (String?) -> Unit): () -> Unit

/** Returns a launcher that requests fine-location permission, then calls [onGranted] if approved. */
@Composable
expect fun rememberLocationPermissionLauncher(onGranted: () -> Unit): () -> Unit
