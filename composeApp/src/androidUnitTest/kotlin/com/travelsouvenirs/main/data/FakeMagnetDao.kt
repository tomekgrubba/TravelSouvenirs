package com.travelsouvenirs.main.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMagnetDao : MagnetDao {

    private val store = mutableMapOf<Long, MagnetEntity>()
    private val allFlow = MutableStateFlow<List<MagnetEntity>>(emptyList())
    private var nextId = 1L

    private fun publish() { allFlow.value = store.values.toList() }

    override fun getAllMagnets(): Flow<List<MagnetEntity>> = allFlow

    override suspend fun getMagnetById(id: Long): MagnetEntity? = store[id]

    override fun getMagnetByIdFlow(id: Long): Flow<MagnetEntity?> =
        allFlow.map { list -> list.find { it.id == id } }

    override suspend fun insertMagnet(magnet: MagnetEntity): Long {
        val id = if (magnet.id == 0L) nextId++ else magnet.id
        store[id] = magnet.copy(id = id)
        publish()
        return id
    }

    override suspend fun deleteMagnet(magnet: MagnetEntity) {
        store.remove(magnet.id)
        publish()
    }
}
