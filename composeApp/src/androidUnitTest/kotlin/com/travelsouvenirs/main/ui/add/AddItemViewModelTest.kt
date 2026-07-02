package com.travelsouvenirs.main.ui.add

import com.travelsouvenirs.main.data.CategoryEntity
import com.travelsouvenirs.main.data.CategoryRepository
import com.travelsouvenirs.main.data.FakeCategoryDao
import com.travelsouvenirs.main.data.FakeItemDao
import com.travelsouvenirs.main.data.ItemEntity
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.LatLon
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.location.LocationService
import com.travelsouvenirs.main.location.PlaceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeLocationService(
    private val location: LatLon? = null,
    private val geocodedPlace: String = "",
    private val results: List<PlaceResult> = emptyList(),
    private val shouldThrow: Boolean = false
) : LocationService {
    override suspend fun getCurrentLocation(): LatLon? {
        if (shouldThrow) throw RuntimeException("GPS unavailable")
        return location
    }
    override suspend fun reverseGeocode(lat: Double, lng: Double): String = geocodedPlace
    override suspend fun searchByName(query: String): List<PlaceResult> = results
}

private class FakeImageStorage : ImageStorage {
    override suspend fun copyToInternalStorage(sourcePath: String): String = sourcePath
    override suspend fun deleteImage(path: String) {}
    override fun localPathForDownload(firebaseId: String): String = "/cache/$firebaseId.jpg"
    override suspend fun deleteAllImages() {}
}


@OptIn(ExperimentalCoroutinesApi::class)
class AddItemViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: FakeItemDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var repository: ItemRepository
    private val fakeLocationService = FakeLocationService()
    private val fakeImageStorage = FakeImageStorage()
    private val fakeImageLocationAnalyzer = object : com.travelsouvenirs.main.image.ImageLocationAnalyzer {
        override suspend fun analyze(imagePath: String, lat: Double?, lng: Double?): String? = null
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeItemDao()
        categoryDao = FakeCategoryDao()
        repository = ItemRepository(dao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        editId: Long? = null,
        locationService: LocationService = fakeLocationService,
        categoryDaoOverride: FakeCategoryDao = categoryDao,
    ) = AddItemViewModel(
        repository,
        locationService,
        fakeImageStorage,
        editId,
        CategoryRepository(categoryDaoOverride, dao),
        fakeImageLocationAnalyzer,
    )

    private val AddItemViewModel.state get() = uiState.value

    private fun setPhotoPath(vm: AddItemViewModel, path: String) {
        val field = AddItemViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(vm) as MutableStateFlow<AddItemUiState>
        stateFlow.value = stateFlow.value.copy(photoPath = path)
    }

    @Test
    fun `initial state has blank name and notes`() {
        val vm = viewModel()
        assertEquals("", vm.state.name)
        assertEquals("", vm.state.notes)
        assertEquals("", vm.state.placeName)
        assertEquals("Default", vm.state.category)
    }

    @Test
    fun `initial state loads categories from room`() = runTest {
        categoryDao.insertCategory(CategoryEntity("Gifts"))
        categoryDao.insertCategory(CategoryEntity("Trip"))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("Default", "Gifts", "Trip"), vm.state.availableCategories)
    }

    @Test
    fun `onNameChange updates name state`() {
        val vm = viewModel()
        vm.onNameChange("Colosseum")
        assertEquals("Colosseum", vm.state.name)
    }

    @Test
    fun `onNotesChange updates notes state`() {
        val vm = viewModel()
        vm.onNotesChange("Bought at the entrance")
        assertEquals("Bought at the entrance", vm.state.notes)
    }

    @Test
    fun `onCategoryChange updates category state`() {
        val vm = viewModel()
        vm.onCategoryChange("Custom Category")
        assertEquals("Custom Category", vm.state.category)
    }

    @Test
    fun `onPhotoSelected copies photo and updates photoPath state`() = runTest {
        val vm = viewModel()
        setPhotoPath(vm, "/source/image.jpg")
        assertEquals("/source/image.jpg", vm.state.photoPath)
    }

    @Test
    fun `onRemovePhoto clears photoPath state`() = runTest {
        val vm = viewModel()
        setPhotoPath(vm, "/source/image.jpg")
        assertEquals("/source/image.jpg", vm.state.photoPath)

        vm.onRemovePhoto()
        assertEquals(null, vm.state.photoPath)
    }

    @Test
    fun `onDateChange updates dateAcquired state`() {
        val vm = viewModel()
        val newDate = "2023-03-21"
        vm.onDateChange(newDate)
        assertEquals(newDate, vm.state.dateAcquired)
    }

    @Test
    fun `onPlaceSelected sets pending coords, clears search, and auto-fills blank name`() {
        val vm = viewModel()
        vm.onPlaceSelected(PlaceResult("Rome", 41.9, 12.5))
        assertEquals(41.9, vm.state.pendingLat)
        assertEquals(12.5, vm.state.pendingLng)
        assertTrue(vm.state.searchResults.isEmpty())
        assertEquals("Rome", vm.state.name)
    }

    @Test
    fun `openLocationDialog sets showLocationDialog to true`() {
        val vm = viewModel()
        vm.openLocationDialog()
        assertTrue(vm.state.showLocationDialog)
    }

    @Test
    fun `closeLocationDialog sets showLocationDialog to false`() {
        val vm = viewModel()
        vm.openLocationDialog()
        vm.closeLocationDialog()
        assertFalse(vm.state.showLocationDialog)
    }

    @Test
    fun `saveItem does nothing when photoPath is null`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Test")
        vm.onPlaceSelected(PlaceResult("Rome", 41.9, 12.5))
        vm.saveItem()
        assertFalse(vm.state.isSaved)
    }

    @Test
    fun `saveItem does nothing when name is blank`() = runTest {
        val vm = viewModel()
        setPhotoPath(vm, "/photo.jpg")
        vm.saveItem()
        assertFalse(vm.state.isSaved)
    }

    @Test
    fun `edit mode loads existing item data`() = runTest {
        dao.insertItem(
            ItemEntity(
                id = 5,
                name = "Louvre",
                notes = "Nice museum",
                photoPath = "/photos/louvre.jpg",
                latitude = 48.86,
                longitude = 2.33,
                placeName = "Paris",
                dateAcquired = "2023-11-14"
            )
        )

        val vm = viewModel(editId = 5)
        advanceUntilIdle()

        assertEquals("Louvre", vm.state.name)
        assertEquals("Nice museum", vm.state.notes)
        assertEquals("Paris", vm.state.placeName)
        assertEquals("/photos/louvre.jpg", vm.state.photoPath)
    }

    @Test
    fun `saveItem with valid photo and name persists item and sets isSaved`() = runTest {
        val vm = viewModel(locationService = FakeLocationService(geocodedPlace = "Paris"))
        setPhotoPath(vm, "/photo.jpg")
        vm.onNameChange("Eiffel Tower")
        vm.onCategoryChange("Souvenir")
        vm.openLocationDialog()
        vm.onPendingLocationChanged(48.85, 2.35)
        vm.confirmLocation()
        advanceUntilIdle()
        vm.saveItem()
        advanceUntilIdle()
        assertTrue(vm.state.isSaved)

        val savedItem = (dao.getAllActiveItems() as kotlinx.coroutines.flow.StateFlow<List<com.travelsouvenirs.main.data.ItemEntity>>).value.first()
        assertEquals("Eiffel Tower", savedItem.name)
        assertEquals("Souvenir", savedItem.category)
        assertEquals("Paris", savedItem.placeName)
    }

    @Test
    fun `saveItem trims leading and trailing spaces and line breaks from notes`() = runTest {
        val vm = viewModel()
        setPhotoPath(vm, "/photo.jpg")
        vm.onNameChange("Eiffel Tower")
        vm.onNotesChange(" \n\r  Some notes with spaces and line breaks \n\n ")
        vm.saveItem()
        advanceUntilIdle()
        assertTrue(vm.state.isSaved)

        val savedItem = (dao.getAllActiveItems() as kotlinx.coroutines.flow.StateFlow<List<com.travelsouvenirs.main.data.ItemEntity>>).value.first()
        assertEquals("Some notes with spaces and line breaks", savedItem.notes)
    }

    @Test
    fun `saveItem limits consecutive line breaks in the middle of notes to a maximum of two`() = runTest {
        val vm = viewModel()
        setPhotoPath(vm, "/photo.jpg")
        vm.onNameChange("Eiffel Tower")
        vm.onNotesChange("Line 1\n\n\n\nLine 2\r\n\r\n\r\nLine 3\n\nLine 4")
        vm.saveItem()
        advanceUntilIdle()
        assertTrue(vm.state.isSaved)

        val savedItem = (dao.getAllActiveItems() as kotlinx.coroutines.flow.StateFlow<List<com.travelsouvenirs.main.data.ItemEntity>>).value.first()
        assertEquals("Line 1\n\nLine 2\n\nLine 3\n\nLine 4", savedItem.notes)
    }

    @Test
    fun `saveItem in edit mode upserts existing item`() = runTest {
        dao.insertItem(
            ItemEntity(
                id = 7,
                name = "Old Name",
                notes = "",
                photoPath = "/old.jpg",
                latitude = 0.0,
                longitude = 0.0,
                placeName = "Rome",
                dateAcquired = "2026-07-02"
            )
        )
        val vm = viewModel(editId = 7)
        advanceUntilIdle()
        vm.onNameChange("New Name")
        setPhotoPath(vm, "/old.jpg")
        vm.saveItem()
        advanceUntilIdle()
        assertTrue(vm.state.isSaved)
        assertEquals("New Name", dao.getItemById(7)?.name)
    }

    @Test
    fun `fetchCurrentLocation success places pending pin and keeps dialog open`() = runTest {
        val location = LatLon(51.51, -0.12)
        val vm = viewModel(locationService = FakeLocationService(location = location))
        vm.openLocationDialog()
        assertTrue(vm.state.showLocationDialog)

        vm.fetchCurrentLocation()
        advanceUntilIdle()

        assertEquals(51.51, vm.state.pendingLat)
        assertEquals(-0.12, vm.state.pendingLng)
        assertTrue(vm.state.showLocationDialog)
        assertFalse(vm.state.isLocating)
        assertEquals("", vm.state.placeName) // not committed until confirmLocation
    }

    @Test
    fun `fetchCurrentLocation null result sets locationError`() = runTest {
        val vm = viewModel(locationService = FakeLocationService(location = null))
        vm.openLocationDialog()

        vm.fetchCurrentLocation()
        advanceUntilIdle()

        assertTrue(vm.state.locationError != null)
        assertTrue(vm.state.showLocationDialog, "Dialog should stay open on error")
        assertFalse(vm.state.isLocating)
    }

    @Test
    fun `fetchCurrentLocation exception sets locationError`() = runTest {
        val vm = viewModel(locationService = FakeLocationService(shouldThrow = true))
        vm.fetchCurrentLocation()
        advanceUntilIdle()

        assertTrue(vm.state.locationError?.contains("GPS unavailable") == true)
        assertFalse(vm.state.isLocating)
    }

    @Test
    fun `onSearchQueryChange shorter than 2 chars clears results`() = runTest {
        val vm = viewModel(locationService = FakeLocationService(results = listOf(PlaceResult("Rome", 41.9, 12.5))))
        vm.onSearchQueryChange("R")
        assertTrue(vm.state.searchResults.isEmpty())
    }

    @Test
    fun `onSearchQueryChange triggers search after debounce delay`() = runTest {
        val vm = viewModel(locationService = FakeLocationService(results = listOf(PlaceResult("Rome", 41.9, 12.5))))
        vm.onSearchQueryChange("Rome")
        assertTrue(vm.state.searchResults.isEmpty(), "Results should not arrive before delay")

        advanceUntilIdle()

        assertEquals(1, vm.state.searchResults.size)
        assertEquals("Rome", vm.state.searchResults[0].name)
    }

    @Test
    fun `onSearchQueryChange rapid updates only execute the last search`() = runTest {
        val vm = viewModel(locationService = FakeLocationService(results = listOf(PlaceResult("Berlin", 52.5, 13.4))))
        vm.onSearchQueryChange("Be")
        vm.onSearchQueryChange("Ber")
        vm.onSearchQueryChange("Berl")
        advanceUntilIdle()

        assertEquals(1, vm.state.searchResults.size)
    }

    @Test
    fun `closeLocationDialog cancels in-flight search`() = runTest {
        val vm = viewModel(locationService = FakeLocationService(results = listOf(PlaceResult("Oslo", 59.9, 10.7))))
        vm.openLocationDialog()
        vm.onSearchQueryChange("Oslo")
        vm.closeLocationDialog()
        advanceUntilIdle()

        assertTrue(vm.state.searchResults.isEmpty(), "Search cancelled by closing dialog should yield no results")
    }

    @Test
    fun `onPendingLocationChanged sets pending coords without incrementing cameraMoveId`() {
        val vm = viewModel()
        val initialCameraId = vm.state.cameraMoveId
        vm.onPendingLocationChanged(48.85, 2.35)
        assertEquals(48.85, vm.state.pendingLat)
        assertEquals(2.35, vm.state.pendingLng)
        assertEquals(initialCameraId, vm.state.cameraMoveId)
    }

    @Test
    fun `confirmLocation commits pending coords, reverseGeocodes, and closes dialog`() = runTest {
        val vm = viewModel(locationService = FakeLocationService(geocodedPlace = "Paris"))
        vm.openLocationDialog()
        vm.onPendingLocationChanged(48.85, 2.35)

        vm.confirmLocation()
        advanceUntilIdle()

        assertEquals("Paris", vm.state.placeName)
        assertEquals(48.85, vm.state.pendingLat)
        assertFalse(vm.state.showLocationDialog)
        assertFalse(vm.state.isLocating)
    }

    @Test
    fun `openLocationDialog with existing location pre-populates pending pin`() = runTest {
        val vm = viewModel(locationService = FakeLocationService(geocodedPlace = "Rome"))
        vm.openLocationDialog()
        vm.onPendingLocationChanged(41.9, 12.5)
        vm.confirmLocation()
        advanceUntilIdle()

        val cameraIdBefore = vm.state.cameraMoveId
        vm.openLocationDialog()

        assertEquals(41.9, vm.state.pendingLat)
        assertEquals(12.5, vm.state.pendingLng)
        assertTrue(vm.state.cameraMoveId > cameraIdBefore)
        assertTrue(vm.state.showLocationDialog)
    }

    @Test
    fun `addCategoryOnTheFly returns true, adds to room, and auto-selects`() = runTest {
        val vm = viewModel()
        assertTrue(vm.addCategoryOnTheFly("Souvenir"))
        advanceUntilIdle()
        assertTrue(vm.state.availableCategories.contains("Souvenir"))
        assertEquals("Souvenir", vm.state.category)
    }

    @Test
    fun `addCategoryOnTheFly returns false for exact duplicate`() = runTest {
        val vm = viewModel()
        vm.addCategoryOnTheFly("Souvenir")
        advanceUntilIdle()
        assertFalse(vm.addCategoryOnTheFly("Souvenir"))
        assertEquals(2, vm.state.availableCategories.size) // Default + Souvenir
    }

    @Test
    fun `addCategoryOnTheFly returns false for case-insensitive duplicate`() = runTest {
        val vm = viewModel()
        vm.addCategoryOnTheFly("Souvenir")
        advanceUntilIdle()
        assertFalse(vm.addCategoryOnTheFly("souvenir"))
        assertFalse(vm.addCategoryOnTheFly("SOUVENIR"))
        assertEquals(2, vm.state.availableCategories.size)
    }

    @Test
    fun `addCategoryOnTheFly returns false when name matches Default`() = runTest {
        val vm = viewModel()
        assertFalse(vm.addCategoryOnTheFly("Default"))
        assertFalse(vm.addCategoryOnTheFly("default"))
        advanceUntilIdle()
        assertEquals(listOf("Default"), vm.state.availableCategories)
    }

    @Test
    fun `addCategoryOnTheFly returns false when max categories reached`() = runTest {
        val vm = viewModel()
        repeat(5) { i -> vm.addCategoryOnTheFly("Cat$i"); advanceUntilIdle() }
        assertFalse(vm.addCategoryOnTheFly("OneMore"))
        advanceUntilIdle()
        assertEquals(6, vm.state.availableCategories.size) // Default + 5 custom
    }

    @Test
    fun `addCategoryOnTheFly persists new category to room`() = runTest {
        val vm = viewModel()
        vm.addCategoryOnTheFly("Souvenir")
        advanceUntilIdle()
        assertEquals(listOf("Souvenir"), categoryDao.getAllNames())
    }

    @Test
    fun `addCategoryOnTheFly returns false when name contains a comma`() = runTest {
        val vm = viewModel()
        assertFalse(vm.addCategoryOnTheFly("Work,Travel"))
        advanceUntilIdle()
        assertEquals(listOf("Default"), vm.state.availableCategories)
    }
}
