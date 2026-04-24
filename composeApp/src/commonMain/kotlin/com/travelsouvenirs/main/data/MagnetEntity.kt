package com.travelsouvenirs.main.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.domain.Magnet
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Room entity that maps to the `magnets` table; dates stored as epoch milliseconds. */
@Entity(tableName = "magnets")
data class MagnetEntity(
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

/** Maps this Room entity to the domain [Magnet] model. */
fun MagnetEntity.toDomain(): Magnet = Magnet(
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

/** Maps this domain [Magnet] to a Room entity for persistence. */
fun Magnet.toEntity(): MagnetEntity = MagnetEntity(
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
