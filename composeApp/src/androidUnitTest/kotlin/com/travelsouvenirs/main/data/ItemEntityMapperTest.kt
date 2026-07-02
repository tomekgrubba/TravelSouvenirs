package com.travelsouvenirs.main.data

import com.travelsouvenirs.main.domain.Item
import org.junit.Test
import kotlin.test.assertEquals

class ItemEntityMapperTest {

    @Test
    fun `toDomain preserves all fields`() {
        val entity = ItemEntity(
            id = 42,
            name = "Berlin Wall",
            notes = "Iconic piece",
            photoPath = "/photos/berlin.jpg",
            latitude = 52.516,
            longitude = 13.377,
            placeName = "Berlin",
            dateAcquired = "2023-06-15"
        )

        val domain = entity.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("Berlin Wall", domain.name)
        assertEquals("Iconic piece", domain.notes)
        assertEquals("/photos/berlin.jpg", domain.photoPath)
        assertEquals(52.516, domain.latitude)
        assertEquals(13.377, domain.longitude)
        assertEquals("Berlin", domain.placeName)
        assertEquals("2023-06-15", domain.dateAcquired)
    }

    @Test
    fun `toEntity then toDomain round-trips all fields`() {
        val original = Item(
            id = 7,
            name = "Eiffel Tower",
            notes = "Bought at the shop",
            photoPath = "/photos/eiffel.jpg",
            latitude = 48.858,
            longitude = 2.294,
            placeName = "Paris",
            dateAcquired = "2022-08-10"
        )

        val result = original.toEntity().toDomain()

        assertEquals(original.id, result.id)
        assertEquals(original.name, result.name)
        assertEquals(original.notes, result.notes)
        assertEquals(original.photoPath, result.photoPath)
        assertEquals(original.latitude, result.latitude)
        assertEquals(original.longitude, result.longitude)
        assertEquals(original.placeName, result.placeName)
        assertEquals(original.dateAcquired, result.dateAcquired)
    }

    @Test
    fun `toEntity stores zero-latitude and zero-longitude accurately`() {
        val item = Item(
            id = 1, name = "X", notes = "", photoPath = "/p.jpg",
            latitude = 0.0, longitude = 0.0,
            placeName = "Null Island", dateAcquired = "2020-01-01"
        )
        val entity = item.toEntity()
        assertEquals(0.0, entity.latitude)
        assertEquals(0.0, entity.longitude)
    }
}
