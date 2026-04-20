package com.travelsouvenirs.main.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.travelsouvenirs.main.data.MagnetDatabase
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.location.LocationHelper
import kotlinx.coroutines.launch

private const val CLUSTER_ZOOM_THRESHOLD = 13f
private const val LOCATION_ZOOM = 4f

/** Full-screen map with photo pin markers, zoom-based clustering, and a "my location" button. */
@Composable
fun MapContent(onPinClick: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = remember {
        MagnetRepository(MagnetDatabase.getDatabase(context).magnetDao())
    }
    val viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory(repository))
    val magnets by viewModel.magnets.collectAsState()
    val magnetPins by viewModel.magnetPins.collectAsState()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
    }
    val zoom = cameraPositionState.position.zoom
    val showIndividual = zoom >= CLUSTER_ZOOM_THRESHOLD

    val magnetGroups = remember(magnets, zoom) {
        if (showIndividual) emptyList()
        else MapViewModel.groupByZoom(magnets, zoom)
    }

    val individualIcons = rememberIndividualIcons(magnetPins)
    val groupIcons = rememberGroupIcons(magnetGroups)

    val scope = rememberCoroutineScope()

    val locationHelper = remember { LocationHelper(context) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            hasLocationPermission = true
            scope.launch {
                try {
                    locationHelper.getCurrentLocation()?.let { (lat, lng) ->
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), LOCATION_ZOOM), 600
                        )
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun jumpToMyLocation() {
        if (hasLocationPermission) {
            scope.launch {
                try {
                    locationHelper.getCurrentLocation()?.let { (lat, lng) ->
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), LOCATION_ZOOM), 600
                        )
                    }
                } catch (_: Exception) { }
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var initialZoomDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialZoomDone) {
            initialZoomDone = true
            try {
                val location = if (ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) locationHelper.getCurrentLocation() else null

                val update = if (location != null) {
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.first, location.second), LOCATION_ZOOM
                    )
                } else if (magnets.isNotEmpty()) {
                    val bounds = LatLngBounds.Builder().apply {
                        magnets.forEach { include(LatLng(it.latitude, it.longitude)) }
                    }.build()
                    CameraUpdateFactory.newLatLngBounds(bounds, 120)
                } else null

                update?.let { cameraPositionState.animate(it, 800) }
            } catch (_: Exception) { }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        @Suppress("MissingPermission")
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            if (showIndividual) {
                magnetPins.forEach { pin ->
                    Marker(
                        state = rememberUpdatedMarkerState(position = pin.position),
                        title = pin.magnet.name,
                        snippet = pin.magnet.placeName,
                        icon = individualIcons[pin.magnet.id],
                        onClick = { onPinClick(pin.magnet.id); true }
                    )
                }
            } else {
                magnetGroups.forEachIndexed { idx, group ->
                    val center = LatLng(group.centerLat, group.centerLng)
                    Marker(
                        state = rememberUpdatedMarkerState(position = center),
                        title = group.magnets.first().name,
                        snippet = if (group.magnets.size > 1)
                            "${group.magnets.size} items here" else group.magnets.first().placeName,
                        icon = groupIcons[idx],
                        onClick = {
                            if (group.magnets.size == 1) {
                                onPinClick(group.magnets.first().id)
                            } else {
                                val groupIds = group.magnets.map { it.id }.toSet()
                                val groupPins = magnetPins.filter { it.magnet.id in groupIds }
                                scope.launch {
                                    val boundsBuilder = LatLngBounds.Builder()
                                    groupPins.forEach { boundsBuilder.include(it.position) }
                                    if (groupPins.isEmpty()) boundsBuilder.include(center)
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 200),
                                        600
                                    )
                                }
                            }
                            true
                        }
                    )
                }
            }
        }

        SmallFloatingActionButton(
            onClick = { jumpToMyLocation() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "My location")
        }

        if (magnets.isEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            ) {
                Text(
                    "No items yet.\nTap + to add your first!",
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
