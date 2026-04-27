package com.travelsouvenirs.main.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.sync.SyncStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val notes: String,
    val photoPath: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val dateAcquiredMillis: Long,
    val category: String = DEFAULT_CATEGORY,
    val firebaseId: String = "",
    val syncStatus: String = SyncStatus.PENDING_UPLOAD.name,
    val updatedAtMillis: Long = 0L,
    val photoStoragePath: String = "",
    val photoStorageUrl: String = "",
)

fun ItemEntity.toDomain(): Item = Item(
    id = id,
    name = name,
    notes = notes,
    photoPath = photoPath,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    dateAcquired = Instant.fromEpochMilliseconds(dateAcquiredMillis)
        .toLocalDateTime(TimeZone.UTC).date,
    category = category,
    firebaseId = firebaseId,
    syncStatus = SyncStatus.valueOf(syncStatus),
    updatedAtMillis = updatedAtMillis,
    photoStoragePath = photoStoragePath,
    photoStorageUrl = photoStorageUrl,
)

fun Item.toEntity(): ItemEntity = ItemEntity(
    id = id,
    name = name,
    notes = notes,
    photoPath = photoPath,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    dateAcquiredMillis = dateAcquired.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
    category = category,
    firebaseId = firebaseId,
    syncStatus = syncStatus.name,
    updatedAtMillis = updatedAtMillis,
    photoStoragePath = photoStoragePath,
    photoStorageUrl = photoStorageUrl,
)
