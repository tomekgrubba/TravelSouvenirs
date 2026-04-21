package com.travelsouvenirs.main.location

import com.travelsouvenirs.main.domain.LatLon
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** iOS implementation of [LocationService] using CLLocationManager and CLGeocoder. */
@OptIn(ExperimentalForeignApi::class)
class IosLocationService : LocationService {

    override suspend fun getCurrentLocation(): LatLon? =
        suspendCancellableCoroutine { cont ->
            val manager = CLLocationManager()
            manager.desiredAccuracy = kCLLocationAccuracyBest
            manager.distanceFilter = kCLDistanceFilterNone

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>
                ) {
                    val loc = didUpdateLocations.lastOrNull() as? CLLocation
                    manager.stopUpdatingLocation()
                    manager.delegate = null
                    if (loc != null) {
                        loc.coordinate.useContents {
                            cont.resume(LatLon(latitude, longitude))
                        }
                    } else {
                        cont.resume(null)
                    }
                }

                override fun locationManager(
                    manager: CLLocationManager,
                    didFailWithError: platform.Foundation.NSError
                ) {
                    manager.stopUpdatingLocation()
                    manager.delegate = null
                    cont.resume(null)
                }
            }

            manager.delegate = delegate
            manager.requestWhenInUseAuthorization()
            manager.startUpdatingLocation()

            cont.invokeOnCancellation {
                manager.stopUpdatingLocation()
                manager.delegate = null
            }
        }

    override suspend fun reverseGeocode(lat: Double, lng: Double): String =
        suspendCancellableCoroutine { cont ->
            val geocoder = CLGeocoder()
            val location = CLLocation(
                latitude = lat,
                longitude = lng
            )
            geocoder.reverseGeocodeLocation(location) { placemarks, _ ->
                val mark = (placemarks as? List<*>)?.firstOrNull() as? platform.CoreLocation.CLPlacemark
                val name = mark?.locality ?: mark?.administrativeArea
                    ?: "%.4f, %.4f".format(lat, lng)
                cont.resume(name)
            }
            cont.invokeOnCancellation { geocoder.cancelGeocode() }
        }

    override suspend fun searchByName(query: String): List<PlaceResult> {
        if (query.length < 2) return emptyList()
        return suspendCancellableCoroutine { cont ->
            val geocoder = CLGeocoder()
            geocoder.geocodeAddressString(query) { placemarks, _ ->
                val results = (placemarks as? List<*>)
                    ?.take(5)
                    ?.filterIsInstance<platform.CoreLocation.CLPlacemark>()
                    ?.mapNotNull { mark ->
                        val name = listOfNotNull(mark.locality, mark.administrativeArea, mark.country)
                            .joinToString(", ")
                        if (name.isBlank()) return@mapNotNull null
                        mark.location?.coordinate?.useContents {
                            PlaceResult(name = name, latitude = latitude, longitude = longitude)
                        }
                    } ?: emptyList()
                cont.resume(results)
            }
            cont.invokeOnCancellation { geocoder.cancelGeocode() }
        }
    }
}
