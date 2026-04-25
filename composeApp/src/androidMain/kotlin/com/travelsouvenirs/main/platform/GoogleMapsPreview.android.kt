package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
internal fun GoogleMapsPreview(latitude: Double, longitude: Double, label: String, modifier: Modifier) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 5f)
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = com.google.maps.android.compose.MapProperties(
            minZoomPreference = 2f
        ),
        uiSettings = MapUiSettings(
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            zoomControlsEnabled = false,
            rotationGesturesEnabled = false
        )
    ) {
        Marker(
            state = MarkerState(LatLng(latitude, longitude)),
            title = label
        )
    }
}
