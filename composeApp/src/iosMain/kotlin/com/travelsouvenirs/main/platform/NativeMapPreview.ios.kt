package com.travelsouvenirs.main.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIUserInterfaceStyle
import kotlinx.cinterop.ObjCAction
import platform.darwin.NSObject
import platform.objc.sel_registerName

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun NativeMapPreview(latitude: Double, longitude: Double, label: String, modifier: Modifier) {
    val showZoomButtons = remember { mutableStateOf(false) }
    val zoomDistance = remember { mutableStateOf(500_000.0) }

    val mapView = remember {
        MKMapView().apply {
            setScrollEnabled(false)
            setZoomEnabled(false)
            setPitchEnabled(false)
            setRotateEnabled(false)
            val coord = CLLocationCoordinate2DMake(latitude, longitude)
            setRegion(MKCoordinateRegionMakeWithDistance(coord, 500_000.0, 500_000.0), animated = false)
            val pin = MKPointAnnotation()
            pin.setCoordinate(coord)
            pin.setTitle(label)
            addAnnotation(pin as MKAnnotationProtocol)
            val tap = UITapGestureRecognizer().apply {
                addTarget(object : NSObject() {
                    @ObjCAction
                    fun handleTap(r: UITapGestureRecognizer) {
                        showZoomButtons.value = true
                    }
                }, action = sel_registerName("handleTap:"))
            }
            addGestureRecognizer(tap)
        }
    }

    LaunchedEffect(Unit) {
        mapView.overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleLight
    }

    Box(modifier = modifier) {
        UIKitView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (showZoomButtons.value) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        val d = maxOf(zoomDistance.value / 2.0, 200.0)
                        zoomDistance.value = d
                        mapView.setRegion(
                            MKCoordinateRegionMakeWithDistance(
                                CLLocationCoordinate2DMake(latitude, longitude), d, d
                            ),
                            animated = true
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) { Text("+") }
                SmallFloatingActionButton(
                    onClick = {
                        val d = minOf(zoomDistance.value * 2.0, 20_000_000.0)
                        zoomDistance.value = d
                        mapView.setRegion(
                            MKCoordinateRegionMakeWithDistance(
                                CLLocationCoordinate2DMake(latitude, longitude), d, d
                            ),
                            animated = true
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) { Text("−") }
            }
        }
    }
}
