package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.di.LocalMagnetRepository
import com.travelsouvenirs.main.ui.map.MapViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation

/** iOS full-screen map using MKMapView with item annotations. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformMapContent(onPinClick: (Long) -> Unit) {
    val repository = LocalMagnetRepository.current
    val locationService = LocalLocationService.current
    val viewModel: MapViewModel = viewModel { MapViewModel(repository) }
    val magnets by viewModel.magnets.collectAsState()

    val mapView = remember {
        (viewModel.nativeMapView as? MKMapView) ?: MKMapView().also { viewModel.nativeMapView = it }
    }

    LaunchedEffect(Unit) {
        if (!viewModel.initialCameraSet) {
            viewModel.initialCameraSet = true
            val loc = locationService.getCurrentLocation()
            if (loc != null) {
                val coord = CLLocationCoordinate2DMake(loc.lat, loc.lng)
                val region = MKCoordinateRegionMakeWithDistance(coord, 5_000_000.0, 5_000_000.0)
                mapView.setRegion(region, animated = false)
            }
        }
    }

    LaunchedEffect(magnets) {
        mapView.removeAnnotations(mapView.annotations)
        magnets.forEach { magnet ->
            val pin = MKPointAnnotation()
            pin.coordinate = CLLocationCoordinate2DMake(magnet.latitude, magnet.longitude)
            pin.title = magnet.name
            pin.subtitle = magnet.placeName
            mapView.addAnnotation(pin as MKAnnotationProtocol)
        }
    }

    UIKitView(
        factory = { mapView },
        modifier = Modifier
    )
}
