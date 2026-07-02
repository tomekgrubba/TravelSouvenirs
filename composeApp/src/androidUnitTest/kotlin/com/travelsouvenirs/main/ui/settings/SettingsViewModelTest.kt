package com.travelsouvenirs.main.ui.settings

import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.data.CategoryRepository
import com.travelsouvenirs.main.data.FakeCategoryDao
import com.travelsouvenirs.main.data.FakeItemDao
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.image.ImageLocationAnalyzer
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.util.AppSettings
import org.mockito.kotlin.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSettings(initial: Map<String, String> = emptyMap()) : Settings {

    private val store = mutableMapOf<String, Any>().also { it.putAll(initial) }

    override val keys: Set<String> get() = store.keys
    override val size: Int get() = store.size
    override fun clear() = store.clear()
    override fun remove(key: String) { store.remove(key) }
    override fun hasKey(key: String): Boolean = key in store

    override fun putString(key: String, value: String) { store[key] = value }
    override fun getString(key: String, defaultValue: String): String = store[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = store[key] as? String

    override fun putInt(key: String, value: Int) { store[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = store[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = store[key] as? Int

    override fun putLong(key: String, value: Long) { store[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = store[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = store[key] as? Long

    override fun putFloat(key: String, value: Float) { store[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float = store[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = store[key] as? Float

    override fun putDouble(key: String, value: Double) { store[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double = store[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = store[key] as? Double

    override fun putBoolean(key: String, value: Boolean) { store[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = store[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = store[key] as? Boolean
}

private class FakeImageLocationAnalyzer : ImageLocationAnalyzer {
    override suspend fun analyze(imagePath: String, lat: Double?, lng: Double?): String? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(
        settings: FakeSettings = FakeSettings(),
        categoryDao: FakeCategoryDao = FakeCategoryDao(),
        itemDao: FakeItemDao = FakeItemDao(),
    ) = SettingsViewModel(
        AppSettings(settings),
        ItemRepository(itemDao),
        CategoryRepository(categoryDao, itemDao),
        mock<AuthRepository>(),
        mock<ImageStorage>(),
        FakeImageLocationAnalyzer(),
        mock<com.travelsouvenirs.main.sync.SyncCoordinator>(),
    )

    @Test
    fun `initial notes is empty string when no key stored`() {
        assertEquals("", vm().notes.value)
    }

    @Test
    fun `initial notes loaded from pre-existing settings`() {
        val vm = vm(FakeSettings(mapOf("notes" to "My travel journal")))
        assertEquals("My travel journal", vm.notes.value)
    }

    @Test
    fun `onNotesChange updates notes state`() {
        val vm = vm()
        vm.onNotesChange("Remember Paris")
        assertEquals("Remember Paris", vm.notes.value)
    }

    @Test
    fun `onNotesChange persists value to settings`() {
        val settings = FakeSettings()
        vm(settings).onNotesChange("Persisted note")
        assertEquals("Persisted note", settings.getStringOrNull("notes"))
    }

    @Test
    fun `new ViewModel instance reads value persisted by previous instance`() {
        val settings = FakeSettings()
        vm(settings).onNotesChange("Saved text")
        assertEquals("Saved text", vm(settings).notes.value)
    }

    @Test
    fun `onNotesChange with empty string clears persisted value`() {
        val settings = FakeSettings(mapOf("notes" to "Old note"))
        val vm = vm(settings)
        vm.onNotesChange("")
        assertEquals("", vm.notes.value)
        assertEquals("", settings.getStringOrNull("notes"))
    }

    @Test
    fun `successive onNotesChange calls each update state and persistence`() {
        val settings = FakeSettings()
        val vm = vm(settings)
        vm.onNotesChange("First")
        vm.onNotesChange("Second")
        vm.onNotesChange("Third")
        assertEquals("Third", vm.notes.value)
        assertEquals("Third", settings.getStringOrNull("notes"))
    }

    @Test
    fun `addCategory returns true and adds category`() = runTest {
        val vm = vm()
        assertTrue(vm.addCategory("Souvenir"))
        assertEquals(listOf("Souvenir"), vm.customCategories.value)
    }

    @Test
    fun `addCategory returns false for exact duplicate`() = runTest {
        val vm = vm()
        vm.addCategory("Souvenir")
        assertFalse(vm.addCategory("Souvenir"))
        assertEquals(1, vm.customCategories.value.size)
    }

    @Test
    fun `addCategory returns false for case-insensitive duplicate`() = runTest {
        val vm = vm()
        vm.addCategory("Souvenir")
        assertFalse(vm.addCategory("souvenir"))
        assertFalse(vm.addCategory("SOUVENIR"))
        assertEquals(1, vm.customCategories.value.size)
    }

    @Test
    fun `addCategory returns false when name matches default category`() = runTest {
        val vm = vm()
        assertFalse(vm.addCategory("Default"))
        assertFalse(vm.addCategory("default"))
        assertTrue(vm.customCategories.value.isEmpty())
    }

    @Test
    fun `addCategory returns false for blank input`() = runTest {
        val vm = vm()
        assertFalse(vm.addCategory("   "))
        assertTrue(vm.customCategories.value.isEmpty())
    }

    @Test
    fun `categories update reactively when another component writes to the dao`() = runTest {
        val dao = FakeCategoryDao()
        val vm = vm(categoryDao = dao)
        assertEquals(emptyList(), vm.customCategories.value)

        dao.insertCategory(com.travelsouvenirs.main.data.CategoryEntity("Souvenir"))
        assertEquals(listOf("Souvenir"), vm.customCategories.value)
    }

    @Test
    fun `settings key used is notes`() {
        val settings = FakeSettings()
        vm(settings).onNotesChange("value")
        assertNull(settings.getStringOrNull("note"))
        assertEquals("value", settings.getStringOrNull("notes"))
    }
}
