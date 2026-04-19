package com.travelsouvenirs.main.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.travelsouvenirs.main.domain.Magnet
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
class MagnetRepositoryTest {

    private lateinit var db: MagnetDatabase
    private lateinit var repository: MagnetRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MagnetDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = MagnetRepository(db.magnetDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun allMagnets_emitsEmptyListInitially() = runTest {
        repository.allMagnets.test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun allMagnets_emitsUpdatedList_afterInsert() = runTest {
        repository.allMagnets.test {
            awaitItem() // initial empty

            repository.insertMagnet(magnet(name = "Colosseum"))
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Colosseum", list[0].name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getMagnetById_returnsDomainObject() = runTest {
        val id = repository.insertMagnet(magnet(name = "Louvre", place = "Paris"))
        val result = repository.getMagnetById(id)
        assertNotNull(result)
        assertEquals("Louvre", result.name)
        assertEquals("Paris", result.placeName)
    }

    @Test
    fun getMagnetById_returnsNull_whenNotFound() = runTest {
        assertNull(repository.getMagnetById(999L))
    }

    @Test
    fun getMagnetByIdFlow_updatesWhenItemEdited() = runTest {
        val id = repository.insertMagnet(magnet(id = 10, name = "Original"))

        repository.getMagnetByIdFlow(id).test {
            assertEquals("Original", awaitItem()?.name)

            repository.insertMagnet(magnet(id = 10, name = "Edited"))
            assertEquals("Edited", awaitItem()?.name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteMagnet_removesItemFromFlow() = runTest {
        val id = repository.insertMagnet(magnet(name = "To Delete"))

        repository.allMagnets.test {
            assertEquals(1, awaitItem().size)

            val toDelete = repository.getMagnetById(id)!!
            repository.deleteMagnet(toDelete)
            assertTrue(awaitItem().isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun insertMagnet_upserts_existingRecord() = runTest {
        repository.insertMagnet(magnet(id = 5, name = "Before"))
        repository.insertMagnet(magnet(id = 5, name = "After"))

        val result = repository.getMagnetById(5)
        assertEquals("After", result?.name)
    }

    private fun magnet(id: Long = 0, name: String = "Test", place: String = "City") = Magnet(
        id = id,
        name = name,
        notes = "",
        photoPath = "/photos/test.jpg",
        latitude = 0.0,
        longitude = 0.0,
        placeName = place,
        dateAcquired = LocalDate(2024, 1, 1)
    )
}
