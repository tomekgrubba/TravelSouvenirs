package com.travelsouvenirs.main.sync

import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.network.NetworkMonitor
import com.travelsouvenirs.main.util.AppSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates the two-phase sync: metadata first, then images.
 * Delegates the actual work to [MetadataSyncService], [ImageSyncService], and [CategorySyncService].
 */
class SyncCoordinator(
    val imageSyncHelper: ImageSyncHelper,
    private val authRepository: AuthRepository,
    private val appSettings: AppSettings,
    private val networkMonitor: NetworkMonitor,
    private val metadataSync: MetadataSyncService,
    private val imageSync: ImageSyncService,
    private val categorySync: CategorySyncService,
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isSyncingImages = MutableStateFlow(false)
    val isSyncingImages: StateFlow<Boolean> = _isSyncingImages.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private fun canSync(): Boolean = !appSettings.wifiOnlySync || networkMonitor.isWifi.value

    /** Full bidirectional sync (metadata then images). Used for periodic background sync. */
    suspend fun sync() {
        if (!canSync()) return
        syncData()
        syncImages()
    }

    /**
     * Phase 1: Syncs item metadata and categories from Firestore without downloading image files.
     * Fast; can be awaited before navigating.
     */
    suspend fun syncData() {
        if (!canSync()) return
        if (_isSyncing.value) return
        val userId = authRepository.currentUser.value?.uid ?: return
        _isSyncing.value = true
        try {
            // Categories must be synced first so every item's category FK parent exists locally.
            categorySync.sync(userId)
            metadataSync.downloadRemoteMetadata(userId)
        } catch (_: Exception) {
            _errors.tryEmit("Sync failed. Check your connection.")
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Phase 2: Uploads pending items (with photos) and downloads missing local image files.
     * Runs in the background.
     */
    suspend fun syncImages() {
        if (!canSync()) return
        if (_isSyncingImages.value) return
        val userId = authRepository.currentUser.value?.uid ?: return
        _isSyncingImages.value = true
        try {
            metadataSync.uploadPending(userId, imageSyncHelper)
            imageSync.downloadMissingImages()
        } catch (_: Exception) {
            _errors.tryEmit("Sync failed. Check your connection.")
        } finally {
            _isSyncingImages.value = false
        }
    }
}
