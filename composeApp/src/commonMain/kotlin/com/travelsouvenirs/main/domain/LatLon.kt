package com.travelsouvenirs.main.domain

/** Platform-agnostic latitude/longitude pair; replaces GMS LatLng in shared code. */
data class LatLon(val lat: Double, val lng: Double)
