package com.travelsouvenirs.main.ui.list

import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.data.FakeItemDao
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeSettings : Settings {
    private val map = mutableMapOf<String, Any>()
    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size
    override fun clear() = map.clear()
    override fun remove(key: String) { map.remove(key) }
    override fun hasKey(key: String) = map.containsKey(key)
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int) = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String) = map[key] as? Int
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long) = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String) = map[key] as? Long
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String, defaultValue: String) = map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String) = map[key] as? String
    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getFloat(key: String, defaultValue: Float) = map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String) = map[key] as? Float
    override fun putDouble(key: String, value: Double) { map[key] = value }
    override fun getDouble(key: String, defaultValue: Double) = map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String) = map[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean) = map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String) = map[key] as? Boolean
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: FakeItemDao
    private lateinit var viewModel: ListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeItemDao()
        viewModel = ListViewModel(FakeSettings(), ItemRepository(dao))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.activate() = backgroundScope.launch(testDispatcher) {
        viewModel.sortedItems.collect {}
    }

    @Test
    fun `all items returned sorted alphabetically by name when query is empty`() = runTest {
        activate()
        dao.insertItem(item("Zebra", 1))
        dao.insertItem(item("Apple", 2))
        dao.insertItem(item("Mango", 3))

        assertEquals(listOf("Apple", "Mango", "Zebra"), viewModel.sortedItems.value.map { it.name })
    }

    @Test
    fun `sorting is case-insensitive`() = runTest {
        activate()
        dao.insertItem(item("zebra", 1))
        dao.insertItem(item("Apple", 2))

        assertEquals(listOf("Apple", "zebra"), viewModel.sortedItems.value.map { it.name })
    }

    @Test
    fun `filter by name is case-insensitive`() = runTest {
        activate()
        dao.insertItem(item("Berlin Wall", 1))
        dao.insertItem(item("Eiffel Tower", 2))

        viewModel.onQueryChange("eiffel")

        assertEquals(1, viewModel.sortedItems.value.size)
        assertEquals("Eiffel Tower", viewModel.sortedItems.value[0].name)
    }

    @Test
    fun `filter by place name`() = runTest {
        activate()
        dao.insertItem(item("Item A", 1, place = "Amsterdam"))
        dao.insertItem(item("Item B", 2, place = "Budapest"))

        viewModel.onQueryChange("buda")

        assertEquals(1, viewModel.sortedItems.value.size)
        assertEquals("Item B", viewModel.sortedItems.value[0].name)
    }

    @Test
    fun `filter by notes`() = runTest {
        activate()
        dao.insertItem(item("Item A", 1, notes = "bought at the harbour"))
        dao.insertItem(item("Item B", 2, notes = "gift from Anna"))

        viewModel.onQueryChange("harbour")

        assertEquals(1, viewModel.sortedItems.value.size)
        assertEquals("Item A", viewModel.sortedItems.value[0].name)
    }

    @Test
    fun `clearing query restores full list`() = runTest {
        activate()
        dao.insertItem(item("Alpha", 1))
        dao.insertItem(item("Beta", 2))

        viewModel.onQueryChange("Alpha")
        assertEquals(1, viewModel.sortedItems.value.size)

        viewModel.onQueryChange("")
        assertEquals(2, viewModel.sortedItems.value.size)
    }

    @Test
    fun `query with no matching items returns empty list`() = runTest {
        activate()
        dao.insertItem(item("Paris", 1))

        viewModel.onQueryChange("xyz_not_found")

        assertTrue(viewModel.sortedItems.value.isEmpty())
    }

    @Test
    fun `empty list when no items exist`() = runTest {
        activate()
        assertTrue(viewModel.sortedItems.value.isEmpty())
    }

    private fun item(
        name: String,
        id: Long,
        place: String = "City",
        notes: String = "",
        dateString: String = "2024-01-01"
    ) = com.travelsouvenirs.main.data.ItemEntity(
        id = id,
        name = name,
        notes = notes,
        photoPath = "/photos/$id.jpg",
        latitude = 0.0,
        longitude = 0.0,
        placeName = place,
        dateAcquiredMillis = LocalDate.parse(dateString)
            .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    )

    @Test
    fun `sorting by date returns items descending by dateAcquired`() = runTest {
        activate()
        dao.insertItem(item("A", 1, dateString = "2024-01-05"))
        dao.insertItem(item("B", 2, dateString = "2024-01-10"))
        dao.insertItem(item("C", 3, dateString = "2024-01-01"))

        viewModel.onSortChange(SortOption.DATE)

        // Should be B (10th), A (5th), C (1st)
        assertEquals(listOf("B", "A", "C"), viewModel.sortedItems.value.map { it.name })
    }

    @Test
    fun `sorting by location returns items alphabetically by place name`() = runTest {
        activate()
        dao.insertItem(item("A", 1, place = "Zurich"))
        dao.insertItem(item("B", 2, place = "Amsterdam"))
        dao.insertItem(item("C", 3, place = "Paris"))

        viewModel.onSortChange(SortOption.LOCATION)

        // Should be Amsterdam (B), Paris (C), Zurich (A)
        assertEquals(listOf("B", "C", "A"), viewModel.sortedItems.value.map { it.name })
    }
}
