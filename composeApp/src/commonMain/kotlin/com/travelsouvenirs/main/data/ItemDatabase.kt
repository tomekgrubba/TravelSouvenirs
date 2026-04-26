package com.travelsouvenirs.main.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY

/** Room database holding the [ItemDao]; constructed via platform-specific builder. */
@Database(entities = [ItemEntity::class], version = 3, exportSchema = true)
@ConstructedBy(ItemDatabaseConstructor::class)
abstract class ItemDatabase : RoomDatabase() {
    /** Returns the DAO used for all item queries. */
    abstract fun itemDao(): ItemDao
}

/** Platform-generated constructor required by Room KMP. */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ItemDatabaseConstructor : RoomDatabaseConstructor<ItemDatabase> {
    override fun initialize(): ItemDatabase
}

/** Platform-specific builder; actual implementations supply the correct database path/context. */
expect fun createItemDatabaseBuilder(): RoomDatabase.Builder<ItemDatabase>

/** Adds the `category` column introduced in schema version 2. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE magnets ADD COLUMN category TEXT NOT NULL DEFAULT '$DEFAULT_CATEGORY'"
        )
    }
}

/** Renames the table from `magnets` to `items` introduced in schema version 3. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE magnets RENAME TO items")
    }
}

/** Builds the database from the platform-specific builder with explicit migrations. */
fun buildItemDatabase(): ItemDatabase =
    createItemDatabaseBuilder()
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
