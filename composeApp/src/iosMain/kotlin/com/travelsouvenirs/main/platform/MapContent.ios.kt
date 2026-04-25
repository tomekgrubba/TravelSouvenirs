package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformMapContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit) {
    when (rememberMapProvider()) {
        MapProviderType.NATIVE -> NativeMapsContent(onPinClick, onAddClick)
        MapProviderType.OPEN_STREET_MAP -> OsmMapContent(onPinClick, onAddClick)
    }
}
