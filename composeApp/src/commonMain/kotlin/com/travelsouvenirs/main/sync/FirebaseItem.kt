package com.travelsouvenirs.main.sync

import kotlinx.serialization.Serializable

/** Mirrors the Firestore document structure under users/{userId}/items/{firebaseId}. */
@Serializable
data class FirebaseItem(
    val name: String = "",
    val notes: String = "",
    val photoStoragePath: String = "",
    val photoStorageUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val placeName: String = "",
    val dateAcquiredMillis: Long = 0L,
    val category: String = "",
    val updatedAtMillis: Long = 0L,
    val deleted: Boolean = false,
)
