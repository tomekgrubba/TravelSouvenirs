package com.travelsouvenirs.main.data

import com.travelsouvenirs.main.domain.Magnet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MagnetRepository(private val dao: MagnetDao) {

    val allMagnets: Flow<List<Magnet>> = dao.getAllMagnets().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getMagnetById(id: Long): Magnet? = dao.getMagnetById(id)?.toDomain()

    suspend fun insertMagnet(magnet: Magnet): Long = dao.insertMagnet(magnet.toEntity())

    suspend fun deleteMagnet(magnet: Magnet) = dao.deleteMagnet(magnet.toEntity())
}
