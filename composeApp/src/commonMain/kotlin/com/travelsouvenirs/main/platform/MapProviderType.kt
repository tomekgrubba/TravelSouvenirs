package com.travelsouvenirs.main.platform

enum class MapProviderType {
    /** Google Maps on Android, Apple MapKit on iOS. */
    NATIVE,
    /** OpenStreetMap tiles: osmdroid on Android, Leaflet.js via WKWebView on iOS. */
    OPEN_STREET_MAP;

    companion object {
        const val SETTINGS_KEY = "map_provider"
        val DEFAULT = NATIVE
        fun fromString(value: String?) = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
