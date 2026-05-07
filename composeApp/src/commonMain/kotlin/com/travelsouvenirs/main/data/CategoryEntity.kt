package com.travelsouvenirs.main.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val updatedAtMillis: Long = 0L,
)
