package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformMapContent(onPinClick: (Long) -> Unit) {
    when (rememberMapProvider()) {
        MapProviderType.NATIVE -> GoogleMapsContent(onPinClick)
        MapProviderType.OPEN_STREET_MAP -> OsmMapContent(onPinClick)
    }
}
