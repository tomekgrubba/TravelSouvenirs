package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable

/** Full-screen interactive map showing item pins; platform actual handles map SDK and location. */
@Composable
expect fun PlatformMapContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit)
