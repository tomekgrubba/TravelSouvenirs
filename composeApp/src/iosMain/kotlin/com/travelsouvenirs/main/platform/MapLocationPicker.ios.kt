package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformMapLocationPicker(
    selectedLat: Double?,
    selectedLng: Double?,
    cameraMoveId: Int,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier
) {
    val onLocationPickedState = rememberUpdatedState(onLocationPicked)

    val handler = remember {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(
                userContentController: WKUserContentController,
                didReceiveScriptMessage: WKScriptMessage
            ) {
                val body = didReceiveScriptMessage.body as? String ?: return
                val parts = body.split(",")
                val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return
                val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: return
                onLocationPickedState.value(lat, lng)
            }
        }
    }

    val mapTheme = rememberMapTheme()
    val isDark = mapTheme == MapTheme.DARK

    val webView = remember {
        val config = WKWebViewConfiguration()
        config.userContentController.addScriptMessageHandler(handler, name = "locationPicker")
        WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
            loadHTMLString(mapPickerHtml(selectedLat, selectedLng, isDark), baseURL = null)
        }
    }

    LaunchedEffect(mapTheme) {
        webView.evaluateJavaScript("setTileLayer($isDark);", completionHandler = null)
    }

    // Update marker position when pin moves (drag/tap/GPS/search)
    LaunchedEffect(selectedLat, selectedLng) {
        if (selectedLat != null && selectedLng != null)
            webView.evaluateJavaScript("updateMarker($selectedLat,$selectedLng)", completionHandler = null)
    }

    // Animate camera ONLY on explicit GPS / search events (cameraMoveId increments)
    LaunchedEffect(cameraMoveId) {
        if (selectedLat != null && selectedLng != null)
            webView.evaluateJavaScript("map.getZoom()>$MAP_ZOOM_LOCATION?map.panTo([$selectedLat,$selectedLng]):map.flyTo([$selectedLat,$selectedLng],$MAP_ZOOM_LOCATION)", completionHandler = null)
    }

    UIKitView(factory = { webView }, modifier = modifier)
}

private fun mapPickerHtml(initialLat: Double?, initialLng: Double?, isDark: Boolean): String {
    val centerLat = initialLat ?: 20.0
    val centerLng = initialLng ?: 0.0
    val zoom = if (initialLat != null) MAP_ZOOM_LOCATION else MAP_ZOOM_MIN
    val markerInit = if (initialLat != null && initialLng != null)
        "marker=L.marker([$initialLat,$initialLng],{draggable:true}).addTo(map);attachDragEnd(marker);"
    else ""
    val tileUrl = if (isDark)
        "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
    else
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    val tileAttr = if (isDark)
        "© OpenStreetMap contributors © CARTO"
    else
        "© OpenStreetMap contributors"
    return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#map{margin:0;padding:0;width:100%;height:100%}</style>
</head><body>
<div id="map"></div>
<script>
var marker=null;
var tileLayer=null;
var map=L.map('map').setView([$centerLat,$centerLng],$zoom);
function setTileLayer(dark){
  if(tileLayer){map.removeLayer(tileLayer);}
  var url=dark?'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png':'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
  var attr=dark?'© OpenStreetMap contributors © CARTO':'© OpenStreetMap contributors';
  tileLayer=L.tileLayer(url,{attribution:attr}).addTo(map);
}
setTileLayer($isDark);
function attachDragEnd(m){
  m.on('dragend',function(){var ll=m.getLatLng();window.webkit.messageHandlers.locationPicker.postMessage(ll.lat+','+ll.lng);});
}
$markerInit
map.on('click',function(e){
  var lat=e.latlng.lat,lng=e.latlng.lng;
  if(marker){marker.setLatLng([lat,lng]);}else{marker=L.marker([lat,lng],{draggable:true}).addTo(map);attachDragEnd(marker);}
  window.webkit.messageHandlers.locationPicker.postMessage(lat+','+lng);
});
function updateMarker(lat,lng){
  if(marker){marker.setLatLng([lat,lng]);}else{marker=L.marker([lat,lng],{draggable:true}).addTo(map);attachDragEnd(marker);}
}
</script></body></html>
""".trimIndent()
}
