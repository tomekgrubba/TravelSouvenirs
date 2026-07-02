package com.travelsouvenirs.main.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    /** All non-deleted items ordered by acquisition date descending. */
    @Query("SELECT * FROM items WHERE syncStatus != 'PENDING_DELETE' ORDER BY dateAcquired DESC")
    fun getAllActiveItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemByIdFlow(id: Long): Flow<ItemEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity): Long

    @Delete
    suspend fun deleteItem(item: ItemEntity)

    @Query("UPDATE items SET category = :toCategory, syncStatus = 'PENDING_UPLOAD', updatedAtMillis = :ts WHERE category = :fromCategory AND syncStatus != 'PENDING_DELETE'")
    suspend fun reassignCategory(fromCategory: String, toCategory: String, ts: Long)

    /** Items that need to be pushed to or removed from Firebase. */
    @Query("SELECT * FROM items WHERE syncStatus IN ('PENDING_UPLOAD', 'PENDING_DELETE')")
    suspend fun getPendingItems(): List<ItemEntity>

    /** Updates sync metadata after a successful upload. */
    @Query("UPDATE items SET syncStatus = :status, firebaseId = :fbId, photoStoragePath = :storagePath, photoStorageUrl = :storageUrl, updatedAtMillis = :ts WHERE id = :id")
    suspend fun updateSyncMeta(id: Long, status: String, fbId: String, storagePath: String, storageUrl: String, ts: Long)

    /** Marks an item as SYNCED by its Firebase ID. */
    @Query("UPDATE items SET syncStatus = 'SYNCED' WHERE firebaseId = :fbId")
    suspend fun markSynced(fbId: String)

    /** Returns the item matching the given Firebase ID, or null. */
    @Query("SELECT * FROM items WHERE firebaseId = :fbId LIMIT 1")
    suspend fun getItemByFirebaseId(fbId: String): ItemEntity?

    /** Permanently removes a soft-deleted item after Firebase confirms deletion. */
    @Query("DELETE FROM items WHERE firebaseId = :fbId AND firebaseId != ''")
    suspend fun hardDeleteByFirebaseId(fbId: String)

    /** Returns all items associated with a given category. */
    @Query("SELECT * FROM items WHERE category = :categoryName")
    suspend fun getItemsByCategory(categoryName: String): List<ItemEntity>


    /** Items that have a remote photo URL but no local image file yet. */
    @Query("SELECT * FROM items WHERE photoStorageUrl != '' AND photoPath = '' AND syncStatus != 'PENDING_DELETE'")
    suspend fun getItemsWithMissingLocalPhotos(): List<ItemEntity>

    @Query("DELETE FROM items")
    suspend fun deleteAll()

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteCategoryDirectly(name: String)

    @Transaction
    suspend fun deleteCategoryAndReassignItems(categoryName: String, defaultCategory: String, ts: Long) {
        reassignCategory(categoryName, defaultCategory, ts)
        deleteCategoryDirectly(categoryName)
    }

    @Transaction
    suspend fun deleteCategoriesAndReassignItems(categoryNames: List<String>, defaultCategory: String, ts: Long) {
        categoryNames.forEach { name ->
            reassignCategory(name, defaultCategory, ts)
            deleteCategoryDirectly(name)
        }
    }
}
