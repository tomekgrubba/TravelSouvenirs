package com.travelsouvenirs.main.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.domain.Item
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Room entity that maps to the `items` table; dates stored as epoch milliseconds. */
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
    val category: String = DEFAULT_CATEGORY
)

/** Maps this Room entity to the domain [Item] model. */
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
    category = category
)

/** Maps this domain [Item] to a Room entity for persistence. */
fun Item.toEntity(): ItemEntity = ItemEntity(
    id = id,
    name = name,
    notes = notes,
    photoPath = photoPath,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    dateAcquiredMillis = dateAcquired.atStartOfDayIn(TimeZone.UTC)
        .toEpochMilliseconds(),
    category = category
)
