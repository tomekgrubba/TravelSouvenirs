package com.travelsouvenirs.main.ui.shared

import com.travelsouvenirs.main.data.CategoryRepository
import com.travelsouvenirs.main.data.FakeCategoryDao
import com.travelsouvenirs.main.data.FakeItemDao
import com.travelsouvenirs.main.data.ItemEntity
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryFilterViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var itemDao: FakeItemDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var itemRepo: ItemRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var viewModel: CategoryFilterViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        itemDao = FakeItemDao()
        categoryDao = FakeCategoryDao()
        itemRepo = ItemRepository(itemDao)
        categoryRepo = CategoryRepository(categoryDao, itemDao)
        viewModel = CategoryFilterViewModel(categoryRepo, itemRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entity(id: Long, category: String) = ItemEntity(
        id = id,
        name = "Item $id",
        notes = "",
        photoPath = "/photos/$id.jpg",
        latitude = 0.0,
        longitude = 0.0,
        placeName = "Place",
        dateAcquired = "2024-01-01",
        category = category
    )

    @Test
    fun `categoryCounts is empty initially`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.categoryCounts.collect {} }
        assertTrue(viewModel.categoryCounts.value.isEmpty())
    }

    @Test
    fun `categoryCounts computes correct count for single item`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.categoryCounts.collect {} }
        itemDao.insertItem(entity(1, DEFAULT_CATEGORY))
        
        val counts = viewModel.categoryCounts.value
        assertEquals(1, counts.size)
        assertEquals(1, counts[DEFAULT_CATEGORY])
    }

    @Test
    fun `categoryCounts groups multiple items by category`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.categoryCounts.collect {} }
        
        itemDao.insertItem(entity(1, DEFAULT_CATEGORY))
        itemDao.insertItem(entity(2, DEFAULT_CATEGORY))
        itemDao.insertItem(entity(3, "Work"))
        itemDao.insertItem(entity(4, "Vacation"))
        itemDao.insertItem(entity(5, "Vacation"))
        
        val counts = viewModel.categoryCounts.value
        assertEquals(3, counts.size)
        assertEquals(2, counts[DEFAULT_CATEGORY])
        assertEquals(1, counts["Work"])
        assertEquals(2, counts["Vacation"])
    }
}
