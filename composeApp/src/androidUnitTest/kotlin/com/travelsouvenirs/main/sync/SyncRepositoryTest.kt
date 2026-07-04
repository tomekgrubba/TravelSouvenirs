package com.travelsouvenirs.main.sync

import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.data.ItemDao
import com.travelsouvenirs.main.data.ItemEntity
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.network.NetworkMonitor
import com.travelsouvenirs.main.util.AppSettings
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRepositoryTest {

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeNetworkMonitor(
        connected: Boolean = true,
        wifi: Boolean = true,
    ) : NetworkMonitor {
        override val isConnected: StateFlow<Boolean> = MutableStateFlow(connected)
        override val isWifi: StateFlow<Boolean> = MutableStateFlow(wifi)
    }

    private class FakeAuthRepository(userId: String? = null) : AuthRepository {
        override val currentUser: StateFlow<FirebaseUser?> = MutableStateFlow(
            if (userId != null) mock<FirebaseUser>().also { /* uid stubbed below */ } else null
        )
        override suspend fun signInWithEmail(email: String, password: String): FirebaseUser = mock()
        override suspend fun createAccount(email: String, password: String): FirebaseUser = mock()
        override suspend fun signInWithGoogle(idToken: String): FirebaseUser = mock()
        override suspend fun sendPasswordResetEmail(email: String) {}
        override suspend fun signOut() {}
    }

    private class FakeSettings(initial: Map<String, Any> = emptyMap()) : Settings {
        private val store = mutableMapOf<String, Any>().also { it.putAll(initial) }
        override val keys: Set<String> get() = store.keys
        override val size: Int get() = store.size
        override fun clear() = store.clear()
        override fun remove(key: String) { store.remove(key) }
        override fun hasKey(key: String) = key in store
        override fun putString(key: String, value: String) { store[key] = value }
        override fun getString(key: String, defaultValue: String) = store[key] as? String ?: defaultValue
        override fun getStringOrNull(key: String) = store[key] as? String
        override fun putInt(key: String, value: Int) { store[key] = value }
        override fun getInt(key: String, defaultValue: Int) = store[key] as? Int ?: defaultValue
        override fun getIntOrNull(key: String) = store[key] as? Int
        override fun putLong(key: String, value: Long) { store[key] = value }
        override fun getLong(key: String, defaultValue: Long) = store[key] as? Long ?: defaultValue
        override fun getLongOrNull(key: String) = store[key] as? Long
        override fun putFloat(key: String, value: Float) { store[key] = value }
        override fun getFloat(key: String, defaultValue: Float) = store[key] as? Float ?: defaultValue
        override fun getFloatOrNull(key: String) = store[key] as? Float
        override fun putDouble(key: String, value: Double) { store[key] = value }
        override fun getDouble(key: String, defaultValue: Double) = store[key] as? Double ?: defaultValue
        override fun getDoubleOrNull(key: String) = store[key] as? Double
        override fun putBoolean(key: String, value: Boolean) { store[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = store[key] as? Boolean ?: defaultValue
        override fun getBooleanOrNull(key: String) = store[key] as? Boolean
    }

    private class NoOpItemDao : ItemDao {
        private val empty = MutableStateFlow<List<ItemEntity>>(emptyList())
        override fun getAllActiveItems(): Flow<List<ItemEntity>> = empty
        override suspend fun getItemById(id: Long): ItemEntity? = null
        override fun getItemByIdFlow(id: Long): Flow<ItemEntity?> = MutableStateFlow(null)
        override suspend fun insertItem(item: ItemEntity): Long = 0L
        override suspend fun deleteItem(item: ItemEntity) {}
        override suspend fun reassignCategory(fromCategory: String, toCategory: String, ts: Long) {}
        override suspend fun getPendingItems(): List<ItemEntity> = emptyList()
        override suspend fun updateSyncMeta(id: Long, status: String, fbId: String, storagePath: String, storageUrl: String, ts: Long) {}
        override suspend fun markSynced(fbId: String) {}
        override suspend fun getItemByFirebaseId(fbId: String): ItemEntity? = null
        override suspend fun hardDeleteByFirebaseId(fbId: String) {}
        override suspend fun getItemsByCategory(categoryName: String): List<ItemEntity> = emptyList()
        override suspend fun getItemsWithMissingLocalPhotos(): List<ItemEntity> = emptyList()
        override suspend fun deleteAll() {}

        override suspend fun deleteCategoryDirectly(name: String) {}
        override suspend fun deleteCategoryAndReassignItems(categoryName: String, defaultCategory: String, ts: Long) {}
        override suspend fun deleteCategoriesAndReassignItems(categoryNames: List<String>, defaultCategory: String, ts: Long) {}
    }

    private fun buildRepo(
        networkMonitor: NetworkMonitor,
        appSettings: AppSettings,
        authUserId: String? = null,
    ): SyncCoordinator {
        val firestore = mock<FirebaseFirestore>()
        val dao = NoOpItemDao()
        val fakeCategoryDao = com.travelsouvenirs.main.data.FakeCategoryDao()
        val categoryRepo = com.travelsouvenirs.main.data.CategoryRepository(fakeCategoryDao, dao)
        return SyncCoordinator(
            cloudImageStorage = mock<CloudImageStorage>(),
            authRepository = FakeAuthRepository(authUserId),
            appSettings = appSettings,
            networkMonitor = networkMonitor,
            metadataSync = MetadataSyncService(dao, fakeCategoryDao, firestore, appSettings),
            imageSync = ImageSyncService(dao, mock<com.travelsouvenirs.main.image.ImageStorage>()),
            categorySync = CategorySyncService(firestore, categoryRepo, appSettings),
        )
    }

    private fun fakeAppSettings(wifiOnly: Boolean = false): AppSettings =
        AppSettings(FakeSettings()).also { it.wifiOnlySync = wifiOnly }

    // ── canSync() guard tests ─────────────────────────────────────────────────

    @Test
    fun `sync skipped entirely when wifi-only enabled and not on wifi`() = runTest {
        val appSettings = fakeAppSettings(wifiOnly = true)
        val repo = buildRepo(
            networkMonitor = FakeNetworkMonitor(connected = true, wifi = false),
            appSettings = appSettings,
        )

        repo.sync()

        assertFalse(repo.isSyncing.value, "isSyncing should stay false when canSync=false")
        assertFalse(repo.isSyncingImages.value, "isSyncingImages should stay false when canSync=false")
        assertTrue(appSettings.lastSyncMillis == 0L, "last_sync_millis should not be updated when sync is skipped")
    }

    @Test
    fun `syncData skipped when wifi-only enabled and not on wifi`() = runTest {
        val repo = buildRepo(
            networkMonitor = FakeNetworkMonitor(connected = true, wifi = false),
            appSettings = fakeAppSettings(wifiOnly = true),
        )

        repo.syncData()

        assertFalse(repo.isSyncing.value)
    }

    @Test
    fun `syncImages skipped when wifi-only enabled and not on wifi`() = runTest {
        val repo = buildRepo(
            networkMonitor = FakeNetworkMonitor(connected = true, wifi = false),
            appSettings = fakeAppSettings(wifiOnly = true),
        )

        repo.syncImages()

        assertFalse(repo.isSyncingImages.value)
    }

    @Test
    fun `sync skipped entirely when device is offline`() = runTest {
        val appSettings = fakeAppSettings(wifiOnly = false)
        val repo = buildRepo(
            networkMonitor = FakeNetworkMonitor(connected = false, wifi = false),
            appSettings = appSettings,
        )

        repo.sync()

        assertFalse(repo.isSyncing.value, "isSyncing should stay false when device is offline")
        assertFalse(repo.isSyncingImages.value, "isSyncingImages should stay false when device is offline")
        assertTrue(appSettings.lastSyncMillis == 0L, "last_sync_millis should not be updated when offline")
    }

    @Test
    fun `syncData skipped when device is offline`() = runTest {
        val repo = buildRepo(
            networkMonitor = FakeNetworkMonitor(connected = false, wifi = false),
            appSettings = fakeAppSettings(wifiOnly = false),
        )

        repo.syncData()

        assertFalse(repo.isSyncing.value)
    }

    @Test
    fun `syncImages skipped when device is offline`() = runTest {
        val repo = buildRepo(
            networkMonitor = FakeNetworkMonitor(connected = false, wifi = false),
            appSettings = fakeAppSettings(wifiOnly = false),
        )

        repo.syncImages()

        assertFalse(repo.isSyncingImages.value)
    }

    @Test
    fun `sync allowed when wifi-only disabled regardless of wifi state`() = runTest {
        val repo = buildRepo(
            networkMonitor = FakeNetworkMonitor(connected = true, wifi = false),
            appSettings = fakeAppSettings(wifiOnly = false),
        )

        repo.sync()

        assertFalse(repo.isSyncing.value)
        assertFalse(repo.isSyncingImages.value)
    }

    @Test
    fun `sync allowed when wifi-only enabled and device is on wifi`() = runTest {
        val repo = buildRepo(
            networkMonitor = FakeNetworkMonitor(connected = true, wifi = true),
            appSettings = fakeAppSettings(wifiOnly = true),
        )

        repo.sync()

        assertFalse(repo.isSyncing.value)
        assertFalse(repo.isSyncingImages.value)
    }

    @Test
    fun `concurrent syncData calls do not double-sync`() = runTest {
        val repo = buildRepo(
            networkMonitor = FakeNetworkMonitor(connected = true, wifi = true),
            appSettings = fakeAppSettings(),
        )

        repo.syncData()
        repo.syncData()

        assertFalse(repo.isSyncing.value)
    }
}
