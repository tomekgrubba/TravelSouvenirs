package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKAnnotationViewDragStateEnding
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObject

private const val PICKER_ZOOM_DISTANCE = 50_000.0   // metres for "zoom in" flyTo equivalent
private const val PICKER_PAN_DISTANCE  = 500_000.0  // metres – if region is already closer, just pan

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformMapLocationPicker(
    selectedLat: Double?,
    selectedLng: Double?,
    cameraMoveId: Int,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier
) {
    val onLocationPickedState = rememberUpdatedState(onLocationPicked)
    val mapTheme = rememberMapTheme()

    val annotation = remember { MKPointAnnotation() }

    val delegate = remember {
        object : NSObject(), MKMapViewDelegateProtocol {

            override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
                if (viewForAnnotation !== annotation) return null
                val view = (mapView.dequeueReusableAnnotationViewWithIdentifier("picker") as? MKAnnotationView)
                    ?: MKAnnotationView(annotation = viewForAnnotation, reuseIdentifier = "picker")
                view.annotation = viewForAnnotation
                view.draggable = true
                view.canShowCallout = false
                view.image = null
                return view
            }

            override fun mapView(
                mapView: MKMapView,
                annotationView: MKAnnotationView,
                didChangeDragState: platform.MapKit.MKAnnotationViewDragState,
                fromOldState: platform.MapKit.MKAnnotationViewDragState
            ) {
                if (didChangeDragState == MKAnnotationViewDragStateEnding) {
                    annotationView.annotation?.coordinate()?.useContents {
                        onLocationPickedState.value(latitude, longitude)
                    }
                }
            }
        }
    }

    val mapView = remember {
        MKMapView().apply {
            this.delegate = delegate as MKMapViewDelegateProtocol
            val tap = UITapGestureRecognizer().apply {
                addTargetAction(object : NSObject() {
                    @Suppress("unused")
                    fun handleTap(recognizer: UITapGestureRecognizer) {
                        if (recognizer.state != UIGestureRecognizerStateBegan) return
                        val point = recognizer.locationInView(this@apply)
                        val coord = this@apply.convertPoint(point, toCoordinateFromView = this@apply)
                        coord.useContents {
                            annotation.setCoordinate(this)
                            if (!this@apply.annotations.contains(annotation as MKAnnotationProtocol)) {
                                this@apply.addAnnotation(annotation as MKAnnotationProtocol)
                            }
                            onLocationPickedState.value(latitude, longitude)
                        }
                    }
                }, action = platform.objc.sel_registerName("handleTap:"))
            }
            this.addGestureRecognizer(tap)
        }
    }

    // Set initial annotation if a position is already selected
    LaunchedEffect(Unit) {
        if (selectedLat != null && selectedLng != null) {
            annotation.setCoordinate(CLLocationCoordinate2DMake(selectedLat, selectedLng))
            mapView.addAnnotation(annotation as MKAnnotationProtocol)
        }
    }

    // Update annotation position when driven externally (GPS / search), without camera move
    LaunchedEffect(selectedLat, selectedLng) {
        if (selectedLat != null && selectedLng != null) {
            annotation.setCoordinate(CLLocationCoordinate2DMake(selectedLat, selectedLng))
            if (!mapView.annotations.contains(annotation as MKAnnotationProtocol)) {
                mapView.addAnnotation(annotation as MKAnnotationProtocol)
            }
        }
    }

    // Animate camera only on explicit GPS / search events (cameraMoveId increments)
    LaunchedEffect(cameraMoveId) {
        if (selectedLat != null && selectedLng != null) {
            val coord = CLLocationCoordinate2DMake(selectedLat, selectedLng)
            val currentSpanDeg = mapView.region.useContents { span.longitudeDelta }
            // If already zoomed in (span < ~9°), just pan; otherwise zoom in
            val distance = if (currentSpanDeg < 9.0) PICKER_PAN_DISTANCE else PICKER_ZOOM_DISTANCE
            mapView.setRegion(
                MKCoordinateRegionMakeWithDistance(coord, distance, distance),
                animated = true
            )
        }
    }

    LaunchedEffect(mapTheme) {
        mapView.overrideUserInterfaceStyle = if (mapTheme == MapTheme.DARK)
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        else
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
    }

    UIKitView(factory = { mapView }, modifier = modifier)
}
