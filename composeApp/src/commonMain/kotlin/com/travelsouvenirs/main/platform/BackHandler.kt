package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable

/** Intercepts the platform back gesture/button when [enabled]; no-op on platforms without a back key. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
