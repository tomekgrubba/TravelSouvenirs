package com.travelsouvenirs.main.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.travelsouvenirs.main.domain.Item
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ItemRepositoryTest {

    private lateinit var db: ItemDatabase
    private lateinit var repository: ItemRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ItemDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = ItemRepository(db.itemDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun allItems_emitsEmptyListInitially() = runTest {
        repository.allItems.test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun allItems_emitsUpdatedList_afterInsert() = runTest {
        repository.allItems.test {
            awaitItem() // initial empty

            repository.insertItem(item(name = "Colosseum"))
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Colosseum", list[0].name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getItemById_returnsDomainObject() = runTest {
        val id = repository.insertItem(item(name = "Louvre", place = "Paris"))
        val result = repository.getItemById(id)
        assertNotNull(result)
        assertEquals("Louvre", result.name)
        assertEquals("Paris", result.placeName)
    }

    @Test
    fun getItemById_returnsNull_whenNotFound() = runTest {
        assertNull(repository.getItemById(999L))
    }

    @Test
    fun getItemByIdFlow_updatesWhenItemEdited() = runTest {
        val id = repository.insertItem(item(id = 10, name = "Original"))

        repository.getItemByIdFlow(id).test {
            assertEquals("Original", awaitItem()?.name)

            repository.insertItem(item(id = 10, name = "Edited"))
            assertEquals("Edited", awaitItem()?.name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteItem_removesItemFromFlow() = runTest {
        val id = repository.insertItem(item(name = "To Delete"))

        repository.allItems.test {
            assertEquals(1, awaitItem().size)

            val toDelete = repository.getItemById(id)!!
            repository.deleteItem(toDelete)
            assertTrue(awaitItem().isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun insertItem_upserts_existingRecord() = runTest {
        repository.insertItem(item(id = 5, name = "Before"))
        repository.insertItem(item(id = 5, name = "After"))

        val result = repository.getItemById(5)
        assertEquals("After", result?.name)
    }

    @Test
    fun reassignCategory_movesOnlySpecifiedItems() = runTest {
        repository.insertItem(item(id = 1, name = "A", category = "Custom Category"))
        repository.insertItem(item(id = 2, name = "B", category = "Custom Category"))
        repository.insertItem(item(id = 3, name = "C", category = "Other Category"))

        repository.reassignCategory("Custom Category", "Default")

        assertEquals("Default", repository.getItemById(1)?.category)
        assertEquals("Default", repository.getItemById(2)?.category)
        assertEquals("Other Category", repository.getItemById(3)?.category) // untouched
    }

    private fun item(id: Long = 0, name: String = "Test", place: String = "City", category: String = "Default") = Item(
        id = id,
        name = name,
        notes = "",
        photoPath = "/photos/test.jpg",
        latitude = 0.0,
        longitude = 0.0,
        placeName = place,
        dateAcquired = LocalDate(2024, 1, 1),
        category = category
    )
}
