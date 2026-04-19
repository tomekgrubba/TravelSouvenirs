package com.travelsouvenirs.main.domain

import kotlinx.datetime.LocalDate

/** Domain model representing a single travel souvenir item. */
data class Magnet(
    val id: Long,
    val name: String,
    val notes: String,
    val photoPath: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val dateAcquired: LocalDate
)
