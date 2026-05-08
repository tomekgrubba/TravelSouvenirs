package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformMapPreview(latitude: Double, longitude: Double, label: String, modifier: Modifier) {
    NativeMapPreview(latitude, longitude, label, modifier)
}
