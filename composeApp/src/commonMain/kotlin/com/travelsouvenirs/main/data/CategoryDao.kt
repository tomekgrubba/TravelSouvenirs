package com.travelsouvenirs.main.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT name FROM categories ORDER BY name ASC")
    suspend fun getAllNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(entity: CategoryEntity)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteCategory(name: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("DELETE FROM categories WHERE name != :defaultName")
    suspend fun deleteAllCustom(defaultName: String)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("UPDATE categories SET updatedAtMillis = :ts WHERE name = :name")
    suspend fun updateCategoryTimestamp(name: String, ts: Long)

    @Transaction
    suspend fun insertOrUpdateCategory(entity: CategoryEntity) {
        insertCategory(entity)
        updateCategoryTimestamp(entity.name, entity.updatedAtMillis)
    }
}
