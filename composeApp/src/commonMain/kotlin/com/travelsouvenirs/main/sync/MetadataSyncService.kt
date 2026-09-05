package com.travelsouvenirs.main.sync

import com.travelsouvenirs.main.data.CategoryDao
import com.travelsouvenirs.main.data.CategoryEntity
import com.travelsouvenirs.main.data.ItemDao
import com.travelsouvenirs.main.data.ItemEntity
import com.travelsouvenirs.main.util.AppSettings
import com.travelsouvenirs.main.util.nowEpochMillis
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

import com.travelsouvenirs.main.image.ImageStorage
import kotlinx.coroutines.CancellationException

/** Engine interface for syncing item metadata. */
interface MetadataSyncEngine {
    suspend fun downloadRemoteMetadata(userId: String)
    suspend fun uploadPending(userId: String, cloudImageStorage: CloudImageStorage)
}

/** Downloads item metadata from Firestore and uploads pending local changes. */
class MetadataSyncService(
    private val dao: ItemDao,
    private val categoryDao: CategoryDao,
    private val firestore: FirebaseFirestore,
    private val appSettings: AppSettings,
    private val imageStorage: ImageStorage? = null,
) : MetadataSyncEngine {
    override suspend fun downloadRemoteMetadata(userId: String) {
        val lastSync = appSettings.lastSyncMillis
        val now = nowEpochMillis()
        val collectionRef = firestore.collection("users").document(userId).collection("items")
        // Use a 5-second lookback buffer so items whose timestamps land at or just before
        // lastSync (due to cross-device clock skew) are still fetched. The local
        // updatedAtMillis comparison below prevents overwriting newer local edits.
        val snapshot = if (lastSync == 0L) collectionRef.get()
        else collectionRef.where { "updatedAtMillis" greaterThan (lastSync - 5_000L) }.get()

        for (doc in snapshot.documents) {
            val remote = doc.data<FirebaseItem>()
            val fbId = doc.id
            if (remote.deleted) {
                val local = dao.getItemByFirebaseId(fbId)
                if (local == null || remote.updatedAtMillis > local.updatedAtMillis) {
                    if (local?.photoPath?.isNotEmpty() == true) {
                        imageStorage?.deleteImage(local.photoPath)
                    }
                    dao.hardDeleteByFirebaseId(fbId)
                }
                continue
            }
            val local = dao.getItemByFirebaseId(fbId)
            if (local == null || remote.updatedAtMillis > local.updatedAtMillis) {
                // Preserve the local photo file path only if the remote storage URL hasn't changed.
                // If the URL changed, clean up the old file and clear photoPath so ImageSyncService
                // will re-download the updated image from the new URL.
                val remoteUrlChanged = local != null
                    && remote.photoStorageUrl.isNotEmpty()
                    && remote.photoStorageUrl != local.photoStorageUrl
                val localPhotoPath = if (remoteUrlChanged) {
                    if (local?.photoPath?.isNotEmpty() == true) {
                        imageStorage?.deleteImage(local.photoPath)
                    }
                    ""
                } else (local?.photoPath ?: "")
                // Ensure the category FK parent exists — categorySync runs first but remote data
                // may contain categories absent from the categories list (e.g. backend inconsistency).
                categoryDao.insertCategory(CategoryEntity(remote.category))
                dao.insertItem(buildEntity(fbId, remote, localPhotoPath, local?.id ?: 0))
            }
        }
        appSettings.lastSyncMillis = now
    }

    override suspend fun uploadPending(userId: String, cloudImageStorage: CloudImageStorage) {
        val pending = dao.getPendingItems()
        val itemsRef = firestore.collection("users").document(userId).collection("items")

        for (entity in pending) {
            try {
                when (SyncStatus.valueOf(entity.syncStatus)) {
                    SyncStatus.PENDING_DELETE -> {
                        if (entity.photoStoragePath.isNotEmpty()) {
                            try {
                                cloudImageStorage.delete(entity.photoStoragePath)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                            }
                        }
                        if (entity.firebaseId.isNotEmpty()) {
                            try {
                                itemsRef.document(entity.firebaseId)
                                    .update(mapOf("deleted" to true, "updatedAtMillis" to entity.updatedAtMillis))
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                try {
                                    itemsRef.document(entity.firebaseId)
                                        .set(mapOf("deleted" to true, "updatedAtMillis" to entity.updatedAtMillis))
                                } catch (inner: Exception) {
                                    if (inner is CancellationException) throw inner
                                }
                            }
                            dao.hardDeleteByFirebaseId(entity.firebaseId)
                        } else {
                            dao.deleteItem(entity)
                        }
                    }
                    SyncStatus.PENDING_UPLOAD -> {
                        val fbId = entity.firebaseId.ifEmpty { randomId() }
                        val now = nowEpochMillis()

                        var storagePath = entity.photoStoragePath
                        var storageUrl = entity.photoStorageUrl
                        if (entity.photoPath.isNotEmpty() && storagePath.isEmpty()) {
                            // storagePath is empty when: (a) this is a new item, or (b) the user
                            // replaced the photo (ItemRepository clears it on photo change).
                            val (path, url) = cloudImageStorage.upload(userId, fbId, entity.photoPath)
                            storagePath = path
                            storageUrl = url
                        }

                        val firebaseItem = FirebaseItem(
                            name = entity.name,
                            notes = entity.notes,
                            photoStoragePath = storagePath,
                            photoStorageUrl = storageUrl,
                            latitude = entity.latitude,
                            longitude = entity.longitude,
                            placeName = entity.placeName,
                            dateAcquired = entity.dateAcquired,
                            category = entity.category,
                            updatedAtMillis = now,
                            deleted = false,
                        )
                        itemsRef.document(fbId).set(firebaseItem)
                        val updatedRows = dao.updateSyncMetaIfUnmodified(
                            id = entity.id,
                            status = SyncStatus.SYNCED.name,
                            fbId = fbId,
                            storagePath = storagePath,
                            storageUrl = storageUrl,
                            ts = now,
                            expectedTs = entity.updatedAtMillis,
                        )
                        if (updatedRows == 0) {
                            // Item was modified concurrently during upload; keep PENDING_UPLOAD
                            dao.updateSyncMeta(
                                id = entity.id,
                                status = SyncStatus.PENDING_UPLOAD.name,
                                fbId = fbId,
                                storagePath = storagePath,
                                storageUrl = storageUrl,
                                ts = entity.updatedAtMillis,
                            )
                        }
                    }
                    SyncStatus.SYNCED -> Unit
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                println("MetadataSyncService: Failed to upload item ${entity.id}: $e")
            }
        }
    }

}

internal fun randomId(): String = buildString {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    repeat(28) { append(chars[Random.nextInt(chars.length)]) }
}

private fun buildEntity(
        fbId: String,
        remote: FirebaseItem,
        localPhotoPath: String,
        existingId: Long,
    ): ItemEntity = ItemEntity(
        id = existingId,
        name = remote.name,
        notes = remote.notes,
        photoPath = localPhotoPath,
        latitude = remote.latitude,
        longitude = remote.longitude,
        placeName = remote.placeName,
        dateAcquired = remote.dateAcquired ?: if (remote.dateAcquiredMillis > 0) {
            Instant.fromEpochMilliseconds(remote.dateAcquiredMillis)
                .toLocalDateTime(TimeZone.UTC).date.toString()
        } else null,
        category = remote.category,
        firebaseId = fbId,
        syncStatus = SyncStatus.SYNCED.name,
        updatedAtMillis = remote.updatedAtMillis,
        photoStoragePath = remote.photoStoragePath,
        photoStorageUrl = remote.photoStorageUrl,
    )
