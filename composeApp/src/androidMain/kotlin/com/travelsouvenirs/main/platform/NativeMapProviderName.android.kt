package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** Returns "Google Maps" when Play Services is available, otherwise "System Maps". */
@Composable
actual fun nativeMapProviderName(): String {
    val context = LocalContext.current
    val isGoogleMapsAvailable = remember(context) {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    return if (isGoogleMapsAvailable) "Google Maps" else "System Maps"
}
