package com.travelsouvenirs.main.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.travelsouvenirs.main.data.MagnetDatabase
import com.travelsouvenirs.main.data.MagnetRepository
import kotlinx.coroutines.launch

private const val CLUSTER_ZOOM_THRESHOLD = 13f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onAddClick: () -> Unit,
    onPinClick: (Long) -> Unit
) {
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Magnets") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddClick) {
                Text("Add new magnet")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false)
            ) {
                if (showIndividual) {
                    magnetPins.forEach { pin ->
                        Marker(
                            state = rememberMarkerState(position = pin.position),
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
                            state = rememberMarkerState(position = center),
                            title = group.magnets.first().name,
                            snippet = if (group.magnets.size > 1)
                                "${group.magnets.size} magnets here" else group.magnets.first().placeName,
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
                                        // If spread pins aren't computed yet, fall back to center
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

            if (magnets.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                ) {
                    Text(
                        "No magnets yet.\nTap + to add your first!",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
