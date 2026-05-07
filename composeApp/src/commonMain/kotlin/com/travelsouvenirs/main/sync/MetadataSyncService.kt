package com.travelsouvenirs.main.sync

import com.travelsouvenirs.main.data.CategoryDao
import com.travelsouvenirs.main.data.CategoryEntity
import com.travelsouvenirs.main.data.ItemDao
import com.travelsouvenirs.main.data.ItemEntity
import com.travelsouvenirs.main.util.AppSettings
import com.travelsouvenirs.main.util.nowEpochMillis
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

/** Downloads item metadata from Firestore and uploads pending local changes. */
class MetadataSyncService(
    private val dao: ItemDao,
    private val categoryDao: CategoryDao,
    private val firestore: FirebaseFirestore,
    private val appSettings: AppSettings,
) {
    suspend fun downloadRemoteMetadata(userId: String) {
        val lastSync = appSettings.lastSyncMillis
        val now = nowEpochMillis()
        val collectionRef = firestore.collection("users").document(userId).collection("items")
        val snapshot = if (lastSync == 0L) collectionRef.get()
        else collectionRef.where { "updatedAtMillis" greaterThan lastSync }.get()

        for (doc in snapshot.documents) {
            val remote = doc.data<FirebaseItem>()
            val fbId = doc.id
            if (remote.deleted) {
                dao.hardDeleteByFirebaseId(fbId)
                continue
            }
            val local = dao.getItemByFirebaseId(fbId)
            if (local == null || remote.updatedAtMillis > local.updatedAtMillis) {
                val localPhotoPath = local?.photoPath ?: ""
                // Ensure the category FK parent exists — categorySync runs first but remote data
                // may contain categories absent from the categories list (e.g. backend inconsistency).
                categoryDao.insertCategory(CategoryEntity(remote.category))
                dao.insertItem(buildEntity(fbId, remote, localPhotoPath, local?.id ?: 0))
            }
        }
        appSettings.lastSyncMillis = now
    }

    suspend fun uploadPending(userId: String, imageSyncHelper: ImageSyncHelper) {
        val pending = dao.getPendingItems()
        val itemsRef = firestore.collection("users").document(userId).collection("items")

        for (entity in pending) {
            when (SyncStatus.valueOf(entity.syncStatus)) {
                SyncStatus.PENDING_DELETE -> {
                    if (entity.firebaseId.isNotEmpty()) {
                        itemsRef.document(entity.firebaseId)
                            .update(mapOf("deleted" to true, "updatedAtMillis" to entity.updatedAtMillis))
                    }
                    dao.hardDeleteByFirebaseId(entity.firebaseId)
                }
                SyncStatus.PENDING_UPLOAD -> {
                    val fbId = entity.firebaseId.ifEmpty { randomId() }
                    val now = nowEpochMillis()

                    var storagePath = entity.photoStoragePath
                    var storageUrl = entity.photoStorageUrl
                    if (entity.photoPath.isNotEmpty() && storagePath.isEmpty()) {
                        val (path, url) = imageSyncHelper.upload(userId, fbId, entity.photoPath)
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
                        dateAcquiredMillis = entity.dateAcquiredMillis,
                        category = entity.category,
                        updatedAtMillis = now,
                        deleted = false,
                    )
                    itemsRef.document(fbId).set(firebaseItem)
                    dao.updateSyncMeta(
                        id = entity.id,
                        status = SyncStatus.SYNCED.name,
                        fbId = fbId,
                        storagePath = storagePath,
                        storageUrl = storageUrl,
                        ts = now,
                    )
                }
                SyncStatus.SYNCED -> Unit
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
        dateAcquiredMillis = remote.dateAcquiredMillis,
        category = remote.category,
        firebaseId = fbId,
        syncStatus = SyncStatus.SYNCED.name,
        updatedAtMillis = remote.updatedAtMillis,
        photoStoragePath = remote.photoStoragePath,
        photoStorageUrl = remote.photoStorageUrl,
    )
