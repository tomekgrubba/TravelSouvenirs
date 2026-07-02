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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
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
import com.travelsouvenirs.main.di.LocalAppViewModel
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.location.LocationService
import com.travelsouvenirs.main.ui.map.ItemGroup
import com.travelsouvenirs.main.ui.shared.CategoryFilterFab
import com.travelsouvenirs.main.ui.map.MapViewModel
import com.travelsouvenirs.main.ui.map.rememberGroupIcons
import com.travelsouvenirs.main.ui.map.rememberIndividualIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*

private const val CLUSTER_ZOOM_THRESHOLD = 13f
private const val INITIAL_ZOOM = 5f
private const val LOCATION_ZOOM = 10f
private const val PIN_ZOOM = 15f


@Composable
internal fun GoogleMapsContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit) {
    val context = LocalContext.current
    val locationService: LocationService = koinInject()
    val categoryFilter = LocalCategoryFilter.current
    val appViewModel = LocalAppViewModel.current

    val viewModel: MapViewModel = koinViewModel()
    val allItems by viewModel.items.collectAsState()
    val allPins by viewModel.itemPins.collectAsState()
    val selectedCategories by categoryFilter.selectedCategories.collectAsState()
    val availableCategories by categoryFilter.availableCategories.collectAsState()

    val items = remember(allItems, selectedCategories) { categoryFilter.filterItems(allItems) }
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

    val appViewModelTarget = appViewModel.targetCameraLocation
    LaunchedEffect(appViewModelTarget) {
        if (appViewModelTarget != null) {
            appViewModel.clearTargetCameraLocation()
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(appViewModelTarget.lat, appViewModelTarget.lng), PIN_ZOOM),
                1000
            )
        }
    }

    var itemGroups by remember { mutableStateOf<List<ItemGroup>>(emptyList()) }
    LaunchedEffect(items, zoom.toInt(), showIndividual) {
        itemGroups = if (showIndividual) emptyList()
        else withContext(Dispatchers.Default) { MapViewModel.groupByZoom(items, zoom) }
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val density = LocalDensity.current
    val markerSizeDp = if (isTablet) 56 else 40
    val markerSizePx = with(density) { markerSizeDp.dp.roundToPx() }

    val individualIcons = rememberIndividualIcons(itemPins, sizePx = markerSizePx)
    val groupIcons = rememberGroupIcons(itemGroups, sizePx = markerSizePx)

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
                        val targetZoom = maxOf(LOCATION_ZOOM, cameraPositionState.position.zoom)
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(loc.lat, loc.lng), targetZoom), 600
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
                // If there's a target location waiting from appViewModel, let that take priority
                if (appViewModel.targetCameraLocation == null) {
                    val location = if (hasLocationPermission) locationService.getCurrentLocation() else null
                    val update = if (location != null) {
                        CameraUpdateFactory.newLatLngZoom(LatLng(location.lat, location.lng), INITIAL_ZOOM)
                    } else if (allItems.isNotEmpty()) {
                        val bounds = LatLngBounds.Builder().apply {
                            allItems.forEach { include(LatLng(it.latitude, it.longitude)) }
                        }.build()
                        CameraUpdateFactory.newLatLngBounds(bounds, 120)
                    } else null
                    update?.let { cameraPositionState.animate(it, 800) }
                }
            } catch (_: Exception) { }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        @Suppress("MissingPermission")
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                minZoomPreference = 2f,
                mapStyleOptions = MapStyleOptions(GOOGLE_MAPS_LIGHT_STYLE_POLAROID)
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            if (showIndividual) {
                itemPins.forEach { pin ->
                    key(pin.item.id) {
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
                }
            } else {
                itemGroups.forEach { group ->
                    key(group.items.first().id, group.items.size) {
                        val center = LatLng(group.centerLat, group.centerLng)
                        Marker(
                            state = rememberUpdatedMarkerState(position = center),
                            title = group.items.first().name,
                            snippet = if (group.items.size > 1)
                                stringResource(Res.string.items_here, group.items.size) else group.items.first().placeName,
                            icon = groupIcons["${group.items.first().photoPath}_${if (group.items.size > 1) group.items.size else 0}"],
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
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isTablet) {
                FloatingActionButton(onClick = { jumpToMyLocation() }) {
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(Res.string.cd_my_location), modifier = Modifier.size(28.dp))
                }
            } else {
                SmallFloatingActionButton(onClick = { jumpToMyLocation() }) {
                    Icon(Icons.Default.MyLocation, contentDescription = stringResource(Res.string.cd_my_location))
                }
            }

            CategoryFilterFab(
                availableCategories = availableCategories,
                selectedCategories = selectedCategories,
                onToggleCategory = { categoryFilter.toggleCategoryFilter(it) },
                isTablet = isTablet,
            )
        }

        if (items.isEmpty()) {
            // Empty state card shown when no items exist or match the filter
            // Added clickable modifier so tapping the overlay opens the add item screen
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .clickable { onAddClick() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
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
