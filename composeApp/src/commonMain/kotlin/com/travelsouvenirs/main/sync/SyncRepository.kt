package com.travelsouvenirs.main.sync

import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.data.ItemDao
import com.travelsouvenirs.main.data.ItemEntity
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.network.NetworkMonitor
import com.travelsouvenirs.main.ui.shared.UserMessageBus
import com.travelsouvenirs.main.util.KEY_CATEGORIES
import com.travelsouvenirs.main.util.KEY_WIFI_ONLY_SYNC
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlin.random.Random

private const val KEY_CATEGORIES_UPDATED = "categories_updated_at"
private const val KEY_LAST_SYNC_MILLIS = "last_sync_millis"

class SyncRepository(
    private val dao: ItemDao,
    private val firestore: FirebaseFirestore,
    val imageSyncHelper: ImageSyncHelper,
    private val authRepository: AuthRepository,
    private val settings: Settings,
    private val imageStorage: ImageStorage,
    private val networkMonitor: NetworkMonitor,
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private fun canSync(): Boolean {
        val wifiOnly = settings.getBoolean(KEY_WIFI_ONLY_SYNC, false)
        return !wifiOnly || networkMonitor.isWifi.value
    }

    private val _isSyncingImages = MutableStateFlow(false)
    val isSyncingImages: StateFlow<Boolean> = _isSyncingImages.asStateFlow()

    /** Full bidirectional sync (metadata then images). Used for periodic background sync. */
    suspend fun sync() {
        if (!canSync()) return
        syncData()
        syncImages()
    }

    /**
     * Phase 1: Syncs item metadata and categories from Firestore without downloading image files.
     * Fast; blocks the UI until complete.
     */
    suspend fun syncData() {
        if (!canSync()) return
        if (_isSyncing.value) return
        val userId = authRepository.currentUser.value?.uid ?: return
        _isSyncing.value = true
        try {
            downloadRemoteMetadata(userId)
            syncCategories(userId)
        } catch (_: Exception) {
            UserMessageBus.send("Sync failed. Check your connection.")
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Phase 2: Uploads pending items (with photos) and downloads missing local image files.
     * Runs in the background; shows a small indicator in the UI.
     */
    suspend fun syncImages() {
        if (!canSync()) return
        if (_isSyncingImages.value) return
        val userId = authRepository.currentUser.value?.uid ?: return
        _isSyncingImages.value = true
        try {
            uploadPending(userId)
            downloadMissingImages()
        } catch (_: Exception) {
            UserMessageBus.send("Sync failed. Check your connection.")
        } finally {
            _isSyncingImages.value = false
        }
    }

    private suspend fun uploadPending(userId: String) {
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
                    val now = Clock.System.now().toEpochMilliseconds()

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

    /** Downloads item metadata from Firestore without fetching image files from Storage. */
    private suspend fun downloadRemoteMetadata(userId: String) {
        val lastSync = settings.getLong(KEY_LAST_SYNC_MILLIS, 0L)
        val now = Clock.System.now().toEpochMilliseconds()
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
                // Keep existing local photo path; image download happens in syncImages()
                val localPhotoPath = local?.photoPath ?: ""
                val merged = buildEntity(fbId, remote, localPhotoPath, local?.id ?: 0)
                dao.insertItem(merged)
            }
        }
        settings.putLong(KEY_LAST_SYNC_MILLIS, now)
    }

    /** Downloads image files for items that have a remote URL but no local photo file yet. */
    private suspend fun downloadMissingImages() {
        val items = dao.getItemsWithMissingLocalPhotos()
        for (entity in items) {
            try {
                val localPath = imageStorage.localPathForDownload(entity.firebaseId)
                downloadUrlToFile(entity.photoStorageUrl, localPath)
                dao.insertItem(entity.copy(photoPath = localPath))
            } catch (_: Exception) { }
        }
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

    private suspend fun syncCategories(userId: String) {
        val remoteRef = firestore
            .collection("users").document(userId)
            .collection("settings").document("categories")
        val localUpdatedAt = settings.getLong(KEY_CATEGORIES_UPDATED, 0L)

        try {
            val snapshot = remoteRef.get()
            if (snapshot.exists) {
                val remoteUpdatedAt = snapshot.get<Long>("updatedAtMillis")
                val remoteList = snapshot.get<List<String>>("list")
                if (remoteUpdatedAt > localUpdatedAt) {
                    val categoriesString = remoteList.filter { it != DEFAULT_CATEGORY }.joinToString(",")
                    settings.putString(KEY_CATEGORIES, categoriesString)
                    settings.putLong(KEY_CATEGORIES_UPDATED, remoteUpdatedAt)
                    return
                }
            }
        } catch (_: Exception) { /* document may not exist yet on first run */ }

        val localCategories = (settings.getStringOrNull(KEY_CATEGORIES) ?: "")
            .split(",").filter { it.isNotBlank() }
        val now = Clock.System.now().toEpochMilliseconds()
        remoteRef.set(
            mapOf(
                "list" to listOf(DEFAULT_CATEGORY) + localCategories,
                "updatedAtMillis" to now,
            )
        )
        settings.putLong(KEY_CATEGORIES_UPDATED, now)
    }
}

private fun randomId(): String = buildString {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    repeat(28) { append(chars[Random.nextInt(chars.length)]) }
}
