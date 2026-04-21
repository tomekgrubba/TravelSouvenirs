package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Small static map centered on [latitude]/[longitude] with a [label] annotation. */
@Composable
expect fun PlatformMapPreview(
    latitude: Double,
    longitude: Double,
    label: String,
    modifier: Modifier = Modifier
)
