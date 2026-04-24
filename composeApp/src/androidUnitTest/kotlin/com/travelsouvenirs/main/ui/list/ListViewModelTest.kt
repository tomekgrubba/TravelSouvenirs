package com.travelsouvenirs.main.ui.list

import com.travelsouvenirs.main.data.FakeMagnetDao
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.Magnet
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

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: FakeMagnetDao
    private lateinit var viewModel: ListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeMagnetDao()
        viewModel = ListViewModel(MagnetRepository(dao))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.activate() = backgroundScope.launch(testDispatcher) {
        viewModel.sortedMagnets.collect {}
    }

    @Test
    fun `all items returned sorted alphabetically by name when query is empty`() = runTest {
        activate()
        dao.insertMagnet(magnet("Zebra", 1))
        dao.insertMagnet(magnet("Apple", 2))
        dao.insertMagnet(magnet("Mango", 3))

        assertEquals(listOf("Apple", "Mango", "Zebra"), viewModel.sortedMagnets.value.map { it.name })
    }

    @Test
    fun `sorting is case-insensitive`() = runTest {
        activate()
        dao.insertMagnet(magnet("zebra", 1))
        dao.insertMagnet(magnet("Apple", 2))

        assertEquals(listOf("Apple", "zebra"), viewModel.sortedMagnets.value.map { it.name })
    }

    @Test
    fun `filter by name is case-insensitive`() = runTest {
        activate()
        dao.insertMagnet(magnet("Berlin Wall", 1))
        dao.insertMagnet(magnet("Eiffel Tower", 2))

        viewModel.onQueryChange("eiffel")

        assertEquals(1, viewModel.sortedMagnets.value.size)
        assertEquals("Eiffel Tower", viewModel.sortedMagnets.value[0].name)
    }

    @Test
    fun `filter by place name`() = runTest {
        activate()
        dao.insertMagnet(magnet("Item A", 1, place = "Amsterdam"))
        dao.insertMagnet(magnet("Item B", 2, place = "Budapest"))

        viewModel.onQueryChange("buda")

        assertEquals(1, viewModel.sortedMagnets.value.size)
        assertEquals("Item B", viewModel.sortedMagnets.value[0].name)
    }

    @Test
    fun `filter by notes`() = runTest {
        activate()
        dao.insertMagnet(magnet("Item A", 1, notes = "bought at the harbour"))
        dao.insertMagnet(magnet("Item B", 2, notes = "gift from Anna"))

        viewModel.onQueryChange("harbour")

        assertEquals(1, viewModel.sortedMagnets.value.size)
        assertEquals("Item A", viewModel.sortedMagnets.value[0].name)
    }

    @Test
    fun `clearing query restores full list`() = runTest {
        activate()
        dao.insertMagnet(magnet("Alpha", 1))
        dao.insertMagnet(magnet("Beta", 2))

        viewModel.onQueryChange("Alpha")
        assertEquals(1, viewModel.sortedMagnets.value.size)

        viewModel.onQueryChange("")
        assertEquals(2, viewModel.sortedMagnets.value.size)
    }

    @Test
    fun `query with no matching items returns empty list`() = runTest {
        activate()
        dao.insertMagnet(magnet("Paris", 1))

        viewModel.onQueryChange("xyz_not_found")

        assertTrue(viewModel.sortedMagnets.value.isEmpty())
    }

    @Test
    fun `empty list when no items exist`() = runTest {
        activate()
        assertTrue(viewModel.sortedMagnets.value.isEmpty())
    }

    private fun magnet(
        name: String,
        id: Long,
        place: String = "City",
        notes: String = "",
        dateString: String = "2024-01-01"
    ) = com.travelsouvenirs.main.data.MagnetEntity(
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
        dao.insertMagnet(magnet("A", 1, dateString = "2024-01-05"))
        dao.insertMagnet(magnet("B", 2, dateString = "2024-01-10"))
        dao.insertMagnet(magnet("C", 3, dateString = "2024-01-01"))

        viewModel.onSortChange(SortOption.DATE)

        // Should be B (10th), A (5th), C (1st)
        assertEquals(listOf("B", "A", "C"), viewModel.sortedMagnets.value.map { it.name })
    }

    @Test
    fun `sorting by location returns items alphabetically by place name`() = runTest {
        activate()
        dao.insertMagnet(magnet("A", 1, place = "Zurich"))
        dao.insertMagnet(magnet("B", 2, place = "Amsterdam"))
        dao.insertMagnet(magnet("C", 3, place = "Paris"))

        viewModel.onSortChange(SortOption.LOCATION)

        // Should be Amsterdam (B), Paris (C), Zurich (A)
        assertEquals(listOf("B", "C", "A"), viewModel.sortedMagnets.value.map { it.name })
    }
}
