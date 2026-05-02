package com.travelsouvenirs.main.platform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.ui.shared.CategoryFilterFab
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.ui.map.ItemGroup
import com.travelsouvenirs.main.ui.map.MapViewModel
import com.travelsouvenirs.main.theme.AppStyle
import com.travelsouvenirs.main.ui.map.buildCircularDataUrl
import com.travelsouvenirs.main.ui.map.buildPolaroidDataUrl
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectZero
import platform.darwin.NSObject
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import travelsouvenirs.composeapp.generated.resources.*

private const val OSM_IOS_CLUSTER_ZOOM_THRESHOLD = 13f

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun OsmMapContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit) {
    val repository = LocalItemRepository.current
    val locationService = LocalLocationService.current
    val categoryFilter = LocalCategoryFilter.current

    val mapTheme = rememberMapTheme()
    val isPolaroid = rememberAppStyle() == AppStyle.POLAROID
    val viewModel: MapViewModel = viewModel { MapViewModel(repository) }
    val allItems by viewModel.items.collectAsState()
    val selectedCategories by categoryFilter.selectedCategories.collectAsState()
    val availableCategories by categoryFilter.availableCategories.collectAsState()

    if (viewModel.lastProvider != MapProviderType.OPEN_STREET_MAP.name) {
        viewModel.lastProvider = MapProviderType.OPEN_STREET_MAP.name
        viewModel.initialCameraSet = false
        (viewModel.nativeMapView as? WKWebView)?.configuration?.userContentController
            ?.removeScriptMessageHandlerForName("pinClick")
        (viewModel.nativeMapView as? WKWebView)?.configuration?.userContentController
            ?.removeScriptMessageHandlerForName("mapEvent")
        viewModel.nativeMapView = null
    }

    val filteredItems = remember(allItems, selectedCategories) { categoryFilter.filterItems(allItems) }

    // Zoom and bounds tracked from JS map events
    var zoomLevel by remember { mutableStateOf(2f) }
    val showIndividual = zoomLevel >= OSM_IOS_CLUSTER_ZOOM_THRESHOLD
    var offScreen by remember { mutableStateOf(EdgeCounts(0, 0, 0, 0)) }
    var itemGroups by remember { mutableStateOf<List<ItemGroup>>(emptyList()) }

    // Page-ready flag: markers are injected only after Leaflet has loaded
    var pageReady by remember { mutableStateOf(false) }

    // rememberUpdatedState so the mapEvent handler always reads the latest filtered items
    val latestFilteredItems = rememberUpdatedState(filteredItems)

    val badgeHex = colorToHex(MaterialTheme.colorScheme.primaryContainer)

    val pinClickHandler = remember {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(
                userContentController: WKUserContentController,
                didReceiveScriptMessage: WKScriptMessage
            ) {
                (didReceiveScriptMessage.body as? String)?.toLongOrNull()?.let { onPinClick(it) }
            }
        }
    }

    val mapEventHandler = remember {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(
                userContentController: WKUserContentController,
                didReceiveScriptMessage: WKScriptMessage
            ) {
                parseMapEvent(didReceiveScriptMessage.body as? String ?: return)?.let { ev ->
                    zoomLevel = ev.zoom
                    offScreen = computeEdgeCounts(latestFilteredItems.value, ev.s, ev.w, ev.n, ev.e)
                }
            }
        }
    }

    val navigationDelegate = remember {
        OsmNavigationDelegate { pageReady = true }
    }

    val webView = remember {
        (viewModel.nativeMapView as? WKWebView) ?: run {
            val config = WKWebViewConfiguration()
            config.userContentController.addScriptMessageHandler(pinClickHandler, name = "pinClick")
            config.userContentController.addScriptMessageHandler(mapEventHandler, name = "mapEvent")
            WKWebView(frame = CGRectZero.readValue(), configuration = config).also { wv ->
                wv.navigationDelegate = navigationDelegate
                wv.loadHTMLString(osmInteractiveHtml(20.0, 0.0, 2, mapTheme == MapTheme.DARK, badgeHex), baseURL = null)
                viewModel.nativeMapView = wv
                viewModel.onClearNativeView = {
                    wv.configuration.userContentController.removeScriptMessageHandlerForName("pinClick")
                    wv.configuration.userContentController.removeScriptMessageHandlerForName("mapEvent")
                }
            }
        }
    }

    // Initial camera — set after page is ready
    LaunchedEffect(pageReady) {
        if (!pageReady) return@LaunchedEffect
        if (!viewModel.initialCameraSet) {
            viewModel.initialCameraSet = true
            try {
                val loc = locationService.getCurrentLocation()
                if (loc != null) {
                    webView.evaluateJavaScript("moveTo(${loc.lat}, ${loc.lng}, 4);", completionHandler = null)
                } else if (allItems.isNotEmpty()) {
                    val s = allItems.minOf { it.latitude }
                    val n = allItems.maxOf { it.latitude }
                    val w = allItems.minOf { it.longitude }
                    val e = allItems.maxOf { it.longitude }
                    webView.evaluateJavaScript("fitBounds($s,$w,$n,$e);", completionHandler = null)
                }
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(mapTheme, pageReady) {
        if (!pageReady) return@LaunchedEffect
        val isDark = mapTheme == MapTheme.DARK
        webView.evaluateJavaScript("setTileLayer($isDark);", completionHandler = null)
    }

    // Compute groups when zoom or items change
    LaunchedEffect(filteredItems, zoomLevel, showIndividual) {
        itemGroups = if (showIndividual) emptyList()
        else withContext(Dispatchers.Default) { MapViewModel.groupByZoom(filteredItems, zoomLevel) }
    }

    // Inject markers when data, groups, or zoom level changes (only after page ready)
    LaunchedEffect(filteredItems, itemGroups, showIndividual, pageReady, isPolaroid) {
        if (!pageReady) return@LaunchedEffect
        if (showIndividual) {
            injectIndividualMarkers(webView, filteredItems, isPolaroid)
        } else {
            injectGroupMarkers(webView, itemGroups, isPolaroid)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        UIKitView(factory = { webView }, modifier = Modifier.fillMaxSize())

        CategoryFilterFab(
            availableCategories = availableCategories,
            selectedCategories = selectedCategories,
            onToggleCategory = { categoryFilter.toggleCategoryFilter(it) },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        )

        if (filteredItems.isEmpty()) {
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

@OptIn(ExperimentalForeignApi::class)
private suspend fun injectIndividualMarkers(webView: WKWebView, items: List<Item>, isPolaroid: Boolean) {
    webView.evaluateJavaScript("clearPins();", completionHandler = null)
    items.forEach { m ->
        val title = m.name.replace("\\", "\\\\").replace("'", "\\'")
        val dataUrl = if (isPolaroid) buildPolaroidDataUrl(m.photoPath, sizePx = 80)
                      else buildCircularDataUrl(m.photoPath, sizePx = 80)
        if (dataUrl != null) {
            val fn = if (isPolaroid) "addPhotoPinPolaroid" else "addPhotoPin"
            webView.evaluateJavaScript(
                "$fn(${m.id}, ${m.latitude}, ${m.longitude}, '$title', '$dataUrl');",
                completionHandler = null
            )
        } else {
            webView.evaluateJavaScript(
                "addPin(${m.id}, ${m.latitude}, ${m.longitude}, '$title');",
                completionHandler = null
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun injectGroupMarkers(webView: WKWebView, groups: List<ItemGroup>, isPolaroid: Boolean) {
    webView.evaluateJavaScript("clearPins();", completionHandler = null)
    groups.forEach { group ->
        val rep = group.items.first()
        val title = rep.name.replace("\\", "\\\\").replace("'", "\\'")
        val dataUrl = if (isPolaroid) buildPolaroidDataUrl(rep.photoPath, sizePx = 80)
                      else buildCircularDataUrl(rep.photoPath, sizePx = 80)
        if (dataUrl != null && group.items.size > 1) {
            val fn = if (isPolaroid) "addClusterPinPolaroid" else "addClusterPin"
            webView.evaluateJavaScript(
                "$fn(${rep.id}, ${group.centerLat}, ${group.centerLng}, '$title', '$dataUrl', ${group.items.size});",
                completionHandler = null
            )
        } else if (dataUrl != null) {
            val fn = if (isPolaroid) "addPhotoPinPolaroid" else "addPhotoPin"
            webView.evaluateJavaScript(
                "$fn(${rep.id}, ${group.centerLat}, ${group.centerLng}, '$title', '$dataUrl');",
                completionHandler = null
            )
        } else {
            webView.evaluateJavaScript(
                "addPin(${rep.id}, ${group.centerLat}, ${group.centerLng}, '$title');",
                completionHandler = null
            )
        }
    }
}

private data class MapEventData(val zoom: Float, val s: Double, val n: Double, val w: Double, val e: Double)

private fun parseMapEvent(json: String): MapEventData? = try {
    fun extract(key: String): Double? {
        val match = Regex("\"$key\":\\s*([\\-0-9.]+)").find(json)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }
    MapEventData(
        zoom = extract("zoom")?.toFloat() ?: return null,
        s    = extract("s") ?: return null,
        n    = extract("n") ?: return null,
        w    = extract("w") ?: return null,
        e    = extract("e") ?: return null
    )
} catch (_: Exception) { null }

private fun colorToHex(color: Color): String {
    fun Int.hex2() = toString(16).padStart(2, '0')
    val r = (color.red   * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue  * 255).toInt()
    return "#${r.hex2()}${g.hex2()}${b.hex2()}"
}

private fun osmInteractiveHtml(lat: Double, lng: Double, zoom: Int, isDark: Boolean, badgeHex: String): String = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#map{margin:0;padding:0;width:100%;height:100%}</style>
</head><body>
<div id="map"></div>
<script>
var map=L.map('map').setView([$lat,$lng],$zoom);
var tileLayer=null;
var badgeColor='$badgeHex';
function setTileLayer(dark){
  if(tileLayer){map.removeLayer(tileLayer);}
  if(dark){
    tileLayer=L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png',{
      attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>',
      subdomains:'abcd'
    }).addTo(map);
  } else {
    tileLayer=L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
      attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);
  }
}
setTileLayer($isDark);
map.on('zoomend moveend',function(){
  var b=map.getBounds();
  window.webkit.messageHandlers.mapEvent.postMessage(JSON.stringify({
    zoom:map.getZoom(),s:b.getSouth(),n:b.getNorth(),w:b.getWest(),e:b.getEast()
  }));
});
var markers={};
function addPin(id,lat,lng,title){
  if(markers[id]){markers[id].setLatLng([lat,lng]);return;}
  var m=L.marker([lat,lng]).addTo(map);
  m.bindTooltip(title,{permanent:false});
  m.on('click',function(){window.webkit.messageHandlers.pinClick.postMessage(String(id));});
  markers[id]=m;
}
function addPhotoPin(id,lat,lng,title,dataUrl){
  if(markers[id]){markers[id].setLatLng([lat,lng]);return;}
  var icon=L.divIcon({
    html:'<div style="width:40px;height:40px;border-radius:50%;overflow:hidden;border:2px solid white;background:#888;">'
        +'<img src="'+dataUrl+'" style="width:100%;height:100%;object-fit:cover;display:block;"/>'
        +'</div>',
    iconSize:[40,40],iconAnchor:[20,20],className:''
  });
  var m=L.marker([lat,lng],{icon:icon}).addTo(map);
  m.bindTooltip(title,{permanent:false});
  m.on('click',function(){window.webkit.messageHandlers.pinClick.postMessage(String(id));});
  markers[id]=m;
}
function addClusterPin(id,lat,lng,title,dataUrl,count){
  if(markers[id]){markers[id].setLatLng([lat,lng]);return;}
  var icon=L.divIcon({
    html:'<div style="position:relative;width:44px;height:44px;">'
        +'<div style="width:40px;height:40px;border-radius:50%;overflow:hidden;border:2px solid white;background:#888;">'
        +'<img src="'+dataUrl+'" style="width:100%;height:100%;object-fit:cover;display:block;"/>'
        +'</div>'
        +'<div style="position:absolute;top:-4px;right:-4px;width:20px;height:20px;border-radius:50%;'
        +'background:'+badgeColor+';border:2px solid white;color:white;font-size:10px;font-weight:bold;'
        +'display:flex;align-items:center;justify-content:center;">'+count+'</div>'
        +'</div>',
    iconSize:[44,44],iconAnchor:[22,22],className:''
  });
  var m=L.marker([lat,lng],{icon:icon}).addTo(map);
  m.bindTooltip(title,{permanent:false});
  m.on('click',function(){window.webkit.messageHandlers.pinClick.postMessage(String(id));});
  markers[id]=m;
}
function addPhotoPinPolaroid(id,lat,lng,title,dataUrl){
  if(markers[id]){markers[id].setLatLng([lat,lng]);return;}
  var icon=L.divIcon({
    html:'<div style="background:white;padding:3px 3px 10px 3px;box-shadow:2px 3px 7px rgba(0,0,0,0.35);line-height:0;">'
        +'<img src="'+dataUrl+'" style="width:34px;height:34px;display:block;object-fit:cover;"/>'
        +'</div>',
    iconSize:[40,47],iconAnchor:[20,47],className:''
  });
  var m=L.marker([lat,lng],{icon:icon}).addTo(map);
  m.bindTooltip(title,{permanent:false});
  m.on('click',function(){window.webkit.messageHandlers.pinClick.postMessage(String(id));});
  markers[id]=m;
}
function addClusterPinPolaroid(id,lat,lng,title,dataUrl,count){
  if(markers[id]){markers[id].setLatLng([lat,lng]);return;}
  var icon=L.divIcon({
    html:'<div style="position:relative;display:inline-block;">'
        +'<div style="background:white;padding:3px 3px 10px 3px;box-shadow:2px 3px 7px rgba(0,0,0,0.35);line-height:0;">'
        +'<img src="'+dataUrl+'" style="width:34px;height:34px;display:block;object-fit:cover;"/>'
        +'</div>'
        +'<div style="position:absolute;top:-5px;right:-5px;width:20px;height:20px;border-radius:50%;'
        +'background:'+badgeColor+';border:2px solid white;color:white;font-size:10px;font-weight:bold;'
        +'display:flex;align-items:center;justify-content:center;">'+count+'</div>'
        +'</div>',
    iconSize:[46,52],iconAnchor:[23,52],className:''
  });
  var m=L.marker([lat,lng],{icon:icon}).addTo(map);
  m.bindTooltip(title,{permanent:false});
  m.on('click',function(){window.webkit.messageHandlers.pinClick.postMessage(String(id));});
  markers[id]=m;
}
function clearPins(){Object.values(markers).forEach(function(m){map.removeLayer(m);});markers={};}
function fitBounds(s,w,n,e){map.fitBounds([[s,w],[n,e]],{padding:[40,40]});}
function moveTo(lat,lng,zoom){map.setView([lat,lng],zoom);}
</script>
</body></html>
""".trimIndent()
