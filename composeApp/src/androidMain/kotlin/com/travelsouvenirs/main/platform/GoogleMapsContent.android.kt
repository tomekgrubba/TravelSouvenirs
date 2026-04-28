package com.travelsouvenirs.main.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.ui.map.MapViewModel
import com.travelsouvenirs.main.ui.map.rememberGroupIcons
import com.travelsouvenirs.main.ui.map.rememberIndividualIcons
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*

private const val CLUSTER_ZOOM_THRESHOLD = 13f
private const val LOCATION_ZOOM = 4f

private const val GOOGLE_MAPS_DARK_STYLE = """[{"elementType":"geometry","stylers":[{"color":"#242f3e"}]},{"elementType":"labels.text.fill","stylers":[{"color":"#746855"}]},{"elementType":"labels.text.stroke","stylers":[{"color":"#242f3e"}]},{"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},{"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},{"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#263c3f"}]},{"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#6b9a76"}]},{"featureType":"road","elementType":"geometry","stylers":[{"color":"#38414e"}]},{"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#212a37"}]},{"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#9ca5b3"}]},{"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#746855"}]},{"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#1f2835"}]},{"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#f3d19c"}]},{"featureType":"transit","elementType":"geometry","stylers":[{"color":"#2f3948"}]},{"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#17263c"}]},{"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#515c6d"}]},{"featureType":"water","elementType":"labels.text.stroke","stylers":[{"color":"#17263c"}]}]"""


@Composable
internal fun GoogleMapsContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit) {
    val context = LocalContext.current
    val repository = LocalItemRepository.current
    val locationService = LocalLocationService.current
    val categoryFilter = LocalCategoryFilter.current

    val mapTheme = rememberMapTheme()
    val viewModel: MapViewModel = viewModel { MapViewModel(repository) }
    val allItems by viewModel.items.collectAsState()
    val allPins by viewModel.itemPins.collectAsState()
    val selectedCategories by categoryFilter.selectedCategories.collectAsState()
    val availableCategories by categoryFilter.availableCategories.collectAsState()

    val items = remember(allItems, selectedCategories) {
        allItems.filter { m ->
            m.category in selectedCategories || m.category !in categoryFilter.allCategoriesSet
        }
    }
    val itemPins = remember(allPins, selectedCategories) {
        allPins.filter { pin ->
            pin.item.category in selectedCategories || pin.item.category !in categoryFilter.allCategoriesSet
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
    }
    val zoom = cameraPositionState.position.zoom
    val showIndividual = zoom >= CLUSTER_ZOOM_THRESHOLD

    val visibleBounds = remember(cameraPositionState.position, cameraPositionState.projection) {
        cameraPositionState.projection?.visibleRegion?.latLngBounds
    }
    val offScreen = remember(items, visibleBounds) {
        val bounds = visibleBounds ?: return@remember EdgeCounts(0, 0, 0, 0)
        computeEdgeCounts(
            items,
            south = bounds.southwest.latitude,
            west = bounds.southwest.longitude,
            north = bounds.northeast.latitude,
            east = bounds.northeast.longitude
        )
    }

    val itemGroups = remember(items, zoom) {
        if (showIndividual) emptyList()
        else MapViewModel.groupByZoom(items, zoom)
    }

    val individualIcons = rememberIndividualIcons(itemPins)
    val groupIcons = rememberGroupIcons(itemGroups)

    val scope = rememberCoroutineScope()

    var hasFineLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCoarseLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val hasLocationPermission = hasFineLocation || hasCoarseLocation

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasCoarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasFineLocation || hasCoarseLocation) {
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
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    var initialZoomDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialZoomDone) {
            initialZoomDone = true
            try {
                val location = if (hasLocationPermission) locationService.getCurrentLocation() else null
                val update = if (location != null) {
                    CameraUpdateFactory.newLatLngZoom(LatLng(location.lat, location.lng), LOCATION_ZOOM)
                } else if (allItems.isNotEmpty()) {
                    val bounds = LatLngBounds.Builder().apply {
                        allItems.forEach { include(LatLng(it.latitude, it.longitude)) }
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
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                minZoomPreference = 2f,
                mapStyleOptions = if (mapTheme == MapTheme.DARK) MapStyleOptions(GOOGLE_MAPS_DARK_STYLE) else null
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            if (showIndividual) {
                itemPins.forEach { pin ->
                    Marker(
                        state = rememberUpdatedMarkerState(
                            position = LatLng(pin.position.lat, pin.position.lng)
                        ),
                        title = pin.item.name,
                        snippet = pin.item.placeName,
                        icon = individualIcons[pin.item.id],
                        onClick = { onPinClick(pin.item.id); true }
                    )
                }
            } else {
                itemGroups.forEachIndexed { idx, group ->
                    val center = LatLng(group.centerLat, group.centerLng)
                    Marker(
                        state = rememberUpdatedMarkerState(position = center),
                        title = group.items.first().name,
                        snippet = if (group.items.size > 1)
                            stringResource(Res.string.items_here, group.items.size) else group.items.first().placeName,
                        icon = groupIcons[idx],
                        onClick = {
                            if (group.items.size == 1) {
                                onPinClick(group.items.first().id)
                            } else {
                                val groupIds = group.items.map { it.id }.toSet()
                                val groupPins = itemPins.filter { it.item.id in groupIds }
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

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(onClick = { jumpToMyLocation() }) {
                Icon(Icons.Default.MyLocation, contentDescription = stringResource(Res.string.cd_my_location))
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
                    Icon(Icons.Default.FilterList, contentDescription = stringResource(Res.string.cd_filter_category))
                }

                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                    modifier = Modifier.width(220.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.filter_by_category),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    availableCategories.forEach { category ->
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

        if (items.isEmpty()) {
            // Empty state card shown when no items exist or match the filter
            // Added clickable modifier so tapping the overlay opens the add item screen
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .clickable { onAddClick() }
            ) {
                Text(
                    if (allItems.isEmpty())
                        stringResource(Res.string.empty_state_no_items)
                    else
                        stringResource(Res.string.empty_state_no_match),
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
