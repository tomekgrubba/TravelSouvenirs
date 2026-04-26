package com.travelsouvenirs.main.data

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

actual fun createItemDatabaseBuilder(): RoomDatabase.Builder<ItemDatabase> =
    Room.databaseBuilder<ItemDatabase>(
        name = NSHomeDirectory() + "/Documents/items_db"
    )
