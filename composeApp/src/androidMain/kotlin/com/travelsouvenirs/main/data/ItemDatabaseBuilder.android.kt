package com.travelsouvenirs.main.data

import androidx.room.Room
import androidx.room.RoomDatabase
import com.travelsouvenirs.main.TravelSouvenirsApp

actual fun createItemDatabaseBuilder(): RoomDatabase.Builder<ItemDatabase> =
    Room.databaseBuilder<ItemDatabase>(
        context = TravelSouvenirsApp.instance,
        name = "items_db"
    )
