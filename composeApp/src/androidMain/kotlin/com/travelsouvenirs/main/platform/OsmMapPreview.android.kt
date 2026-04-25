package com.travelsouvenirs.main.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun OsmMapPreview(latitude: Double, longitude: Double, label: String, modifier: Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { mutableStateOf<MapView?>(null) }

    // Properly handle the MapView lifecycle (pause/resume/detach) to prevent 
    // the map from continuing to fetch tiles and leak memory in the background.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.value?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.value?.onDetach()
        }
    }

    // Outer Box lets the transparent Compose overlay sit on top of the AndroidView so that
    // all touch events are consumed before osmdroid sees them — isClickable/isFocusable
    // alone are not enough to fully block scroll/zoom gestures on MapView.
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(false)
                    setBuiltInZoomControls(false)
                    minZoomLevel = 3.0
                    isVerticalMapRepetitionEnabled = false
                    setScrollableAreaLimitDouble(org.osmdroid.util.BoundingBox(85.0, 180.0, -85.0, -180.0))
                    isClickable = false
                    isFocusable = false
                    controller.setZoom(8.0)
                    controller.setCenter(GeoPoint(latitude, longitude))

                    val pinW = 32
                    val pinH = 48
                    val bmp = Bitmap.createBitmap(pinW, pinH, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).apply {
                        val cx = pinW / 2f
                        val cr = pinW / 2f - 2f
                        val blue = 0xFF1565C0.toInt()
                        // Triangle stem pointing down
                        drawPath(Path().apply {
                            moveTo(cx - cr * 0.45f, cr * 1.8f)
                            lineTo(cx, pinH - 2f)
                            lineTo(cx + cr * 0.45f, cr * 1.8f)
                            close()
                        }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue })
                        // Circle head
                        drawCircle(cx, cr + 2f, cr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue })
                        // White ring
                        drawCircle(cx, cr + 2f, cr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 3f
                        })
                        // White inner dot
                        drawCircle(cx, cr + 2f, cr * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                    }

                    val pin = Marker(this, null)
                    pin.position = GeoPoint(latitude, longitude)
                    pin.title = label
                    pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    pin.icon = BitmapDrawable(ctx.resources, bmp)
                    overlays.add(pin)
                    mapView.value = this
                }
            },
            modifier = Modifier.matchParentSize()
        )
        // Transparent overlay that absorbs all pointer events before they reach the MapView
        Box(modifier = Modifier.matchParentSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(pass = PointerEventPass.Initial)
                        .changes.forEach { it.consume() }
                }
            }
        })
    }
}
