package com.travelsouvenirs.main.platform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.travelsouvenirs.main.di.LocalAppViewModel
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.location.LocationService
import com.travelsouvenirs.main.ui.map.ItemGroup
import com.travelsouvenirs.main.ui.map.MapViewModel
import com.travelsouvenirs.main.ui.shared.CategoryFilterFab
import org.koin.compose.koinInject
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.currentKoinScope
import com.travelsouvenirs.main.ui.map.rememberGroupIosIcons
import com.travelsouvenirs.main.ui.map.rememberIndividualIosIcons
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMake
import platform.UIKit.UIScreen
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKStandardMapConfiguration
import platform.MapKit.MKStandardMapEmphasisStyleMuted
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIImage
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObject
import platform.objc.sel_registerName
import travelsouvenirs.composeapp.generated.resources.*
import kotlin.math.log2

private const val NATIVE_CLUSTER_ZOOM_THRESHOLD = 13f

// Carries typed payload for each map annotation.
private class ItemAnnotation(
    val itemId: Long,
    val groupIndex: Int,  // -1 for individual items
    val itemCount: Int
) : MKPointAnnotation()

// Implements MKMapViewDelegate to provide custom annotation views and react to region changes.
private class MapDelegateHelper(
    val iconCache: MutableMap<Long, UIImage>,
    val groupIconCache: MutableMap<Int, UIImage>,
    val onPinClick: (Long) -> Unit,
    val onRegionChange: (zoom: Float, south: Double, west: Double, north: Double, east: Double) -> Unit
) : NSObject(), MKMapViewDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        val ann = viewForAnnotation as? ItemAnnotation ?: return null
        val reuseId = if (ann.groupIndex >= 0) "g${ann.groupIndex}" else "i${ann.itemId}"
        val dequeued = mapView.dequeueReusableAnnotationViewWithIdentifier(reuseId) as? MKAnnotationView
        val view = dequeued ?: MKAnnotationView(annotation = viewForAnnotation, reuseIdentifier = reuseId)
        view.annotation = viewForAnnotation
        view.canShowCallout = false
        view.image = if (ann.groupIndex >= 0) groupIconCache[ann.groupIndex] else iconCache[ann.itemId]

        return view
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val ann = didSelectAnnotationView.annotation as? ItemAnnotation ?: return
        if (ann.groupIndex < 0 || ann.itemCount == 1) {
            onPinClick(ann.itemId)
        } else {
            mapView.region.useContents {
                val newSpanLat = span.latitudeDelta / 3.0
                val newSpanLng = span.longitudeDelta / 3.0
                val newRegion = MKCoordinateRegionMake(ann.coordinate, MKCoordinateSpanMake(newSpanLat, newSpanLng))
                mapView.setRegion(newRegion, animated = true)
            }
        }
        mapView.deselectAnnotation(ann, animated = false)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        mapView.region.useContents {
            val cLat = center.latitude
            val cLng = center.longitude
            val hLat = span.latitudeDelta / 2.0
            val hLng = span.longitudeDelta / 2.0
            val zoom = log2(360.0 / span.longitudeDelta.coerceAtLeast(0.0001)).toFloat()
            onRegionChange(zoom, cLat - hLat, cLng - hLng, cLat + hLat, cLng + hLng)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun NativeMapsContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit) {
    val locationService: LocationService = koinInject()
    val categoryFilter = LocalCategoryFilter.current
    val appViewModel = LocalAppViewModel.current
    val koinScope = currentKoinScope()
    val viewModel: MapViewModel = viewModel { koinScope.get<MapViewModel>() }

    val allItems by viewModel.items.collectAsState()
    val allPins  by viewModel.itemPins.collectAsState()
    val selectedCategories by categoryFilter.selectedCategories.collectAsState()
    val availableCategories by categoryFilter.availableCategories.collectAsState()

    val items = remember(allItems, selectedCategories) { categoryFilter.filterItems(allItems) }
    val itemPins = remember(allPins, selectedCategories) {
        allPins.filter { pin ->
            pin.item.category in selectedCategories || pin.item.category !in categoryFilter.allCategoriesSet
        }
    }

    var zoomLevel by remember { mutableStateOf(5f) }
    val showIndividual = zoomLevel >= NATIVE_CLUSTER_ZOOM_THRESHOLD
    var itemGroups by remember { mutableStateOf<List<ItemGroup>>(emptyList()) }
    var offScreen by remember { mutableStateOf(EdgeCounts(0, 0, 0, 0)) }
    // Clustering: recompute groups whenever zoom or items change
    LaunchedEffect(items, zoomLevel, showIndividual) {
        itemGroups = if (showIndividual) emptyList()
        else withContext(Dispatchers.Default) { MapViewModel.groupByZoom(items, zoomLevel) }
    }

    val screenWidthPt = UIScreen.mainScreen.bounds.useContents { size.width }
    val isTablet = screenWidthPt >= 600.0
    val markerSizeDp = if (isTablet) 56 else 40
    // UIGraphicsBeginImageContextWithOptions sizes are in UIKit points (1pt ≈ 1dp on iOS)
    val individualIcons = rememberIndividualIosIcons(itemPins, markerSizeDp)
    val groupIcons      = rememberGroupIosIcons(itemGroups, markerSizeDp)

    // rememberUpdatedState so delegate lambdas always see the latest values
    val latestItems    = rememberUpdatedState(items)
    val latestOnPinClick = rememberUpdatedState(onPinClick)
    val coroutineScope = rememberCoroutineScope()

    val mapView = remember {
        (viewModel.nativeMapView as? MKMapView) ?: MKMapView().also { viewModel.nativeMapView = it }
    }

    fun jumpToMyLocation() {
        coroutineScope.launch {
            try {
                locationService.getCurrentLocation()?.let { loc ->
                    val coord = CLLocationCoordinate2DMake(loc.lat, loc.lng)
                    mapView.setRegion(
                        MKCoordinateRegionMakeWithDistance(coord, 5000.0, 5000.0),
                        animated = true
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun scrollOffScreen(direction: String) {
        mapView.region.useContents {
            val cLat = center.latitude
            val cLng = center.longitude
            val hLat = span.latitudeDelta / 2.0
            val hLng = span.longitudeDelta / 2.0
            val target = findNextOffscreenItem(
                items = items,
                direction = direction,
                south = cLat - hLat,
                west = cLng - hLng,
                north = cLat + hLat,
                east = cLng + hLng
            )
            if (target != null) {
                val newRegion = MKCoordinateRegionMake(
                    CLLocationCoordinate2DMake(target.latitude, target.longitude),
                    span
                )
                mapView.setRegion(newRegion, animated = true)
            }
        }
    }

    val delegate = remember {
        MapDelegateHelper(
            iconCache      = mutableMapOf(),
            groupIconCache = mutableMapOf(),
            onPinClick     = { id -> coroutineScope.launch { latestOnPinClick.value(id) } },
            onRegionChange = { zoom, s, w, n, e ->
                zoomLevel = zoom
                offScreen = computeEdgeCounts(latestItems.value, s, w, n, e)
            }
        )
    }

    val targetLoc by appViewModel.targetCameraLocation.collectAsState()
    LaunchedEffect(targetLoc) {
        val target = targetLoc
        if (target != null) {
            appViewModel.clearTargetCameraLocation()
            coroutineScope.launch {
                val coord = CLLocationCoordinate2DMake(target.lat, target.lng)
                mapView.setRegion(
                    MKCoordinateRegionMakeWithDistance(coord, 5000.0, 5000.0),
                    animated = true
                )
            }
        }
    }

    // Keep delegate icon caches in sync when loading finishes
    LaunchedEffect(individualIcons.entries.toList()) {
        delegate.iconCache.clear()
        delegate.iconCache.putAll(individualIcons)
    }
    LaunchedEffect(groupIcons.entries.toList()) {
        delegate.groupIconCache.clear()
        delegate.groupIconCache.putAll(groupIcons)
    }

    LaunchedEffect(Unit) {
        mapView.delegate = delegate
    }

    LaunchedEffect(Unit) {
        mapView.overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleLight
        mapView.preferredConfiguration = MKStandardMapConfiguration().also {
            it.emphasisStyle = MKStandardMapEmphasisStyleMuted
        }
        mapView.showsUserLocation = true
    }

    // Initial camera — fire only once per provider switch
    LaunchedEffect(Unit) {
        if (!viewModel.initialCameraSet) {
            viewModel.initialCameraSet = true
            if (appViewModel.targetCameraLocation.value == null) {
                val loc = locationService.getCurrentLocation()
                if (loc != null) {
                    val coord = CLLocationCoordinate2DMake(loc.lat, loc.lng)
                    mapView.setRegion(
                        MKCoordinateRegionMakeWithDistance(coord, 5_000_000.0, 5_000_000.0),
                        animated = false
                    )
                } else if (allItems.isNotEmpty()) {
                    val minLat = allItems.minOf { it.latitude }
                    val maxLat = allItems.maxOf { it.latitude }
                    val minLng = allItems.minOf { it.longitude }
                    val maxLng = allItems.maxOf { it.longitude }
                    val spanLat = (maxLat - minLat) * 1.4 + 0.01
                    val spanLng = (maxLng - minLng) * 1.4 + 0.01
                    mapView.setRegion(
                        MKCoordinateRegionMake(
                            CLLocationCoordinate2DMake((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0),
                            MKCoordinateSpanMake(spanLat, spanLng)
                        ),
                        animated = false
                    )
                }
            }
        }
    }

    // Place annotations after icons finish loading (keyed on cache sizes to re-fire when icons arrive)
    LaunchedEffect(items, itemPins, itemGroups, showIndividual,
                   individualIcons.size, groupIcons.size) {
        mapView.removeAnnotations(mapView.annotations)
        if (showIndividual) {
            itemPins.forEach { pin ->
                val ann = ItemAnnotation(itemId = pin.item.id, groupIndex = -1, itemCount = 1)
                ann.setCoordinate(CLLocationCoordinate2DMake(pin.position.lat, pin.position.lng))
                ann.setTitle(pin.item.name)
                ann.setSubtitle(pin.item.placeName)
                mapView.addAnnotation(ann as MKAnnotationProtocol)
            }
        } else {
            itemGroups.forEachIndexed { idx, group ->
                val ann = ItemAnnotation(itemId = group.items.first().id, groupIndex = idx, itemCount = group.items.size)
                ann.setCoordinate(CLLocationCoordinate2DMake(group.centerLat, group.centerLng))
                ann.setTitle(group.items.first().name)
                ann.setSubtitle(
                    if (group.items.size > 1) "${group.items.size} items"
                    else group.items.first().placeName
                )
                mapView.addAnnotation(ann as MKAnnotationProtocol)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        UIKitView(factory = { mapView }, modifier = Modifier.fillMaxSize())

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

        // Empty state
        if (items.isEmpty()) {
            val noItems = allItems.isEmpty()
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .clickable { onAddClick() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(
                    if (noItems)
                        stringResource(Res.string.empty_state_no_items)
                    else
                        stringResource(Res.string.empty_state_no_match),
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Edge indicators
        if (offScreen.top > 0)
            EdgeIndicator("▲", offScreen.top, { scrollOffScreen("top") }, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        if (offScreen.bottom > 0)
            EdgeIndicator("▼", offScreen.bottom, { scrollOffScreen("bottom") }, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        if (offScreen.left > 0)
            EdgeIndicator("◀", offScreen.left, { scrollOffScreen("left") }, Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
        if (offScreen.right > 0)
            EdgeIndicator("▶", offScreen.right, { scrollOffScreen("right") }, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
    }
}
