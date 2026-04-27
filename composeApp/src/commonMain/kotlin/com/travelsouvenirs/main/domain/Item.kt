package com.travelsouvenirs.main.domain

import com.travelsouvenirs.main.sync.SyncStatus
import kotlinx.datetime.LocalDate

const val DEFAULT_CATEGORY = "Default"
/** Maximum number of user-defined custom categories (excluding Default). */
const val MAX_CUSTOM_CATEGORIES = 5

/** Domain model representing a single travel souvenir item. */
data class Item(
    val id: Long,
    val name: String,
    val notes: String,
    val photoPath: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val dateAcquired: LocalDate,
    val category: String = DEFAULT_CATEGORY,
    val firebaseId: String = "",
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
    val updatedAtMillis: Long = 0L,
    val photoStoragePath: String = "",
    val photoStorageUrl: String = "",
)
