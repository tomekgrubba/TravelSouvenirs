package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformMapLocationPicker(
    selectedLat: Double?,
    selectedLng: Double?,
    cameraMoveId: Int,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier
) {
    when (rememberMapProvider()) {
        MapProviderType.NATIVE -> GoogleMapsLocationPicker(selectedLat, selectedLng, cameraMoveId, onLocationPicked, modifier)
        MapProviderType.OPEN_STREET_MAP -> OsmMapLocationPicker(selectedLat, selectedLng, cameraMoveId, onLocationPicked, modifier)
    }
}
