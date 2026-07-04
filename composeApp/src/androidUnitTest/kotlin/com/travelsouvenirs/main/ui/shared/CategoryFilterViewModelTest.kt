package com.travelsouvenirs.main.ui.shared

import com.travelsouvenirs.main.data.CategoryEntity
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

    private suspend fun insertCategory(name: String) {
        categoryDao.insertCategory(CategoryEntity(name, 123456L))
    }

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
        
        insertCategory("Work")
        insertCategory("Vacation")
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

    @Test
    fun `selectedCategory defaults to null when multiple categories exist`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.availableCategories.collect {} }
        backgroundScope.launch(testDispatcher) { viewModel.selectedCategory.collect {} }
        
        insertCategory("Vacation")
        itemDao.insertItem(entity(1, DEFAULT_CATEGORY))
        itemDao.insertItem(entity(2, "Vacation"))
        
        assertEquals(null, viewModel.selectedCategory.value)
    }

    @Test
    fun `selectedCategory updates correctly when selectCategory is called`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.availableCategories.collect {} }
        backgroundScope.launch(testDispatcher) { viewModel.selectedCategory.collect {} }
        
        insertCategory("Vacation")
        itemDao.insertItem(entity(1, DEFAULT_CATEGORY))
        itemDao.insertItem(entity(2, "Vacation"))
        
        viewModel.selectCategory("Vacation")
        assertEquals("Vacation", viewModel.selectedCategory.value)
        assertEquals(setOf("Vacation"), viewModel.selectedCategories.value)
        
        viewModel.selectCategory(null)
        assertEquals(null, viewModel.selectedCategory.value)
        assertEquals(setOf(DEFAULT_CATEGORY, "Vacation"), viewModel.selectedCategories.value)
    }

    @Test
    fun `selectedCategory automatically selects the only category when only one exists`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.availableCategories.collect {} }
        backgroundScope.launch(testDispatcher) { viewModel.selectedCategory.collect {} }
        
        itemDao.insertItem(entity(1, DEFAULT_CATEGORY))
        
        assertEquals(DEFAULT_CATEGORY, viewModel.selectedCategory.value)
    }

    @Test
    fun `selectedCategory resets to null if the selected category is no longer available`() = runTest {
        backgroundScope.launch(testDispatcher) { viewModel.availableCategories.collect {} }
        backgroundScope.launch(testDispatcher) { viewModel.selectedCategory.collect {} }
        
        insertCategory("Vacation")
        insertCategory("Work")
        val item1 = entity(1, DEFAULT_CATEGORY)
        val vacationItem = entity(2, "Vacation")
        val item3 = entity(3, "Work")
        itemDao.insertItem(item1)
        itemDao.insertItem(vacationItem)
        itemDao.insertItem(item3)
        
        viewModel.selectCategory("Vacation")
        assertEquals("Vacation", viewModel.selectedCategory.value)
        
        // Remove item with Vacation category so it is no longer available
        itemDao.deleteItem(vacationItem)
        
        assertEquals(null, viewModel.selectedCategory.value)
    }
}
