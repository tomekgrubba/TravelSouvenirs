package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Minimum zoom level shown on the location picker map. */
internal const val MAP_ZOOM_MIN = 2

/** Zoom level applied when flying to a GPS or search result. */
internal const val MAP_ZOOM_LOCATION = 10

/**
 * Platform-specific interactive map that lets the user pick a geographic location.
 *
 * @param selectedLat Latitude of the current pin, or null if no pin has been placed.
 * @param selectedLng Longitude of the current pin, or null if no pin has been placed.
 * @param cameraMoveId Incrementing token that triggers a camera animation to [selectedLat]/[selectedLng].
 *   Only GPS and search events should increment this; tap and drag must not, to avoid camera snap-back.
 * @param onLocationPicked Called when the user taps the map, confirms a drag, or the pin is moved programmatically.
 */
@Composable
expect fun PlatformMapLocationPicker(
    selectedLat: Double?,
    selectedLng: Double?,
    cameraMoveId: Int,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
)
