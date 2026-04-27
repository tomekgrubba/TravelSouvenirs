package com.travelsouvenirs.main.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.sync.SyncStatus

@Database(entities = [ItemEntity::class], version = 4, exportSchema = true)
@ConstructedBy(ItemDatabaseConstructor::class)
abstract class ItemDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ItemDatabaseConstructor : RoomDatabaseConstructor<ItemDatabase> {
    override fun initialize(): ItemDatabase
}

expect fun createItemDatabaseBuilder(): RoomDatabase.Builder<ItemDatabase>

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE magnets ADD COLUMN category TEXT NOT NULL DEFAULT '$DEFAULT_CATEGORY'"
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE magnets RENAME TO items")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE items ADD COLUMN firebaseId TEXT NOT NULL DEFAULT ''")
        connection.execSQL(
            "ALTER TABLE items ADD COLUMN syncStatus TEXT NOT NULL DEFAULT '${SyncStatus.PENDING_UPLOAD.name}'"
        )
        connection.execSQL("ALTER TABLE items ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE items ADD COLUMN photoStoragePath TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE items ADD COLUMN photoStorageUrl TEXT NOT NULL DEFAULT ''")
    }
}

fun buildItemDatabase(): ItemDatabase =
    createItemDatabaseBuilder()
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()
