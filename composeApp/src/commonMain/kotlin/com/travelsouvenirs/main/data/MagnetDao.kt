package com.travelsouvenirs.main.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room DAO for all souvenir item persistence operations. */
@Dao
interface MagnetDao {
    /** Returns a live Flow of all items ordered by acquisition date descending. */
    @Query("SELECT * FROM magnets ORDER BY dateAcquiredMillis DESC")
    fun getAllMagnets(): Flow<List<MagnetEntity>>

    /** Returns a single item by [id], or null if it does not exist. */
    @Query("SELECT * FROM magnets WHERE id = :id")
    suspend fun getMagnetById(id: Long): MagnetEntity?

    /** Returns a live Flow for a single item by [id], emitting on every update. */
    @Query("SELECT * FROM magnets WHERE id = :id")
    fun getMagnetByIdFlow(id: Long): Flow<MagnetEntity?>

    /** Inserts or replaces an item; returns the row id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMagnet(magnet: MagnetEntity): Long

    /** Permanently removes an item from the database. */
    @Delete
    suspend fun deleteMagnet(magnet: MagnetEntity)

    /** Moves all items assigned to [fromCategory] to [toCategory]. */
    @Query("UPDATE magnets SET category = :toCategory WHERE category = :fromCategory")
    suspend fun reassignCategory(fromCategory: String, toCategory: String)
}
