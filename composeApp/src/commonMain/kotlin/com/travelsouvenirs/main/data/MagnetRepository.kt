package com.travelsouvenirs.main.data

import com.travelsouvenirs.main.domain.Magnet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Mediates between the DAO and the rest of the app, mapping entities to domain objects. */
class MagnetRepository(private val dao: MagnetDao) {

    /** Live stream of all items; emits whenever the database changes. */
    val allMagnets: Flow<List<Magnet>> = dao.getAllMagnets().map { list ->
        list.map { it.toDomain() }
    }

    /** Returns a single item by [id], or null if not found. */
    suspend fun getMagnetById(id: Long): Magnet? = dao.getMagnetById(id)?.toDomain()

    /** Returns a live Flow for a single item, re-emitting whenever it is updated. */
    fun getMagnetByIdFlow(id: Long): Flow<Magnet?> = dao.getMagnetByIdFlow(id).map { it?.toDomain() }

    /** Inserts a new item or replaces an existing one (upsert); returns the row id. */
    suspend fun insertMagnet(magnet: Magnet): Long = dao.insertMagnet(magnet.toEntity())

    /** Permanently removes [magnet] from the database. */
    suspend fun deleteMagnet(magnet: Magnet) = dao.deleteMagnet(magnet.toEntity())

    /** Moves all items assigned to [fromCategory] to [toCategory]. */
    suspend fun reassignCategory(fromCategory: String, toCategory: String) =
        dao.reassignCategory(fromCategory, toCategory)
}
