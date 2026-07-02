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

@Database(entities = [ItemEntity::class, CategoryEntity::class], version = 7, exportSchema = true)
@ConstructedBy(ItemDatabaseConstructor::class)
abstract class ItemDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao
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

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`name` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`name`))"
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "INSERT OR IGNORE INTO categories (name, updatedAtMillis) VALUES ('$DEFAULT_CATEGORY', 0)"
        )
        connection.execSQL(
            "INSERT OR IGNORE INTO categories (name, updatedAtMillis) SELECT DISTINCT category, 0 FROM items WHERE category != '$DEFAULT_CATEGORY'"
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `items_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `photoPath` TEXT NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `placeName` TEXT NOT NULL,
                `dateAcquiredMillis` INTEGER NOT NULL,
                `category` TEXT NOT NULL DEFAULT '$DEFAULT_CATEGORY',
                `firebaseId` TEXT NOT NULL DEFAULT '',
                `syncStatus` TEXT NOT NULL DEFAULT 'PENDING_UPLOAD',
                `updatedAtMillis` INTEGER NOT NULL DEFAULT 0,
                `photoStoragePath` TEXT NOT NULL DEFAULT '',
                `photoStorageUrl` TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(`category`) REFERENCES `categories`(`name`) ON DELETE SET DEFAULT
            )
            """.trimIndent()
        )
        connection.execSQL("INSERT INTO items_new SELECT * FROM items")
        connection.execSQL("DROP TABLE items")
        connection.execSQL("ALTER TABLE items_new RENAME TO items")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_items_category` ON `items` (`category`)")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `items_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `photoPath` TEXT NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `placeName` TEXT NOT NULL,
                `dateAcquired` TEXT,
                `category` TEXT NOT NULL,
                `firebaseId` TEXT NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `updatedAtMillis` INTEGER NOT NULL,
                `photoStoragePath` TEXT NOT NULL,
                `photoStorageUrl` TEXT NOT NULL,
                FOREIGN KEY(`category`) REFERENCES `categories`(`name`) ON UPDATE NO ACTION ON DELETE SET DEFAULT
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO items_new (
                id, name, notes, photoPath, latitude, longitude, placeName,
                dateAcquired, category, firebaseId, syncStatus, updatedAtMillis,
                photoStoragePath, photoStorageUrl
            )
            SELECT
                id, name, notes, photoPath, latitude, longitude, placeName,
                CASE WHEN dateAcquiredMillis > 0 THEN date(dateAcquiredMillis / 1000, 'unixepoch') ELSE NULL END,
                category, firebaseId, syncStatus, updatedAtMillis,
                photoStoragePath, photoStorageUrl
            FROM items
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE items")
        connection.execSQL("ALTER TABLE items_new RENAME TO items")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_items_category` ON `items` (`category`)")
    }
}

fun buildItemDatabase(): ItemDatabase =
    createItemDatabaseBuilder()
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                connection.execSQL("INSERT OR IGNORE INTO `categories` (name, updatedAtMillis) VALUES ('$DEFAULT_CATEGORY', 0)")
            }
            override fun onOpen(connection: SQLiteConnection) {
                connection.execSQL("PRAGMA foreign_keys = ON")
            }
        })
        .build()
