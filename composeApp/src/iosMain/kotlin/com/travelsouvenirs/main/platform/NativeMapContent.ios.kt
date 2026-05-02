package com.travelsouvenirs.main.platform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.ui.shared.CategoryFilterFab
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.ui.map.ItemGroup
import com.travelsouvenirs.main.ui.map.MapViewModel
import com.travelsouvenirs.main.ui.map.rememberGroupIosIcons
import com.travelsouvenirs.main.ui.map.rememberIndividualIosIcons
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UIImage
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObject
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
        val view = (mapView.dequeueReusableAnnotationViewWithIdentifier(reuseId) as? MKAnnotationView)
            ?: MKAnnotationView(annotation = viewForAnnotation, reuseIdentifier = reuseId)
        view.annotation = viewForAnnotation
        view.canShowCallout = true
        view.image = if (ann.groupIndex >= 0) groupIconCache[ann.groupIndex] else iconCache[ann.itemId]
        return view
    }

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val ann = didSelectAnnotationView.annotation as? ItemAnnotation ?: return
        if (ann.groupIndex < 0) onPinClick(ann.itemId)
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
    val mapTheme = rememberMapTheme()
    val repository = LocalItemRepository.current
    val locationService = LocalLocationService.current
    val categoryFilter = LocalCategoryFilter.current
    val viewModel: MapViewModel = viewModel { MapViewModel(repository) }

    if (viewModel.lastProvider != MapProviderType.NATIVE.name) {
        viewModel.lastProvider = MapProviderType.NATIVE.name
        viewModel.initialCameraSet = false
        viewModel.nativeMapView = null
    }

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

    val individualIcons = rememberIndividualIosIcons(itemPins)
    val groupIcons      = rememberGroupIosIcons(itemGroups)

    // rememberUpdatedState so the delegate lambda always sees the latest items
    val latestItems = rememberUpdatedState(items)

    val delegate = remember {
        MapDelegateHelper(
            iconCache      = mutableMapOf(),
            groupIconCache = mutableMapOf(),
            onPinClick     = onPinClick,
            onRegionChange = { zoom, s, w, n, e ->
                zoomLevel = zoom
                offScreen = computeEdgeCounts(latestItems.value, s, w, n, e)
            }
        )
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

    val mapView = remember {
        (viewModel.nativeMapView as? MKMapView) ?: MKMapView().also { viewModel.nativeMapView = it }
    }

    LaunchedEffect(Unit) {
        mapView.delegate = delegate
    }

    LaunchedEffect(mapTheme) {
        mapView.overrideUserInterfaceStyle = if (mapTheme == MapTheme.DARK)
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        else
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
    }

    // Initial camera — fire only once per provider switch
    LaunchedEffect(Unit) {
        if (!viewModel.initialCameraSet) {
            viewModel.initialCameraSet = true
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

        CategoryFilterFab(
            availableCategories = availableCategories,
            selectedCategories = selectedCategories,
            onToggleCategory = { categoryFilter.toggleCategoryFilter(it) },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        )

        // Empty state
        if (items.isEmpty()) {
            val noItems = allItems.isEmpty()
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
                    .then(if (noItems) Modifier.clickable { onAddClick() } else Modifier)
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
            EdgeIndicator("▲", offScreen.top, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        if (offScreen.bottom > 0)
            EdgeIndicator("▼", offScreen.bottom, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        if (offScreen.left > 0)
            EdgeIndicator("◀", offScreen.left, Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
        if (offScreen.right > 0)
            EdgeIndicator("▶", offScreen.right, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
    }
}
