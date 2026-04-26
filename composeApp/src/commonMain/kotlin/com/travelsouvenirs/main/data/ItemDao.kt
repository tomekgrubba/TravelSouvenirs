package com.travelsouvenirs.main.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room DAO for all souvenir item persistence operations. */
@Dao
interface ItemDao {
    /** Returns a live Flow of all items ordered by acquisition date descending. */
    @Query("SELECT * FROM items ORDER BY dateAcquiredMillis DESC")
    fun getAllItems(): Flow<List<ItemEntity>>

    /** Returns a single item by [id], or null if it does not exist. */
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Long): ItemEntity?

    /** Returns a live Flow for a single item by [id], emitting on every update. */
    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemByIdFlow(id: Long): Flow<ItemEntity?>

    /** Inserts or replaces an item; returns the row id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    /** Permanently removes an item from the database. */
    @Delete
    suspend fun deleteItem(item: ItemEntity)

    /** Moves all items assigned to [fromCategory] to [toCategory]. */
    @Query("UPDATE items SET category = :toCategory WHERE category = :fromCategory")
    suspend fun reassignCategory(fromCategory: String, toCategory: String)
}
