package com.travelsouvenirs.main.data

import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.TimeZone

class ItemRepository(private val dao: ItemDao) {

    val allItems: Flow<List<Item>> = dao.getAllActiveItems().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getItemById(id: Long): Item? = dao.getItemById(id)?.toDomain()

    fun getItemByIdFlow(id: Long): Flow<Item?> = dao.getItemByIdFlow(id).map { it?.toDomain() }

    suspend fun insertItem(item: Item): Long {
        val now = Clock.System.now().toEpochMilliseconds()
        val existing: ItemEntity? = if (item.id != 0L) dao.getItemById(item.id) else null
        val entity = ItemEntity(
            id = item.id,
            name = item.name,
            notes = item.notes,
            photoPath = item.photoPath,
            latitude = item.latitude,
            longitude = item.longitude,
            placeName = item.placeName,
            dateAcquiredMillis = item.dateAcquired.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            category = item.category,
            firebaseId = existing?.firebaseId ?: "",
            syncStatus = SyncStatus.PENDING_UPLOAD.name,
            updatedAtMillis = now,
            photoStoragePath = existing?.photoStoragePath ?: "",
            photoStorageUrl = existing?.photoStorageUrl ?: "",
        )
        return dao.insertItem(entity)
    }

    /** Marks an item for deletion and lets the sync engine remove it from Firebase. */
    suspend fun deleteItem(item: Item) {
        val existing = dao.getItemById(item.id) ?: return
        if (existing.firebaseId.isEmpty()) {
            // Never synced — safe to hard-delete immediately
            dao.deleteItem(existing)
        } else {
            // Mark as pending delete so SyncRepository can remove it from Firebase first
            dao.insertItem(existing.copy(
                syncStatus = SyncStatus.PENDING_DELETE.name,
                updatedAtMillis = Clock.System.now().toEpochMilliseconds(),
            ))
        }
    }

    suspend fun reassignCategory(fromCategory: String, toCategory: String) =
        dao.reassignCategory(fromCategory, toCategory)
}
