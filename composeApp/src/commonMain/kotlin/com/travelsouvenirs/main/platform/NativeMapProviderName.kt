package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable

/**
 * Returns a display name for the native map provider installed on this device
 * (e.g. "Google Maps" on Android with Play Services, "Apple Maps" on iOS).
 */
@Composable
expect fun nativeMapProviderName(): String
