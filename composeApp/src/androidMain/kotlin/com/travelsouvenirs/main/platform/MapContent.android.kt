package com.travelsouvenirs.main.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
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
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.di.LocalMagnetRepository
import com.travelsouvenirs.main.ui.map.MapViewModel
import com.travelsouvenirs.main.ui.map.rememberGroupIcons
import com.travelsouvenirs.main.ui.map.rememberIndividualIcons
import kotlinx.coroutines.launch

private const val CLUSTER_ZOOM_THRESHOLD = 13f
private const val LOCATION_ZOOM = 4f

private data class EdgeCounts(val top: Int, val bottom: Int, val left: Int, val right: Int)

/** Full-screen Google Maps implementation with photo pins, zoom clustering, location button, and category filter. */
@Composable
actual fun PlatformMapContent(onPinClick: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = LocalMagnetRepository.current
    val locationService = LocalLocationService.current
    val categoryFilter = LocalCategoryFilter.current

    val viewModel: MapViewModel = viewModel { MapViewModel(repository) }
    val allMagnets by viewModel.magnets.collectAsState()
    val allPins by viewModel.magnetPins.collectAsState()
    val selectedCategories by categoryFilter.selectedCategories.collectAsState()

    // Apply category filter at screen level
    val magnets = remember(allMagnets, selectedCategories) {
        allMagnets.filter { m ->
            m.category in selectedCategories || m.category !in categoryFilter.allCategoriesSet
        }
    }
    val magnetPins = remember(allPins, selectedCategories) {
        allPins.filter { pin ->
            pin.magnet.category in selectedCategories || pin.magnet.category !in categoryFilter.allCategoriesSet
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
    }
    val zoom = cameraPositionState.position.zoom
    val showIndividual = zoom >= CLUSTER_ZOOM_THRESHOLD

    val visibleBounds = remember(cameraPositionState.position) {
        cameraPositionState.projection?.visibleRegion?.latLngBounds
    }
    val offScreen = remember(magnets, visibleBounds) {
        val bounds = visibleBounds
        if (bounds == null) return@remember EdgeCounts(0, 0, 0, 0)
        val sw = bounds.southwest
        val ne = bounds.northeast
        val centerLat = (sw.latitude + ne.latitude) / 2.0
        val centerLng = (sw.longitude + ne.longitude) / 2.0
        val halfLat = (ne.latitude - sw.latitude) / 2.0
        val halfLng = (ne.longitude - sw.longitude) / 2.0
        if (halfLat <= 0 || halfLng <= 0) return@remember EdgeCounts(0, 0, 0, 0)
        var top = 0; var bottom = 0; var left = 0; var right = 0
        magnets.filter { (it.latitude != 0.0 || it.longitude != 0.0) &&
            (it.latitude > ne.latitude || it.latitude < sw.latitude ||
             it.longitude > ne.longitude || it.longitude < sw.longitude)
        }.forEach { m ->
            val normLat = (m.latitude - centerLat) / halfLat
            val normLng = (m.longitude - centerLng) / halfLng
            if (abs(normLat) >= abs(normLng)) {
                if (normLat > 0) top++ else bottom++
            } else {
                if (normLng > 0) right++ else left++
            }
        }
        EdgeCounts(top, bottom, left, right)
    }

    val magnetGroups = remember(magnets, zoom) {
        if (showIndividual) emptyList()
        else MapViewModel.groupByZoom(magnets, zoom)
    }

    val individualIcons = rememberIndividualIcons(magnetPins)
    val groupIcons = rememberGroupIcons(magnetGroups)

    val scope = rememberCoroutineScope()

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
                    locationService.getCurrentLocation()?.let { loc ->
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(loc.lat, loc.lng), LOCATION_ZOOM), 600
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
                    locationService.getCurrentLocation()?.let { loc ->
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(loc.lat, loc.lng), LOCATION_ZOOM), 600
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
                val location = if (hasLocationPermission) locationService.getCurrentLocation() else null
                val update = if (location != null) {
                    CameraUpdateFactory.newLatLngZoom(LatLng(location.lat, location.lng), LOCATION_ZOOM)
                } else if (allMagnets.isNotEmpty()) {
                    val bounds = LatLngBounds.Builder().apply {
                        allMagnets.forEach { include(LatLng(it.latitude, it.longitude)) }
                    }.build()
                    CameraUpdateFactory.newLatLngBounds(bounds, 120)
                } else null
                update?.let { cameraPositionState.animate(it, 800) }
            } catch (_: Exception) { }
        }
    }

    var showFilterMenu by remember { mutableStateOf(false) }
    val isFilterActive = selectedCategories != categoryFilter.allCategoriesSet

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
                        state = rememberUpdatedMarkerState(
                            position = LatLng(pin.position.lat, pin.position.lng)
                        ),
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
                                    groupPins.forEach { boundsBuilder.include(LatLng(it.position.lat, it.position.lng)) }
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

        // Overlay buttons — top end
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(onClick = { jumpToMyLocation() }) {
                Icon(Icons.Default.MyLocation, contentDescription = "My location")
            }

            Box {
                SmallFloatingActionButton(
                    onClick = { showFilterMenu = true },
                    containerColor = if (isFilterActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isFilterActive)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filter by category")
                }

                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                    modifier = Modifier.width(220.dp)
                ) {
                    Text(
                        text = "Filter by category",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    categoryFilter.availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            leadingIcon = {
                                Checkbox(
                                    checked = category in selectedCategories,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = { categoryFilter.toggleCategoryFilter(category) }
                        )
                    }
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
                    if (allMagnets.isEmpty())
                        "No items yet.\nTap + to add your first!"
                    else
                        "No items match\nthe selected categories.",
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (offScreen.top > 0)
            EdgeIndicator("▲", offScreen.top, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        if (offScreen.bottom > 0)
            EdgeIndicator("▼", offScreen.bottom, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        if (offScreen.left > 0)
            EdgeIndicator("◀", offScreen.left, Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
        if (offScreen.right > 0)
            EdgeIndicator("▶", offScreen.right, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
    }
}

@Composable
private fun EdgeIndicator(arrow: String, count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "$arrow  $count",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.extraSmall
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
