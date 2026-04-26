package com.travelsouvenirs.main.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeItemDao : ItemDao {

    private val store = mutableMapOf<Long, ItemEntity>()
    private val allFlow = MutableStateFlow<List<ItemEntity>>(emptyList())
    private var nextId = 1L

    private fun publish() { allFlow.value = store.values.toList() }

    override fun getAllItems(): Flow<List<ItemEntity>> = allFlow

    override suspend fun getItemById(id: Long): ItemEntity? = store[id]

    override fun getItemByIdFlow(id: Long): Flow<ItemEntity?> =
        allFlow.map { list -> list.find { it.id == id } }

    override suspend fun insertItem(item: ItemEntity): Long {
        val id = if (item.id == 0L) nextId++ else item.id
        store[id] = item.copy(id = id)
        publish()
        return id
    }

    override suspend fun deleteItem(item: ItemEntity) {
        store.remove(item.id)
        publish()
    }

    override suspend fun reassignCategory(fromCategory: String, toCategory: String) {
        store.values.filter { it.category == fromCategory }.forEach {
            store[it.id] = it.copy(category = toCategory)
        }
        publish()
    }
}
