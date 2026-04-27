package com.travelsouvenirs.main.platform

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.ui.map.MapViewModel
import com.travelsouvenirs.main.ui.map.buildCircularBitmap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.graphics.toArgb
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon as OsmPolygon
import travelsouvenirs.composeapp.generated.resources.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val OSM_CLUSTER_ZOOM_THRESHOLD = 13
private const val OSM_LOCATION_ZOOM = 4.0


private fun buildApproxCircle(center: GeoPoint, radiusMeters: Double, fillArgb: Int, strokeArgb: Int): OsmPolygon {
    val steps = 64
    val points = (0 until steps).map { i ->
        val angle = i * 2 * PI / steps
        val dLat = (radiusMeters / 111320.0) * cos(angle)
        val dLng = (radiusMeters / (111320.0 * cos(Math.toRadians(center.latitude)))) * sin(angle)
        GeoPoint(center.latitude + dLat, center.longitude + dLng)
    }
    return OsmPolygon().apply {
        this.points = points
        fillPaint.color = fillArgb
        outlinePaint.color = strokeArgb
        outlinePaint.strokeWidth = 3f
        setInfoWindow(null)
    }
}

@Composable
internal fun OsmMapContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit) {
    val context = LocalContext.current
    val repository = LocalItemRepository.current
    val locationService = LocalLocationService.current
    val categoryFilter = LocalCategoryFilter.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val viewModel: MapViewModel = viewModel { MapViewModel(repository) }
    val allItems by viewModel.items.collectAsState()
    val allPins by viewModel.itemPins.collectAsState()
    val selectedCategories by categoryFilter.selectedCategories.collectAsState()
    val availableCategories by categoryFilter.availableCategories.collectAsState()

    // Reset camera state when switching to this provider
    if (viewModel.lastProvider != MapProviderType.OPEN_STREET_MAP.name) {
        viewModel.lastProvider = MapProviderType.OPEN_STREET_MAP.name
        viewModel.osmZoom = null
        viewModel.osmCenterLat = null
        viewModel.osmCenterLng = null
    }

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

    // Initialize zoomLevel with the restored zoom from the ViewModel to ensure 
    // marker cluster logic computes correctly immediately when returning from the detail screen.
    var zoomLevel by remember { mutableStateOf(viewModel.osmZoom?.toInt() ?: 3) }
    val showIndividual = zoomLevel >= OSM_CLUSTER_ZOOM_THRESHOLD
    // Always reflects the latest filtered items inside the map listener closure
    val latestItems = rememberUpdatedState(items)
    var offScreen by remember { mutableStateOf(EdgeCounts(0, 0, 0, 0)) }
    var showFilterMenu by remember { mutableStateOf(false) }
    val isFilterActive = selectedCategories != categoryFilter.allCategoriesSet
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
    val isApproximate = hasCoarseLocation && !hasFineLocation
    var approxUserPos by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var approxOverlay by remember { mutableStateOf<OsmPolygon?>(null) }

    // Individual icon cache: itemId -> BitmapDrawable (no badge)
    val iconCache = remember { mutableMapOf<Long, BitmapDrawable>() }
    // Cluster icon cache: (firstItemId, count) -> BitmapDrawable (with count badge)
    val clusterIconCache = remember { mutableMapOf<Pair<Long, Int>, BitmapDrawable>() }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            
            // Limit zoom out and prevent vertical map repetition
            minZoomLevel = 3.0
            isVerticalMapRepetitionEnabled = false
            // Explicitly set the maximum map boundaries to prevent the user from panning into empty gray space
            setScrollableAreaLimitDouble(BoundingBox(85.0, 180.0, -85.0, -180.0))
            
            // Restore primitive zoom and center state from ViewModel to prevent memory leaks 
            // caused by holding the Activity Context inside the ViewModel.
            val savedZoom = viewModel.osmZoom
            val savedLat = viewModel.osmCenterLat
            val savedLng = viewModel.osmCenterLng
            if (savedZoom != null && savedLat != null && savedLng != null) {
                controller.setZoom(savedZoom)
                controller.setCenter(GeoPoint(savedLat, savedLng))
            } else {
                controller.setZoom(3.0)
            }
        }
    }

    // Use rememberSaveable so that the initial camera is only set once per lifecycle,
    // allowing the user to navigate away and back without the map resetting to the default bounds.
    var initialZoomDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialZoomDone && viewModel.osmZoom == null) {
            initialZoomDone = true
            try {
                val loc = if (hasLocationPermission) locationService.getCurrentLocation() else null
                if (loc != null) {
                    approxUserPos = loc.lat to loc.lng
                    mapView.controller.setZoom(OSM_LOCATION_ZOOM)
                    mapView.controller.setCenter(GeoPoint(loc.lat, loc.lng))
                } else if (allItems.isNotEmpty()) {
                    val bb = BoundingBox.fromGeoPoints(allItems.map { GeoPoint(it.latitude, it.longitude) })
                    mapView.post { mapView.zoomToBoundingBox(bb, false, 120) }
                }
            } catch (_: Exception) { }
        }
    }

    // Save map state to ViewModel on dispose
    DisposableEffect(mapView) {
        onDispose {
            try {
                viewModel.osmZoom = mapView.zoomLevelDouble
                val center = mapView.mapCenter
                if (center != null) {
                    viewModel.osmCenterLat = center.latitude
                    viewModel.osmCenterLng = center.longitude
                }
            } catch (_: Exception) { }
            mapView.onDetach()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasCoarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasFineLocation || hasCoarseLocation) {
            scope.launch {
                try {
                    locationService.getCurrentLocation()?.let { loc ->
                        approxUserPos = loc.lat to loc.lng
                        mapView.controller.animateTo(GeoPoint(loc.lat, loc.lng), OSM_LOCATION_ZOOM, 600L)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    // Update zoomLevel and edge counts reactively from actual map events
    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onZoom(event: ZoomEvent?): Boolean {
                event?.zoomLevel?.let { zoomLevel = it.toInt() }
                val bb = mapView.boundingBox
                if (bb != null) {
                    offScreen = computeEdgeCounts(
                        latestItems.value, bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast
                    )
                }
                return false
            }
            override fun onScroll(event: ScrollEvent?): Boolean {
                val bb = mapView.boundingBox
                if (bb != null) {
                    offScreen = computeEdgeCounts(
                        latestItems.value, bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast
                    )
                }
                return false
            }
        }
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }

    // Rebuild markers when pins, filter, or zoom level changes
    LaunchedEffect(itemPins, items, zoomLevel) {
        // Pre-load missing icons
        itemPins.forEach { pin ->
            if (!iconCache.containsKey(pin.item.id)) {
                val bmp = buildCircularBitmap(context, pin.item.photoPath, 0, 120)
                if (bmp != null) iconCache[pin.item.id] = BitmapDrawable(context.resources, bmp)
            }
        }

        // Guard against a MapView that left composition during the IO suspension above
        if (!mapView.isAttachedToWindow) return@LaunchedEffect
        mapView.getRepository() ?: return@LaunchedEffect

        // Atomically update overlays on the UI thread to minimize the window for a ConcurrentModificationException
        // when osmdroid's background render thread iterates over the overlays list. We build a temporary list first, 
        // retaining system overlays like the copyright logo, and then swap it.
        val newOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
        newOverlays.addAll(mapView.overlays.filter { it !is Marker })

        if (showIndividual) {
            itemPins.forEach { pin ->
                // Ensure the MapView is still in a valid state before creating markers
                val repo = mapView.getRepository() ?: return@forEach
                
                val marker = Marker(mapView)
                marker.position = GeoPoint(pin.position.lat, pin.position.lng)
                marker.title = pin.item.name
                marker.snippet = pin.item.placeName
                iconCache[pin.item.id]?.let { marker.icon = it }
                marker.setOnMarkerClickListener { _, _ -> onPinClick(pin.item.id); true }
                // Suppress the default info window popup
                marker.setInfoWindow(null)
                newOverlays.add(marker)
            }
        } else {
            val groups = MapViewModel.groupByZoom(items, zoomLevel.toFloat())
            groups.forEach { group ->
                val firstId = group.items.first().id
                val count = group.items.size
                val cacheKey = firstId to count
                if (!clusterIconCache.containsKey(cacheKey)) {
                    val bmp = buildCircularBitmap(context, group.items.first().photoPath, count, 120)
                    if (bmp != null) clusterIconCache[cacheKey] = BitmapDrawable(context.resources, bmp)
                }
                
                // Ensure the MapView is still in a valid state before creating markers
                val repo = mapView.getRepository() ?: return@forEach
                
                val marker = Marker(mapView)
                marker.position = GeoPoint(group.centerLat, group.centerLng)
                marker.title = group.items.first().name
                clusterIconCache[cacheKey]?.let { marker.icon = it }
                // Suppress the default info window popup
                marker.setInfoWindow(null)
                if (group.items.size == 1) {
                    marker.setOnMarkerClickListener { _, _ -> onPinClick(group.items.first().id); true }
                } else {
                    marker.setOnMarkerClickListener { _, _ ->
                        val groupIds = group.items.map { it.id }.toSet()
                        // Use spread positions from itemPins instead of raw coordinates:
                        // same-location items produce a zero-area BoundingBox from raw coords,
                        // which zooms to maximum level. Spread positions guarantee non-zero area.
                        val groupPins = itemPins.filter { it.item.id in groupIds }
                        if (groupPins.size > 1) {
                            val bb = BoundingBox.fromGeoPoints(groupPins.map { GeoPoint(it.position.lat, it.position.lng) })
                            // Matched Google Maps behavior: use 200px padding when zooming to a cluster bounds
                            mapView.post { mapView.zoomToBoundingBox(bb, true, 200) }
                        } else {
                            mapView.controller.animateTo(GeoPoint(group.centerLat, group.centerLng), 16.0, 600L)
                        }
                        true
                    }
                }
                newOverlays.add(marker)
            }
        }

        mapView.overlays.clear()
        mapView.overlays.addAll(newOverlays)

        val bb = mapView.boundingBox
        if (bb != null) {
            offScreen = computeEdgeCounts(items, bb.latSouth, bb.lonWest, bb.latNorth, bb.lonEast)
        }
        mapView.invalidate()
    }

    // Lifecycle events for osmdroid
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    LaunchedEffect(approxUserPos, isApproximate) {
        approxOverlay?.let { mapView.overlays.remove(it) }
        approxOverlay = null
        val pos = approxUserPos
        if (isApproximate && pos != null) {
            val circle = buildApproxCircle(
                center = GeoPoint(pos.first, pos.second),
                radiusMeters = 2000.0,
                fillArgb = primaryColor.copy(alpha = 0.15f).toArgb(),
                strokeArgb = primaryColor.copy(alpha = 0.6f).toArgb()
            )
            mapView.overlays.add(0, circle)
            approxOverlay = circle
            mapView.invalidate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        scope.launch {
                            try {
                                locationService.getCurrentLocation()?.let { loc ->
                                    approxUserPos = loc.lat to loc.lng
                                    mapView.controller.animateTo(GeoPoint(loc.lat, loc.lng), OSM_LOCATION_ZOOM, 600L)
                                }
                            } catch (_: Exception) { }
                        }
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                }
            ) {
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
