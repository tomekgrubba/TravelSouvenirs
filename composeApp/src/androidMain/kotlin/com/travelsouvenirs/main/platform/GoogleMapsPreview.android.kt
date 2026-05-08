package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
internal fun GoogleMapsPreview(latitude: Double, longitude: Double, label: String, modifier: Modifier) {
    var showZoomControls by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 5f)
    }
    GoogleMap(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    showZoomControls = true
                }
            }
        },
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            minZoomPreference = 2f,
            mapStyleOptions = MapStyleOptions(GOOGLE_MAPS_LIGHT_STYLE_POLAROID)
        ),
        uiSettings = MapUiSettings(
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            zoomControlsEnabled = showZoomControls,
            rotationGesturesEnabled = false
        ),
        onMapClick = { showZoomControls = true }
    ) {
        Marker(
            state = MarkerState(LatLng(latitude, longitude)),
            title = label
        )
    }
}
