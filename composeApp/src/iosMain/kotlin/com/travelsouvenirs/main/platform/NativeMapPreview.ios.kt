package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UIUserInterfaceStyle

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun NativeMapPreview(latitude: Double, longitude: Double, label: String, modifier: Modifier) {
    val mapTheme = rememberMapTheme()
    val mapView = remember {
        MKMapView().apply {
            setScrollEnabled(false)
            setZoomEnabled(false)
            setPitchEnabled(false)
            setRotateEnabled(false)
            val coord = CLLocationCoordinate2DMake(latitude, longitude)
            val region = MKCoordinateRegionMakeWithDistance(coord, 500_000.0, 500_000.0)
            setRegion(region, animated = false)
            val pin = MKPointAnnotation()
            pin.setCoordinate(coord)
            pin.setTitle(label)
            addAnnotation(pin as MKAnnotationProtocol)
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
