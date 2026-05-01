package com.travelsouvenirs.main.platform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.ui.map.MapViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectZero
import platform.darwin.NSObject
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import travelsouvenirs.composeapp.generated.resources.*

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun OsmMapContent(onPinClick: (Long) -> Unit, onAddClick: () -> Unit) {
    val repository = LocalItemRepository.current
    val locationService = LocalLocationService.current
    val categoryFilter = LocalCategoryFilter.current

    val mapTheme = rememberMapTheme()
    val viewModel: MapViewModel = viewModel { MapViewModel(repository) }
    val allItems by viewModel.items.collectAsState()
    val selectedCategories by categoryFilter.selectedCategories.collectAsState()
    val availableCategories by categoryFilter.availableCategories.collectAsState()

    if (viewModel.lastProvider != MapProviderType.OPEN_STREET_MAP.name) {
        viewModel.lastProvider = MapProviderType.OPEN_STREET_MAP.name
        viewModel.initialCameraSet = false
        (viewModel.nativeMapView as? WKWebView)?.configuration?.userContentController
            ?.removeScriptMessageHandlerForName("pinClick")
        viewModel.nativeMapView = null
    }

    val filteredItems = remember(allItems, selectedCategories) { categoryFilter.filterItems(allItems) }

    var showFilterMenu by remember { mutableStateOf(false) }
    val isFilterActive = selectedCategories != categoryFilter.allCategoriesSet

    // Page-ready flag: markers are injected only after Leaflet has loaded
    var pageReady by remember { mutableStateOf(false) }

    val handler = remember {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(
                userContentController: WKUserContentController,
                didReceiveScriptMessage: WKScriptMessage
            ) {
                (didReceiveScriptMessage.body as? String)?.toLongOrNull()?.let { onPinClick(it) }
            }
        }
    }

    val navigationDelegate = remember {
        OsmNavigationDelegate { pageReady = true }
    }

    val webView = remember {
        (viewModel.nativeMapView as? WKWebView) ?: run {
            val config = WKWebViewConfiguration()
            config.userContentController.addScriptMessageHandler(handler, name = "pinClick")
            WKWebView(frame = CGRectZero.readValue(), configuration = config).also { wv ->
                wv.navigationDelegate = navigationDelegate
                wv.loadHTMLString(osmInteractiveHtml(20.0, 0.0, 2, mapTheme == MapTheme.DARK), baseURL = null)
                viewModel.nativeMapView = wv
                viewModel.onClearNativeView = {
                    wv.configuration.userContentController.removeScriptMessageHandlerForName("pinClick")
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

    // Inject/refresh markers when data or filter changes (only after page ready)
    LaunchedEffect(filteredItems, pageReady) {
        if (!pageReady) return@LaunchedEffect
        injectMarkers(webView, filteredItems)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        UIKitView(factory = { webView }, modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
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
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun injectMarkers(webView: WKWebView, items: List<Item>) {
    webView.evaluateJavaScript("clearPins();", completionHandler = null)
    items.forEach { m ->
        val title = m.name.replace("\\", "\\\\").replace("'", "\\'")
        webView.evaluateJavaScript(
            "addPin(${m.id}, ${m.latitude}, ${m.longitude}, '$title');",
            completionHandler = null
        )
    }
}

private fun osmInteractiveHtml(lat: Double, lng: Double, zoom: Int, isDark: Boolean): String = """
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
var markers={};
function addPin(id,lat,lng,title){
  if(markers[id]){markers[id].setLatLng([lat,lng]);return;}
  var m=L.marker([lat,lng]).addTo(map);
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
