package com.travelsouvenirs.main.data

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase

/** Holds the application context; must be initialized in [MainActivity.onCreate] before the database is built. */
object AppContext {
    lateinit var application: Application
}

actual fun createMagnetDatabaseBuilder(): RoomDatabase.Builder<MagnetDatabase> =
    Room.databaseBuilder<MagnetDatabase>(
        context = AppContext.application,
        name = "magnets_db"
    )
