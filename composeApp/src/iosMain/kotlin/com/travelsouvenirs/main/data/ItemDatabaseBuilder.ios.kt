package com.travelsouvenirs.main.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSHomeDirectory

actual fun createItemDatabaseBuilder(): RoomDatabase.Builder<ItemDatabase> =
    Room.databaseBuilder<ItemDatabase>(
        name = NSHomeDirectory() + "/Documents/items_db"
    ).setDriver(BundledSQLiteDriver())
