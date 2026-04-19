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
class MagnetDaoTest {

    private lateinit var db: MagnetDatabase
    private lateinit var dao: MagnetDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MagnetDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.magnetDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndRetrieveById() = runTest {
        val id = dao.insertMagnet(entity("Colosseum", place = "Rome"))
        val result = dao.getMagnetById(id)
        assertNotNull(result)
        assertEquals("Colosseum", result.name)
        assertEquals("Rome", result.placeName)
    }

    @Test
    fun getMagnetById_returnsNull_whenNotFound() = runTest {
        assertNull(dao.getMagnetById(999L))
    }

    @Test
    fun insertMultiple_getAllMagnets_returnsAll() = runTest {
        dao.insertMagnet(entity("A"))
        dao.insertMagnet(entity("B"))
        dao.insertMagnet(entity("C"))

        dao.getAllMagnets().test {
            val list = awaitItem()
            assertEquals(3, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun delete_removesFromDatabase() = runTest {
        val id = dao.insertMagnet(entity("To Delete"))
        val inserted = dao.getMagnetById(id)!!
        dao.deleteMagnet(inserted)
        assertNull(dao.getMagnetById(id))
    }

    @Test
    fun insert_withSameId_replacesExistingRow() = runTest {
        val id = dao.insertMagnet(entity("Original", id = 10))
        dao.insertMagnet(entity("Updated", id = 10))
        val result = dao.getMagnetById(id)
        assertEquals("Updated", result?.name)
    }

    @Test
    fun getAllMagnets_emitsOnInsert() = runTest {
        dao.getAllMagnets().test {
            assertEquals(0, awaitItem().size)

            dao.insertMagnet(entity("Paris"))
            assertEquals(1, awaitItem().size)

            dao.insertMagnet(entity("London"))
            assertEquals(2, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAllMagnets_emitsOnDelete() = runTest {
        val id = dao.insertMagnet(entity("Berlin"))

        dao.getAllMagnets().test {
            assertEquals(1, awaitItem().size)

            dao.deleteMagnet(dao.getMagnetById(id)!!)
            assertTrue(awaitItem().isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getMagnetByIdFlow_emitsUpdatedValue_onUpsert() = runTest {
        val id = dao.insertMagnet(entity("Original", id = 7))

        dao.getMagnetByIdFlow(id).test {
            assertEquals("Original", awaitItem()?.name)

            dao.insertMagnet(entity("Updated", id = 7))
            assertEquals("Updated", awaitItem()?.name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun entity(name: String, place: String = "City", id: Long = 0) = MagnetEntity(
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
