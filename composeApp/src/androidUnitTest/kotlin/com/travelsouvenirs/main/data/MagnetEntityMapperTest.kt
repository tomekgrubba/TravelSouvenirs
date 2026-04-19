package com.travelsouvenirs.main.data

import com.travelsouvenirs.main.domain.Magnet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Test
import kotlin.test.assertEquals

class MagnetEntityMapperTest {

    @Test
    fun `toDomain preserves all fields`() {
        val date = LocalDate(2023, 6, 15)
        val entity = MagnetEntity(
            id = 42,
            name = "Berlin Wall",
            notes = "Iconic piece",
            photoPath = "/photos/berlin.jpg",
            latitude = 52.516,
            longitude = 13.377,
            placeName = "Berlin",
            dateAcquiredMillis = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        )

        val domain = entity.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("Berlin Wall", domain.name)
        assertEquals("Iconic piece", domain.notes)
        assertEquals("/photos/berlin.jpg", domain.photoPath)
        assertEquals(52.516, domain.latitude)
        assertEquals(13.377, domain.longitude)
        assertEquals("Berlin", domain.placeName)
        assertEquals(date.year, domain.dateAcquired.year)
        assertEquals(date.monthNumber, domain.dateAcquired.monthNumber)
        assertEquals(date.dayOfMonth, domain.dateAcquired.dayOfMonth)
    }

    @Test
    fun `toEntity then toDomain round-trips all fields`() {
        val original = Magnet(
            id = 7,
            name = "Eiffel Tower",
            notes = "Bought at the shop",
            photoPath = "/photos/eiffel.jpg",
            latitude = 48.858,
            longitude = 2.294,
            placeName = "Paris",
            dateAcquired = LocalDate(2022, 8, 10)
        )

        val result = original.toEntity().toDomain()

        assertEquals(original.id, result.id)
        assertEquals(original.name, result.name)
        assertEquals(original.notes, result.notes)
        assertEquals(original.photoPath, result.photoPath)
        assertEquals(original.latitude, result.latitude)
        assertEquals(original.longitude, result.longitude)
        assertEquals(original.placeName, result.placeName)
        assertEquals(original.dateAcquired.year, result.dateAcquired.year)
        assertEquals(original.dateAcquired.monthNumber, result.dateAcquired.monthNumber)
        assertEquals(original.dateAcquired.dayOfMonth, result.dateAcquired.dayOfMonth)
    }

    @Test
    fun `toEntity stores zero-latitude and zero-longitude accurately`() {
        val magnet = Magnet(
            id = 1, name = "X", notes = "", photoPath = "/p.jpg",
            latitude = 0.0, longitude = 0.0,
            placeName = "Null Island", dateAcquired = LocalDate(2020, 1, 1)
        )
        val entity = magnet.toEntity()
        assertEquals(0.0, entity.latitude)
        assertEquals(0.0, entity.longitude)
    }
}
