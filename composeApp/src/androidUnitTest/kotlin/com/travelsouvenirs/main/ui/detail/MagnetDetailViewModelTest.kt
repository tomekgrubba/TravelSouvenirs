package com.travelsouvenirs.main.ui.detail

import com.travelsouvenirs.main.data.FakeMagnetDao
import com.travelsouvenirs.main.data.MagnetEntity
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.image.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MagnetDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: FakeMagnetDao
    private lateinit var repository: MagnetRepository

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

    private val fakeImageStorage = object : ImageStorage {
        val deletedPaths = mutableListOf<String>()
        override suspend fun copyToInternalStorage(sourcePath: String): String = sourcePath
        override suspend fun deleteImage(path: String) { deletedPaths.add(path) }
    }

    private fun viewModel(id: Long) = MagnetDetailViewModel(repository, id, fakeImageStorage)

    private fun TestScope.activate(vm: MagnetDetailViewModel) =
        backgroundScope.launch(testDispatcher) { vm.magnet.collect {} }

    private fun entity(id: Long, name: String, notes: String = "") = MagnetEntity(
        id = id,
        name = name,
        notes = notes,
        photoPath = "/photos/$id.jpg",
        latitude = 48.85,
        longitude = 2.35,
        placeName = "Paris",
        dateAcquiredMillis = 0L
    )

    @Test
    fun `magnet starts null before any subscriber`() {
        val vm = viewModel(1)
        assertNull(vm.magnet.value)
    }

    @Test
    fun `magnet emits item when found in database`() = runTest {
        dao.insertMagnet(entity(1, "Colosseum"))
        val vm = viewModel(1)
        activate(vm)
        advanceUntilIdle()

        assertNotNull(vm.magnet.value)
        assertEquals("Colosseum", vm.magnet.value?.name)
    }

    @Test
    fun `magnet stays null when id does not exist`() = runTest {
        val vm = viewModel(99)
        activate(vm)
        advanceUntilIdle()

        assertNull(vm.magnet.value)
    }

    @Test
    fun `magnet preserves notes and place name`() = runTest {
        dao.insertMagnet(entity(1, "Eiffel", notes = "Bought at level 2"))
        val vm = viewModel(1)
        activate(vm)
        advanceUntilIdle()

        assertEquals("Eiffel", vm.magnet.value?.name)
        assertEquals("Bought at level 2", vm.magnet.value?.notes)
        assertEquals("Paris", vm.magnet.value?.placeName)
    }

    @Test
    fun `deleteMagnet removes item and invokes callback`() = runTest {
        dao.insertMagnet(entity(1, "Colosseum"))
        val vm = viewModel(1)
        activate(vm)
        advanceUntilIdle()
        assertNotNull(vm.magnet.value)

        var callbackInvoked = false
        vm.deleteMagnet { callbackInvoked = true }
        advanceUntilIdle()

        assertTrue(callbackInvoked)
        assertNull(vm.magnet.value)
        assertNull(dao.getMagnetById(1))
    }

    @Test
    fun `deleteMagnet is no-op when magnet not yet loaded`() = runTest {
        val vm = viewModel(99)
        activate(vm)

        var callbackInvoked = false
        vm.deleteMagnet { callbackInvoked = true }
        advanceUntilIdle()

        assertFalse(callbackInvoked)
    }

    @Test
    fun `magnet flow re-emits when item is updated`() = runTest {
        dao.insertMagnet(entity(1, "Original"))
        val vm = viewModel(1)
        activate(vm)
        advanceUntilIdle()
        assertEquals("Original", vm.magnet.value?.name)

        dao.insertMagnet(entity(1, "Updated"))
        advanceUntilIdle()

        assertEquals("Updated", vm.magnet.value?.name)
    }
}
