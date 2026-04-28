package com.travelsouvenirs.main.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.travelsouvenirs.main.domain.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Android implementation of [LocationService] using FusedLocationProviderClient and Geocoder. */
class AndroidLocationService(private val context: Context) : LocationService {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): LatLon? {
        val fresh = getFreshLocation()
        if (fresh != null) return fresh
        return getLastLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocation(): LatLon? {
        val cts = CancellationTokenSource()
        return try {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { cts.cancel() }
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setDurationMillis(5_000)
                    .setMaxUpdateAgeMillis(60_000)
                    .build()
                client.getCurrentLocation(request, cts.token)
                    .addOnSuccessListener { location ->
                        cont.resume(if (location != null) LatLon(location.latitude, location.longitude) else null)
                    }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (_: Exception) { null }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastLocation(): LatLon? = try {
        suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc ->
                    cont.resume(if (loc != null) LatLon(loc.latitude, loc.longitude) else null)
                }
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (_: Exception) { null }

    override suspend fun reverseGeocode(lat: Double, lng: Double): String {
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

    override suspend fun searchByName(query: String): List<PlaceResult> {
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
