package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformMapContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit) {
    NativeMapsContent(onPinClick, onAddClick)
}
