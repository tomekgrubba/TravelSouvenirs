package com.travelsouvenirs.main.data

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

actual fun createMagnetDatabaseBuilder(): RoomDatabase.Builder<MagnetDatabase> =
    Room.databaseBuilder<MagnetDatabase>(
        name = NSHomeDirectory() + "/Documents/magnets_db"
    )
