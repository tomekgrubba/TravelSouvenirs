package com.travelsouvenirs.main.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Room database; single-instance, holds the [MagnetDao]. */
@Database(entities = [MagnetEntity::class], version = 1, exportSchema = true)
abstract class MagnetDatabase : RoomDatabase() {
    /** Returns the DAO used for all item queries. */
    abstract fun magnetDao(): MagnetDao

    companion object {
        @Volatile private var INSTANCE: MagnetDatabase? = null

        /** Returns the singleton database instance, creating it on first call. */
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
