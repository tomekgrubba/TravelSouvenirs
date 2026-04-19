package com.travelsouvenirs.main.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MagnetDao {
    @Query("SELECT * FROM magnets ORDER BY dateAcquiredMillis DESC")
    fun getAllMagnets(): Flow<List<MagnetEntity>>

    @Query("SELECT * FROM magnets WHERE id = :id")
    suspend fun getMagnetById(id: Long): MagnetEntity?

    @Query("SELECT * FROM magnets WHERE id = :id")
    fun getMagnetByIdFlow(id: Long): Flow<MagnetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMagnet(magnet: MagnetEntity): Long

    @Delete
    suspend fun deleteMagnet(magnet: MagnetEntity)
}
