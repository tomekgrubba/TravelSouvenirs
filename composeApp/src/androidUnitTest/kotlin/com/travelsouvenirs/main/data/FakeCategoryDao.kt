package com.travelsouvenirs.main.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCategoryDao : CategoryDao {

    private val store = mutableSetOf<CategoryEntity>()
    private val allFlow = MutableStateFlow<List<CategoryEntity>>(emptyList())

    private fun publish() { allFlow.value = store.sortedBy { it.name } }

    override fun getAllCategories(): Flow<List<CategoryEntity>> = allFlow

    override suspend fun getAllNames(): List<String> = store.map { it.name }.sorted()

    override suspend fun insertCategory(entity: CategoryEntity) {
        store.removeIf { it.name == entity.name }
        store.add(entity)
        publish()
    }

    override suspend fun deleteCategory(name: String) {
        store.removeIf { it.name == name }
        publish()
    }

    override suspend fun deleteAll() {
        store.clear()
        publish()
    }

    override suspend fun deleteAllCustom(defaultName: String) {
        store.removeIf { it.name != defaultName }
        publish()
    }

    override suspend fun count(): Int = store.size

    override suspend fun updateCategoryTimestamp(name: String, ts: Long) {
        val existing = store.find { it.name == name }
        if (existing != null) {
            store.remove(existing)
            store.add(existing.copy(updatedAtMillis = ts))
            publish()
        }
    }

    override suspend fun insertOrUpdateCategory(entity: CategoryEntity) {
        val existing = store.find { it.name == entity.name }
        if (existing == null) {
            insertCategory(entity)
        } else {
            updateCategoryTimestamp(entity.name, entity.updatedAtMillis)
        }
    }
}
