package com.travelsouvenirs.main.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ItemDaoTest {

    private lateinit var db: ItemDatabase
    private lateinit var dao: ItemDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ItemDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.itemDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndRetrieveById() = runTest {
        val id = dao.insertItem(entity("Colosseum", place = "Rome"))
        val result = dao.getItemById(id)
        assertNotNull(result)
        assertEquals("Colosseum", result.name)
        assertEquals("Rome", result.placeName)
    }

    @Test
    fun getItemById_returnsNull_whenNotFound() = runTest {
        assertNull(dao.getItemById(999L))
    }

    @Test
    fun insertMultiple_getAllItems_returnsAll() = runTest {
        dao.insertItem(entity("A"))
        dao.insertItem(entity("B"))
        dao.insertItem(entity("C"))

        dao.getAllItems().test {
            val list = awaitItem()
            assertEquals(3, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun delete_removesFromDatabase() = runTest {
        val id = dao.insertItem(entity("To Delete"))
        val inserted = dao.getItemById(id)!!
        dao.deleteItem(inserted)
        assertNull(dao.getItemById(id))
    }

    @Test
    fun insert_withSameId_replacesExistingRow() = runTest {
        val id = dao.insertItem(entity("Original", id = 10))
        dao.insertItem(entity("Updated", id = 10))
        val result = dao.getItemById(id)
        assertEquals("Updated", result?.name)
    }

    @Test
    fun getAllItems_emitsOnInsert() = runTest {
        dao.getAllItems().test {
            assertEquals(0, awaitItem().size)

            dao.insertItem(entity("Paris"))
            assertEquals(1, awaitItem().size)

            dao.insertItem(entity("London"))
            assertEquals(2, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAllItems_emitsOnDelete() = runTest {
        val id = dao.insertItem(entity("Berlin"))

        dao.getAllItems().test {
            assertEquals(1, awaitItem().size)

            dao.deleteItem(dao.getItemById(id)!!)
            assertTrue(awaitItem().isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getItemByIdFlow_emitsUpdatedValue_onUpsert() = runTest {
        val id = dao.insertItem(entity("Original", id = 7))

        dao.getItemByIdFlow(id).test {
            assertEquals("Original", awaitItem()?.name)

            dao.insertItem(entity("Updated", id = 7))
            assertEquals("Updated", awaitItem()?.name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun entity(name: String, place: String = "City", id: Long = 0) = ItemEntity(
        id = id,
        name = name,
        notes = "",
        photoPath = "/photos/test.jpg",
        latitude = 0.0,
        longitude = 0.0,
        placeName = place,
        dateAcquiredMillis = 1_700_000_000_000L
    )
}
