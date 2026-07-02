package com.travelsouvenirs.main.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.domain.Item

@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["name"],
        childColumns = ["category"],
        onDelete = ForeignKey.SET_DEFAULT,
    )],
    indices = [Index("category")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val notes: String,
    val photoPath: String,
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val dateAcquired: String? = null,
    val category: String = DEFAULT_CATEGORY,
    val firebaseId: String = "",
    val syncStatus: String = "PENDING_UPLOAD",
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
    dateAcquired = dateAcquired,
    category = category,
)

fun Item.toEntity(): ItemEntity = ItemEntity(
    id = id,
    name = name,
    notes = notes,
    photoPath = photoPath,
    latitude = latitude,
    longitude = longitude,
    placeName = placeName,
    dateAcquired = dateAcquired,
    category = category,
)
