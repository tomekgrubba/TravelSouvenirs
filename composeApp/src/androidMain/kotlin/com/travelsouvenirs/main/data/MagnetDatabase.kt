package com.travelsouvenirs.main.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MagnetEntity::class], version = 1, exportSchema = true)
abstract class MagnetDatabase : RoomDatabase() {
    abstract fun magnetDao(): MagnetDao

    companion object {
        @Volatile private var INSTANCE: MagnetDatabase? = null

        fun getDatabase(context: Context): MagnetDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MagnetDatabase::class.java,
                    "magnets_db"
                ).build().also { INSTANCE = it }
            }
    }
}
