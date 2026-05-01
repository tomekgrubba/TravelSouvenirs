package com.travelsouvenirs.main.ui.detail

import com.travelsouvenirs.main.data.FakeItemDao
import com.travelsouvenirs.main.data.ItemEntity
import com.travelsouvenirs.main.data.ItemRepository
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
class ItemDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: FakeItemDao
    private lateinit var repository: ItemRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeItemDao()
        repository = ItemRepository(dao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val fakeImageStorage = object : ImageStorage {
        val deletedPaths = mutableListOf<String>()
        override suspend fun copyToInternalStorage(sourcePath: String): String = sourcePath
        override suspend fun deleteImage(path: String) { deletedPaths.add(path) }
        override fun localPathForDownload(firebaseId: String): String = "/cache/$firebaseId.jpg"
    }

    private fun viewModel(id: Long) = ItemDetailViewModel(repository, id, fakeImageStorage)

    private fun TestScope.activate(vm: ItemDetailViewModel) =
        backgroundScope.launch(testDispatcher) { vm.item.collect {} }

    private fun entity(id: Long, name: String, notes: String = "") = ItemEntity(
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
    fun `item starts null before any subscriber`() {
        val vm = viewModel(1)
        assertNull(vm.item.value)
    }

    @Test
    fun `item emits value when found in database`() = runTest {
        dao.insertItem(entity(1, "Colosseum"))
        val vm = viewModel(1)
        activate(vm)
        advanceUntilIdle()

        assertNotNull(vm.item.value)
        assertEquals("Colosseum", vm.item.value?.name)
    }

    @Test
    fun `item stays null when id does not exist`() = runTest {
        val vm = viewModel(99)
        activate(vm)
        advanceUntilIdle()

        assertNull(vm.item.value)
    }

    @Test
    fun `item preserves notes and place name`() = runTest {
        dao.insertItem(entity(1, "Eiffel", notes = "Bought at level 2"))
        val vm = viewModel(1)
        activate(vm)
        advanceUntilIdle()

        assertEquals("Eiffel", vm.item.value?.name)
        assertEquals("Bought at level 2", vm.item.value?.notes)
        assertEquals("Paris", vm.item.value?.placeName)
    }

    @Test
    fun `deleteItem removes item and invokes callback`() = runTest {
        dao.insertItem(entity(1, "Colosseum"))
        val vm = viewModel(1)
        activate(vm)
        advanceUntilIdle()
        assertNotNull(vm.item.value)

        var callbackInvoked = false
        vm.deleteItem { callbackInvoked = true }
        advanceUntilIdle()

        assertTrue(callbackInvoked)
        assertNull(vm.item.value)
        assertNull(dao.getItemById(1))
    }

    @Test
    fun `deleteItem is no-op when item not yet loaded`() = runTest {
        val vm = viewModel(99)
        activate(vm)

        var callbackInvoked = false
        vm.deleteItem { callbackInvoked = true }
        advanceUntilIdle()

        assertFalse(callbackInvoked)
    }

    @Test
    fun `item flow re-emits when item is updated`() = runTest {
        dao.insertItem(entity(1, "Original"))
        val vm = viewModel(1)
        activate(vm)
        advanceUntilIdle()
        assertEquals("Original", vm.item.value?.name)

        dao.insertItem(entity(1, "Updated"))
        advanceUntilIdle()

        assertEquals("Updated", vm.item.value?.name)
    }
}
