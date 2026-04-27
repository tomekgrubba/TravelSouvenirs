package com.travelsouvenirs.main.data

import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class ItemRepository(private val dao: ItemDao) {

    val allItems: Flow<List<Item>> = dao.getAllActiveItems().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getItemById(id: Long): Item? = dao.getItemById(id)?.toDomain()

    fun getItemByIdFlow(id: Long): Flow<Item?> = dao.getItemByIdFlow(id).map { it?.toDomain() }

    suspend fun insertItem(item: Item): Long {
        val stamped = item.copy(
            syncStatus = SyncStatus.PENDING_UPLOAD,
            updatedAtMillis = Clock.System.now().toEpochMilliseconds(),
        )
        return dao.insertItem(stamped.toEntity())
    }

    /** Marks an item for deletion and lets the sync engine remove it from Firebase. */
    suspend fun deleteItem(item: Item) {
        if (item.firebaseId.isEmpty()) {
            // Never synced — safe to hard-delete immediately
            dao.deleteItem(item.toEntity())
        } else {
            // Mark as pending delete so SyncRepository can remove it from Firebase first
            val stamped = item.copy(
                syncStatus = SyncStatus.PENDING_DELETE,
                updatedAtMillis = Clock.System.now().toEpochMilliseconds(),
            )
            dao.insertItem(stamped.toEntity())
        }
    }

    suspend fun reassignCategory(fromCategory: String, toCategory: String) =
        dao.reassignCategory(fromCategory, toCategory)
}
