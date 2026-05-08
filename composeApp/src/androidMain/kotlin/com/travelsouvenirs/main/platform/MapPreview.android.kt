package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformMapPreview(latitude: Double, longitude: Double, label: String, modifier: Modifier) {
    GoogleMapsPreview(latitude, longitude, label, modifier)
}
