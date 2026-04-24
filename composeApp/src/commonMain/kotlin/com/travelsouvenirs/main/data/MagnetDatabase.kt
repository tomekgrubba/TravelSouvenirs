package com.travelsouvenirs.main.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY

/** Room database holding the [MagnetDao]; constructed via platform-specific builder. */
@Database(entities = [MagnetEntity::class], version = 2, exportSchema = true)
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

/** Adds the `category` column introduced in schema version 2. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE magnets ADD COLUMN category TEXT NOT NULL DEFAULT '$DEFAULT_CATEGORY'"
        )
    }
}

/** Builds the database from the platform-specific builder with explicit migrations. */
fun buildMagnetDatabase(): MagnetDatabase =
    createMagnetDatabaseBuilder()
        .addMigrations(MIGRATION_1_2)
        .build()
