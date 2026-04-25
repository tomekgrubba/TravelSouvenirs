package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
internal fun OsmMapPreview(latitude: Double, longitude: Double, label: String, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)
                isEnabled = false
                controller.setZoom(5.0)
                controller.setCenter(GeoPoint(latitude, longitude))
                val pin = Marker(this)
                pin.position = GeoPoint(latitude, longitude)
                pin.title = label
                pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                pin.setInfoWindow(null)
                overlays.add(pin)
            }
        },
        modifier = modifier
    )
}
