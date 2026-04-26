package com.travelsouvenirs.main.data

import com.travelsouvenirs.main.domain.Item
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Mediates between the DAO and the rest of the app, mapping entities to domain objects. */
class ItemRepository(private val dao: ItemDao) {

    /** Live stream of all items; emits whenever the database changes. */
    val allItems: Flow<List<Item>> = dao.getAllItems().map { list ->
        list.map { it.toDomain() }
    }

    /** Returns a single item by [id], or null if not found. */
    suspend fun getItemById(id: Long): Item? = dao.getItemById(id)?.toDomain()

    /** Returns a live Flow for a single item, re-emitting whenever it is updated. */
    fun getItemByIdFlow(id: Long): Flow<Item?> = dao.getItemByIdFlow(id).map { it?.toDomain() }

    /** Inserts a new item or replaces an existing one (upsert); returns the row id. */
    suspend fun insertItem(item: Item): Long = dao.insertItem(item.toEntity())

    /** Permanently removes [item] from the database. */
    suspend fun deleteItem(item: Item) = dao.deleteItem(item.toEntity())

    /** Moves all items assigned to [fromCategory] to [toCategory]. */
    suspend fun reassignCategory(fromCategory: String, toCategory: String) =
        dao.reassignCategory(fromCategory, toCategory)
}
