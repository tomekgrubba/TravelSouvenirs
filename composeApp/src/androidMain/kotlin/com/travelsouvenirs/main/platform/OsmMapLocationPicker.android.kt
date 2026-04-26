package com.travelsouvenirs.main.platform

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

/** Location picker backed by OSMDroid. Clips to its layout bounds to match the Google Maps variant. */
@Composable
internal fun OsmMapLocationPicker(
    selectedLat: Double?,
    selectedLng: Double?,
    cameraMoveId: Int,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onLocationPickedState = rememberUpdatedState(onLocationPicked)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(true)
            minZoomLevel = MAP_ZOOM_MIN.toDouble()
            isVerticalMapRepetitionEnabled = false
            setScrollableAreaLimitDouble(BoundingBox(85.0, 180.0, -85.0, -180.0))
            val startZoom = if (selectedLat != null) MAP_ZOOM_LOCATION.toDouble() else MAP_ZOOM_MIN.toDouble()
            controller.setZoom(startZoom)
            controller.setCenter(GeoPoint(selectedLat ?: 20.0, selectedLng ?: 0.0))
        }
    }

    val tapOverlay = remember {
        object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val point = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                onLocationPickedState.value(point.latitude, point.longitude)
                return true
            }
        }
    }

    DisposableEffect(mapView) {
        mapView.overlays.add(0, tapOverlay)
        onDispose { mapView.overlays.remove(tapOverlay) }
    }

    // Rebuild the draggable marker whenever the pin position changes
    LaunchedEffect(selectedLat, selectedLng) {
        val newOverlays = mapView.overlays.filter { it !is Marker }.toMutableList()
        if (selectedLat != null && selectedLng != null) {
            val marker = Marker(mapView, null).apply {
                position = GeoPoint(selectedLat, selectedLng)
                setInfoWindow(null)
                setDraggable(true)
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDragStart(marker: Marker) {}
                    override fun onMarkerDrag(marker: Marker) {}
                    override fun onMarkerDragEnd(marker: Marker) {
                        onLocationPickedState.value(marker.position.latitude, marker.position.longitude)
                    }
                })
            }
            newOverlays.add(marker)
        }
        mapView.overlays.clear()
        mapView.overlays.addAll(newOverlays)
        mapView.invalidate()
    }

    // Animate camera ONLY on explicit GPS / search events (cameraMoveId increments)
    LaunchedEffect(cameraMoveId) {
        if (selectedLat != null && selectedLng != null) {
            val targetZoom = maxOf(mapView.zoomLevelDouble, MAP_ZOOM_LOCATION.toDouble())
            mapView.controller.animateTo(GeoPoint(selectedLat, selectedLng), targetZoom, 600L)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.clipToBounds())
}
