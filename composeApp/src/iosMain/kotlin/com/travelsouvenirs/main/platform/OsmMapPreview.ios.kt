package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import androidx.compose.runtime.key

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun OsmMapPreview(latitude: Double, longitude: Double, label: String, modifier: Modifier) {
    val mapTheme = rememberMapTheme()
    val isDark = mapTheme == MapTheme.DARK
    key(isDark) {
        val webView = remember {
            WKWebView(frame = CGRectZero.readValue(), configuration = WKWebViewConfiguration()).apply {
                scrollView.scrollEnabled = false
                scrollView.bounces = false
                setUserInteractionEnabled(false)
                loadHTMLString(osmPreviewHtml(latitude, longitude, label, isDark), baseURL = null)
            }
        }
        UIKitView(factory = { webView }, modifier = modifier)
    }
}

private fun osmPreviewHtml(lat: Double, lng: Double, label: String, isDark: Boolean): String {
    val safeLabel = label.replace("\\", "\\\\").replace("'", "\\'")
    val tileUrl = if (isDark)
        "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
    else
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    val tileAttr = if (isDark)
        "&copy; OpenStreetMap contributors &copy; CARTO"
    else
        "&copy; OpenStreetMap contributors"
    val subdomains = if (isDark) """,subdomains:'abcd'""" else ""
    return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#map{margin:0;padding:0;width:100%;height:100%;pointer-events:none}</style>
</head><body>
<div id="map"></div>
<script>
var map=L.map('map',{
  zoomControl:false,dragging:false,scrollWheelZoom:false,
  touchZoom:false,doubleClickZoom:false,boxZoom:false,keyboard:false
}).setView([$lat,$lng],5);
L.tileLayer('$tileUrl',{attribution:'$tileAttr'$subdomains}).addTo(map);
L.marker([$lat,$lng]).bindTooltip('$safeLabel',{permanent:true}).addTo(map);
</script></body></html>
""".trimIndent()
}
