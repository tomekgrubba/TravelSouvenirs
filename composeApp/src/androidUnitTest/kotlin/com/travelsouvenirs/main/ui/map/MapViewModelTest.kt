package com.travelsouvenirs.main.ui.map

import com.travelsouvenirs.main.data.FakeItemDao
import com.travelsouvenirs.main.data.ItemEntity
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: FakeItemDao
    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeItemDao()
        viewModel = MapViewModel(ItemRepository(dao))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.activate() {
        backgroundScope.launch(testDispatcher) { viewModel.itemPins.collect {} }
        backgroundScope.launch(testDispatcher) { viewModel.itemGroups.collect {} }
        backgroundScope.launch(testDispatcher) { viewModel.items.collect {} }
    }

    private fun entity(id: Long, lat: Double, lng: Double, name: String = "Item $id") = ItemEntity(
        id = id,
        name = name,
        notes = "",
        photoPath = "/photos/$id.jpg",
        latitude = lat,
        longitude = lng,
        placeName = "City",
        dateAcquiredMillis = 0L
    )

    private fun item(id: Long, lat: Double, lng: Double, name: String = "Item $id") = Item(
        id = id,
        name = name,
        notes = "",
        photoPath = "/photos/$id.jpg",
        latitude = lat,
        longitude = lng,
        placeName = "City",
        dateAcquired = LocalDate(2024, 1, 1)
    )

    // ── groupByZoom (pure companion function) ────────────────────────────────

    @Test
    fun `groupByZoom returns empty list for empty input`() {
        assertTrue(MapViewModel.groupByZoom(emptyList(), 10f).isEmpty())
    }

    @Test
    fun `groupByZoom returns single group for single item`() {
        val groups = MapViewModel.groupByZoom(listOf(item(1, 48.0, 2.0)), 10f)
        assertEquals(1, groups.size)
        assertEquals(1, groups[0].items.size)
    }

    @Test
    fun `groupByZoom clusters nearby items at low zoom`() {
        // At zoom=1 the threshold is ~42 degrees, so items 0.0001 apart merge
        val m1 = item(1, 48.0, 2.0)
        val m2 = item(2, 48.0001, 2.0001)
        val groups = MapViewModel.groupByZoom(listOf(m1, m2), 1f)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].items.size)
    }

    @Test
    fun `groupByZoom keeps distant items separate at high zoom`() {
        val m1 = item(1, 48.85, 2.35)   // Paris
        val m2 = item(2, 51.51, -0.12)  // London
        val groups = MapViewModel.groupByZoom(listOf(m1, m2), 15f)
        assertEquals(2, groups.size)
    }

    @Test
    fun `groupByZoom center is average of member coordinates`() {
        val m1 = item(1, 48.0, 2.0)
        val m2 = item(2, 48.0001, 2.0001)
        val groups = MapViewModel.groupByZoom(listOf(m1, m2), 1f)
        assertEquals(1, groups.size)
        assertEquals((48.0 + 48.0001) / 2, groups[0].centerLat, 1e-9)
        assertEquals((2.0 + 2.0001) / 2, groups[0].centerLng, 1e-9)
    }

    @Test
    fun `groupByZoom single-item group center equals item coordinates`() {
        val m = item(1, 51.51, -0.12)
        val groups = MapViewModel.groupByZoom(listOf(m), 15f)
        assertEquals(51.51, groups[0].centerLat, 1e-9)
        assertEquals(-0.12, groups[0].centerLng, 1e-9)
    }

    @Test
    fun `groupByZoom items that cluster at low zoom stay separate at high zoom`() {
        val m1 = item(1, 48.0, 2.0)
        val m2 = item(2, 48.05, 2.05)
        // At zoom=1 they should cluster; at zoom=12 threshold is ~0.017 so 0.05 apart = separate
        val lowZoom = MapViewModel.groupByZoom(listOf(m1, m2), 1f)
        val highZoom = MapViewModel.groupByZoom(listOf(m1, m2), 12f)
        assertEquals(1, lowZoom.size)
        assertEquals(2, highZoom.size)
    }

    @Test
    fun `groupByZoom at zoom 0 clusters almost everything`() {
        val m1 = item(1, 10.0, 10.0)
        val m2 = item(2, 20.0, 20.0)
        val m3 = item(3, -10.0, -10.0)
        // Zoom 0 threshold is ~84.3 degrees, these are close enough
        val groups = MapViewModel.groupByZoom(listOf(m1, m2, m3), 0f)
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].items.size)
    }

    @Test
    fun `groupByZoom at zoom 20 uses very small threshold`() {
        // At zoom 20, threshold is approx 0.00008 degrees.
        // 0.00001 apart -> Should cluster
        val m1 = item(1, 48.0, 2.0)
        val m2 = item(2, 48.00001, 2.00001)
        val groupsCluster = MapViewModel.groupByZoom(listOf(m1, m2), 20f)
        assertEquals(1, groupsCluster.size)
        
        // 0.0001 apart -> Should NOT cluster
        val m3 = item(3, 48.0001, 2.0001)
        val groupsSeparate = MapViewModel.groupByZoom(listOf(m1, m3), 20f)
        assertEquals(2, groupsSeparate.size)
    }

    // ── itemPins StateFlow (spreadOverlapping behaviour) ───────────────────

    @Test
    fun `empty database produces empty pin list`() = runTest {
        activate()
        advanceUntilIdle()
        assertTrue(viewModel.itemPins.value.isEmpty())
    }

    @Test
    fun `single item pin is placed at its exact position`() = runTest {
        activate()
        dao.insertItem(entity(1, 48.85, 2.35))
        advanceUntilIdle()

        val pins = viewModel.itemPins.value
        assertEquals(1, pins.size)
        assertEquals(48.85, pins[0].position.lat, 1e-9)
        assertEquals(2.35, pins[0].position.lng, 1e-9)
    }

    @Test
    fun `two items at distinct coords are each at their exact position`() = runTest {
        activate()
        dao.insertItem(entity(1, 48.85, 2.35, "Paris"))
        dao.insertItem(entity(2, 51.51, -0.12, "London"))
        advanceUntilIdle()

        val paris = viewModel.itemPins.value.first { it.item.name == "Paris" }
        val london = viewModel.itemPins.value.first { it.item.name == "London" }
        assertEquals(48.85, paris.position.lat, 1e-9)
        assertEquals(51.51, london.position.lat, 1e-9)
    }

    @Test
    fun `two items at the same rounded coords are spread to different positions`() = runTest {
        activate()
        dao.insertItem(entity(1, 48.0, 2.0, "A"))
        dao.insertItem(entity(2, 48.0, 2.0, "B"))
        advanceUntilIdle()

        val pins = viewModel.itemPins.value
        assertEquals(2, pins.size)
        val p0 = pins[0].position
        val p1 = pins[1].position
        assertTrue(abs(p0.lat - p1.lat) > 1e-6 || abs(p0.lng - p1.lng) > 1e-6,
            "Overlapping pins should be spread apart")
    }

    @Test
    fun `three items at the same coords produce three distinct pin positions`() = runTest {
        activate()
        dao.insertItem(entity(1, 48.0, 2.0))
        dao.insertItem(entity(2, 48.0, 2.0))
        dao.insertItem(entity(3, 48.0, 2.0))
        advanceUntilIdle()

        val pins = viewModel.itemPins.value
        assertEquals(3, pins.size)
        val positions = pins.map { it.position }
        val unique = positions.distinctBy { Pair(it.lat, it.lng) }
        assertEquals(3, unique.size, "All three spread positions should be distinct")
    }

    @Test
    fun `spreadOverlapping circle math is exact`() = runTest {
        activate()
        dao.insertItem(entity(1, 48.0, 2.0))
        dao.insertItem(entity(2, 48.0, 2.0))
        dao.insertItem(entity(3, 48.0, 2.0))
        dao.insertItem(entity(4, 48.0, 2.0))
        advanceUntilIdle()

        val pins = viewModel.itemPins.value
        assertEquals(4, pins.size)
        // Expecting 4 points in a circle:
        // angle = 0: lat + R*cos(0)=lat+R, lng + R*sin(0)=lng
        assertEquals(48.0 + 0.0004, pins[0].position.lat, 1e-9)
        assertEquals(2.0, pins[0].position.lng, 1e-9)
        // angle = PI/2: lat + R*cos(PI/2)=lat, lng + R*sin(PI/2)=lng+R
        assertEquals(48.0, pins[1].position.lat, 1e-9)
        assertEquals(2.0 + 0.0004, pins[1].position.lng, 1e-9)
        // angle = PI: lat - R, lng
        assertEquals(48.0 - 0.0004, pins[2].position.lat, 1e-9)
        // angle = 3PI/2: lat, lng - R
        assertEquals(2.0 - 0.0004, pins[3].position.lng, 1e-9)
    }

    // ── itemGroups StateFlow (computeGroups behaviour) ────────────────────

    @Test
    fun `empty database produces empty group list`() = runTest {
        activate()
        advanceUntilIdle()
        assertTrue(viewModel.itemGroups.value.isEmpty())
    }

    @Test
    fun `items at same rounded coord form a single group`() = runTest {
        activate()
        // Difference of 0.00001 is below the 4-decimal rounding threshold
        dao.insertItem(entity(1, 48.0, 2.0))
        dao.insertItem(entity(2, 48.00001, 2.00001))
        advanceUntilIdle()

        val groups = viewModel.itemGroups.value
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].items.size)
    }

    @Test
    fun `items at different rounded coords form separate groups`() = runTest {
        activate()
        dao.insertItem(entity(1, 48.0, 2.0))
        dao.insertItem(entity(2, 51.5, 0.1))
        advanceUntilIdle()

        assertEquals(2, viewModel.itemGroups.value.size)
    }

    @Test
    fun `items StateFlow exposes raw list unchanged`() = runTest {
        activate()
        dao.insertItem(entity(1, 48.0, 2.0))
        dao.insertItem(entity(2, 51.5, 0.1))
        advanceUntilIdle()

        assertEquals(2, viewModel.items.value.size)
    }

    @Test
    fun `all three flows update when a new item is inserted`() = runTest {
        activate()
        advanceUntilIdle()
        assertTrue(viewModel.items.value.isEmpty())

        dao.insertItem(entity(1, 48.85, 2.35))
        advanceUntilIdle()

        assertEquals(1, viewModel.items.value.size)
        assertEquals(1, viewModel.itemPins.value.size)
        assertEquals(1, viewModel.itemGroups.value.size)
    }
}
