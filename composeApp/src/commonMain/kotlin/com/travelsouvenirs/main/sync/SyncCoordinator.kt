package com.travelsouvenirs.main.sync

import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.network.NetworkMonitor
import com.travelsouvenirs.main.util.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates the two-phase sync: metadata first, then images.
 * Delegates the actual work to [MetadataSyncService], [ImageSyncService], and [CategorySyncService].
 */
class SyncCoordinator(
    private val cloudImageStorage: CloudImageStorage,
    private val authRepository: AuthRepository,
    private val appSettings: AppSettings,
    private val networkMonitor: NetworkMonitor,
    private val metadataSync: MetadataSyncService,
    private val imageSync: ImageSyncService,
    private val categorySync: CategorySyncService,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var syncJob = SupervisorJob()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isSyncingImages = MutableStateFlow(false)
    val isSyncingImages: StateFlow<Boolean> = _isSyncingImages.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    init {
        // Trigger sync whenever the signed-in user changes from null → non-null.
        // Lives in a singleton scope so it catches sign-in regardless of which screen is active.
        scope.launch {
            var prevUid: String? = null
            authRepository.currentUser.collect { user ->
                val uid = user?.uid
                if (uid == null) {
                    cancelAllSyncs()
                } else if (prevUid == null) {
                    syncData()
                    launch { syncImages() }
                }
                prevUid = uid
            }
        }
    }

    private fun canSync(): Boolean {
        if (!networkMonitor.isConnected.value) return false
        return !appSettings.wifiOnlySync || networkMonitor.isWifi.value
    }

    /** Cancels any ongoing sync operations immediately. */
    fun cancelAllSyncs() {
        syncJob.cancel()
        syncJob = SupervisorJob()
        _isSyncing.value = false
        _isSyncingImages.value = false
    }

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
        val currentJob = Job(syncJob)
        try {
            withContext(currentJob) {
                _isSyncing.value = true
                // Categories must be synced first so every item's category FK parent exists locally.
                categorySync.sync(userId)
                coroutineContext.ensureActive()
                metadataSync.downloadRemoteMetadata(userId)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SyncCoordinator: metadata sync failed: $e")
            _errors.tryEmit("Sync failed. Check your connection.")
        } finally {
            _isSyncing.value = false
            currentJob.complete()
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
        val currentJob = Job(syncJob)
        try {
            withContext(currentJob) {
                _isSyncingImages.value = true
                metadataSync.uploadPending(userId, cloudImageStorage)
                coroutineContext.ensureActive()
                imageSync.downloadMissingImages()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("SyncCoordinator: image sync failed: $e")
            _errors.tryEmit("Sync failed. Check your connection.")
        } finally {
            _isSyncingImages.value = false
            currentJob.complete()
        }
    }
}
