package com.travelsouvenirs.main.data

import androidx.room.Room
import androidx.room.RoomDatabase
import com.travelsouvenirs.main.TravelSouvenirsApp

actual fun createMagnetDatabaseBuilder(): RoomDatabase.Builder<MagnetDatabase> =
    Room.databaseBuilder<MagnetDatabase>(
        context = TravelSouvenirsApp.instance,
        name = "magnets_db"
    )
