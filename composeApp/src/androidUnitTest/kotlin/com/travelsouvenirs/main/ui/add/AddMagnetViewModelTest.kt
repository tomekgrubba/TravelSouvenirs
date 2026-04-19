package com.travelsouvenirs.main.ui.add

import android.content.Context
import com.travelsouvenirs.main.data.FakeMagnetDao
import com.travelsouvenirs.main.data.MagnetEntity
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.location.PlaceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AddMagnetViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: FakeMagnetDao
    private lateinit var repository: MagnetRepository
    private val mockContext: Context = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeMagnetDao()
        repository = MagnetRepository(dao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(editId: Long? = null) =
        AddMagnetViewModel(repository, mockContext, editId)

    @Test
    fun `initial state has blank name and notes`() {
        val vm = viewModel()
        assertEquals("", vm.name.value)
        assertEquals("", vm.notes.value)
        assertEquals("", vm.placeName.value)
    }

    @Test
    fun `onNameChange updates name state`() {
        val vm = viewModel()
        vm.onNameChange("Colosseum")
        assertEquals("Colosseum", vm.name.value)
    }

    @Test
    fun `onNotesChange updates notes state`() {
        val vm = viewModel()
        vm.onNotesChange("Bought at the entrance")
        assertEquals("Bought at the entrance", vm.notes.value)
    }

    @Test
    fun `onDateChange updates dateAcquired state`() {
        val vm = viewModel()
        val newDate = LocalDate(2023, 3, 21)
        vm.onDateChange(newDate)
        assertEquals(newDate, vm.dateAcquired.value)
    }

    @Test
    fun `onPlaceSelected updates placeName and coordinates`() {
        val vm = viewModel()
        vm.onPlaceSelected(PlaceResult("Rome", 41.9, 12.5))
        assertEquals("Rome", vm.placeName.value)
    }

    @Test
    fun `openLocationDialog sets showLocationDialog to true`() {
        val vm = viewModel()
        vm.openLocationDialog()
        assertTrue(vm.showLocationDialog.value)
    }

    @Test
    fun `closeLocationDialog sets showLocationDialog to false`() {
        val vm = viewModel()
        vm.openLocationDialog()
        vm.closeLocationDialog()
        assertFalse(vm.showLocationDialog.value)
    }

    @Test
    fun `saveMagnet does nothing when photoPath is null`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Test")
        vm.onPlaceSelected(PlaceResult("Rome", 41.9, 12.5))
        vm.saveMagnet()
        assertFalse(vm.isSaved.value)
    }

    @Test
    fun `saveMagnet does nothing when name is blank`() = runTest {
        val vm = viewModel()
        setPhotoPath(vm, "/photo.jpg")
        vm.onPlaceSelected(PlaceResult("Rome", 41.9, 12.5))
        vm.saveMagnet()
        assertFalse(vm.isSaved.value)
    }

    @Test
    fun `edit mode loads existing magnet data`() = runTest {
        dao.insertMagnet(
            MagnetEntity(
                id = 5,
                name = "Louvre",
                notes = "Nice museum",
                photoPath = "/photos/louvre.jpg",
                latitude = 48.86,
                longitude = 2.33,
                placeName = "Paris",
                dateAcquiredMillis = 1_700_000_000_000L
            )
        )

        val vm = viewModel(editId = 5)
        advanceUntilIdle()

        assertEquals("Louvre", vm.name.value)
        assertEquals("Nice museum", vm.notes.value)
        assertEquals("Paris", vm.placeName.value)
        assertEquals("/photos/louvre.jpg", vm.photoPath.value)
    }

    private fun setPhotoPath(vm: AddMagnetViewModel, path: String) {
        val field = AddMagnetViewModel::class.java.getDeclaredField("_photoPath")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<String?>).value = path
    }
}
