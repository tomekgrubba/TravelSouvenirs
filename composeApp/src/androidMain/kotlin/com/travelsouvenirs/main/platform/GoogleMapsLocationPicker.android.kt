package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.DragState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private const val GOOGLE_MAPS_DARK_STYLE = """[{"elementType":"geometry","stylers":[{"color":"#242f3e"}]},{"elementType":"labels.text.fill","stylers":[{"color":"#746855"}]},{"elementType":"labels.text.stroke","stylers":[{"color":"#242f3e"}]},{"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},{"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},{"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#263c3f"}]},{"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#6b9a76"}]},{"featureType":"road","elementType":"geometry","stylers":[{"color":"#38414e"}]},{"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#212a37"}]},{"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#9ca5b3"}]},{"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#746855"}]},{"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#1f2835"}]},{"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#f3d19c"}]},{"featureType":"transit","elementType":"geometry","stylers":[{"color":"#2f3948"}]},{"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#17263c"}]},{"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#515c6d"}]},{"featureType":"water","elementType":"labels.text.stroke","stylers":[{"color":"#17263c"}]}]"""

/** Location picker backed by Google Maps Compose. */
@Composable
internal fun GoogleMapsLocationPicker(
    selectedLat: Double?,
    selectedLng: Double?,
    cameraMoveId: Int,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialLatLng = if (selectedLat != null && selectedLng != null)
        LatLng(selectedLat, selectedLng) else LatLng(20.0, 0.0)
    val initialZoom = if (selectedLat != null) MAP_ZOOM_LOCATION.toFloat() else MAP_ZOOM_MIN.toFloat()

    val mapTheme = rememberMapTheme()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, initialZoom)
    }

    // rememberMarkerState (not rememberUpdatedMarkerState) so dragState is observable
    val markerState = rememberMarkerState(position = initialLatLng)

    val onLocationPickedState = rememberUpdatedState(onLocationPicked)

    // Keep marker in sync when an external change (GPS / search) flows in
    LaunchedEffect(selectedLat, selectedLng) {
        if (selectedLat != null && selectedLng != null)
            markerState.position = LatLng(selectedLat, selectedLng)
    }

    // Animate camera ONLY on explicit GPS / search events (cameraMoveId increments)
    LaunchedEffect(cameraMoveId) {
        if (selectedLat != null && selectedLng != null) {
            val targetZoom = maxOf(cameraPositionState.position.zoom, MAP_ZOOM_LOCATION.toFloat())
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(selectedLat, selectedLng), targetZoom),
                durationMs = 600
            )
        }
    }

    // Report drag end to ViewModel — does NOT trigger camera animation
    LaunchedEffect(markerState.dragState) {
        if (markerState.dragState == DragState.END) {
            val pos = markerState.position
            onLocationPickedState.value(pos.latitude, pos.longitude)
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            minZoomPreference = 2f,
            mapStyleOptions = if (mapTheme == MapTheme.DARK) MapStyleOptions(GOOGLE_MAPS_DARK_STYLE) else null
        ),
        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false),
        onMapClick = { latLng -> onLocationPicked(latLng.latitude, latLng.longitude) }
    ) {
        if (selectedLat != null && selectedLng != null) {
            Marker(state = markerState, draggable = true)
        }
    }
}
