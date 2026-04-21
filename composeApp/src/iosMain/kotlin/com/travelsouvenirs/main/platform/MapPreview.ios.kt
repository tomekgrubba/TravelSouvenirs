package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.CoreLocation.CLLocationCoordinate2DMake

/** iOS static map preview using MKMapView with a single annotation; interactions disabled. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformMapPreview(
    latitude: Double,
    longitude: Double,
    label: String,
    modifier: Modifier
) {
    val mapView = remember {
        MKMapView().apply {
            scrollEnabled = false
            zoomEnabled = false
            pitchEnabled = false
            rotateEnabled = false
            val coord = CLLocationCoordinate2DMake(latitude, longitude)
            val region = MKCoordinateRegionMakeWithDistance(coord, 500_000.0, 500_000.0)
            setRegion(region, animated = false)
            val pin = MKPointAnnotation()
            pin.coordinate = coord
            pin.title = label
            addAnnotation(pin as MKAnnotationProtocol)
        }
    }
    UIKitView(
        factory = { mapView },
        modifier = modifier
    )
}
