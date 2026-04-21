package com.travelsouvenirs.main.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/** Room database holding the [MagnetDao]; constructed via platform-specific builder. */
@Database(entities = [MagnetEntity::class], version = 1, exportSchema = true)
@ConstructedBy(MagnetDatabaseConstructor::class)
abstract class MagnetDatabase : RoomDatabase() {
    /** Returns the DAO used for all item queries. */
    abstract fun magnetDao(): MagnetDao
}

/** Platform-generated constructor required by Room KMP. */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object MagnetDatabaseConstructor : RoomDatabaseConstructor<MagnetDatabase> {
    override fun initialize(): MagnetDatabase
}

/** Platform-specific builder; actual implementations supply the correct database path/context. */
expect fun createMagnetDatabaseBuilder(): RoomDatabase.Builder<MagnetDatabase>

/** Builds the database from the platform-specific builder with destructive migration on schema change. */
fun buildMagnetDatabase(): MagnetDatabase =
    createMagnetDatabaseBuilder()
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
