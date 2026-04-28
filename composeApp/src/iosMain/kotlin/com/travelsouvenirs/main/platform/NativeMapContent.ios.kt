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
import com.travelsouvenirs.main.di.LocalItemRepository
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UIUserInterfaceStyleDark
import platform.UIKit.UIUserInterfaceStyleLight

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun NativeMapsContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit) {
    val mapTheme = rememberMapTheme()
    val repository = LocalItemRepository.current
    val locationService = LocalLocationService.current
    val viewModel: MapViewModel = viewModel { MapViewModel(repository) }

    if (viewModel.lastProvider != MapProviderType.NATIVE.name) {
        viewModel.lastProvider = MapProviderType.NATIVE.name
        viewModel.initialCameraSet = false
        viewModel.nativeMapView = null
    }

    val items by viewModel.items.collectAsState()

    val mapView = remember {
        (viewModel.nativeMapView as? MKMapView) ?: MKMapView().also { viewModel.nativeMapView = it }
    }

    LaunchedEffect(mapTheme) {
        mapView.overrideUserInterfaceStyle = if (mapTheme == MapTheme.DARK) UIUserInterfaceStyleDark else UIUserInterfaceStyleLight
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

    LaunchedEffect(items) {
        mapView.removeAnnotations(mapView.annotations)
        items.forEach { item ->
            val pin = MKPointAnnotation()
            pin.setCoordinate(CLLocationCoordinate2DMake(item.latitude, item.longitude))
            pin.setTitle(item.name)
            pin.setSubtitle(item.placeName)
            mapView.addAnnotation(pin as MKAnnotationProtocol)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        UIKitView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        if (items.isEmpty()) {
            // Empty state card shown when no items exist
            // Added clickable modifier so tapping the overlay opens the add item screen
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .clickable { onAddClick() }
            ) {
                Text(
                    stringResource(Res.string.empty_state_no_items),
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
