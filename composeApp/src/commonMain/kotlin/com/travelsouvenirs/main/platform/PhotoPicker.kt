package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable

/** Returns a launcher that opens the system photo gallery; result is the internal-storage path. */
@Composable
expect fun rememberPhotoPicker(onResult: (String?) -> Unit): () -> Unit

/** Returns a launcher that opens the camera; result is the internal-storage path after cropping. */
@Composable
expect fun rememberCameraCapture(onResult: (String?) -> Unit): () -> Unit

/** Returns a launcher that requests fine-location permission, then calls [onGranted] if approved. */
@Composable
expect fun rememberLocationPermissionLauncher(onGranted: () -> Unit): () -> Unit
