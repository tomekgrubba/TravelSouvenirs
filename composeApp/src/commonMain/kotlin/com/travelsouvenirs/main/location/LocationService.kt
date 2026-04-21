package com.travelsouvenirs.main.location

import com.travelsouvenirs.main.domain.LatLon

/** A geocoded search result returned by forward geocoding. */
data class PlaceResult(val name: String, val latitude: Double, val longitude: Double)

/** Platform-agnostic interface for location and geocoding operations. */
interface LocationService {
    /** Returns the device's current coordinates, or null if unavailable or permission denied. */
    suspend fun getCurrentLocation(): LatLon?
    /** Resolves [lat]/[lng] to a human-readable place name; falls back to "lat, lng" on failure. */
    suspend fun reverseGeocode(lat: Double, lng: Double): String
    /** Forward-geocodes [query] and returns up to 5 matching results; empty list on failure. */
    suspend fun searchByName(query: String): List<PlaceResult>
}
