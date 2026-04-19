package com.travelsouvenirs.main.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A geocoded search result with a display name and coordinates. */
data class PlaceResult(val name: String, val latitude: Double, val longitude: Double)

/** Wraps FusedLocationProviderClient and Android Geocoder for location and search operations. */
class LocationHelper(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Returns the device's current coordinates, or null if unavailable. Requires location permission. */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .build()
            client.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    if (location != null) cont.resume(Pair(location.latitude, location.longitude))
                    else cont.resume(null)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    /** Resolves coordinates to a human-readable place name; falls back to "lat, lng" on failure. */
    suspend fun reverseGeocode(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val name = addresses.firstOrNull()?.let { addr ->
                            addr.locality ?: addr.subAdminArea ?: addr.adminArea
                        } ?: "%.4f, %.4f".format(lat, lng)
                        cont.resume(name)
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    addresses?.firstOrNull()?.let { addr ->
                        addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    } ?: "%.4f, %.4f".format(lat, lng)
                }
            }
        } catch (e: Exception) {
            "%.4f, %.4f".format(lat, lng)
        }
    }

    /** Geocodes [query] and returns up to 5 matching [PlaceResult]s; returns empty list on failure. */
    suspend fun searchByName(query: String): List<PlaceResult> {
        if (query.length < 2) return emptyList()
        return try {
            val geocoder = Geocoder(context)
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 5) { cont.resume(it) }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 5) ?: emptyList()
                }
            }
            addresses.map { addr ->
                PlaceResult(
                    name = listOfNotNull(addr.locality, addr.adminArea, addr.countryName)
                        .joinToString(", "),
                    latitude = addr.latitude,
                    longitude = addr.longitude
                )
            }.filter { it.name.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
