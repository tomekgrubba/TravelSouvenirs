package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformMapPreview(latitude: Double, longitude: Double, label: String, modifier: Modifier) {
    when (rememberMapProvider()) {
        MapProviderType.NATIVE -> GoogleMapsPreview(latitude, longitude, label, modifier)
        MapProviderType.OPEN_STREET_MAP -> OsmMapPreview(latitude, longitude, label, modifier)
    }
}
