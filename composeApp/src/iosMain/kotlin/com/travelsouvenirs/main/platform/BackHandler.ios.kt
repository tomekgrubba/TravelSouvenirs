package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable

/** iOS: no-op — back navigation is handled by the native swipe gesture. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op on iOS
}
