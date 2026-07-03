package com.travelsouvenirs.main.platform

import com.travelsouvenirs.main.domain.Item
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapOverlaysTest {

    private fun item(id: Long, lat: Double, lng: Double, name: String = "Item $id") = Item(
        id = id,
        name = name,
        notes = "",
        photoPath = "/photos/$id.jpg",
        latitude = lat,
        longitude = lng,
        placeName = "City",
        dateAcquired = "2024-01-01"
    )

    // Boundaries of visible region (south, west, north, east)
    // Center: lat = 0.0, lng = 0.0
    // Half width: lat = 10.0, lng = 10.0
    private val south = -10.0
    private val west = -10.0
    private val north = 10.0
    private val east = 10.0

    @Test
    fun testEmptyItems() {
        assertNull(findNextOffscreenItem(emptyList(), "top", south, west, north, east))
    }

    @Test
    fun testOnlyOnscreenItems() {
        val items = listOf(
            item(1, 0.0, 0.0),
            item(2, 5.0, -5.0),
            item(3, -9.0, 9.0)
        )
        assertNull(findNextOffscreenItem(items, "top", south, west, north, east))
        assertNull(findNextOffscreenItem(items, "bottom", south, west, north, east))
        assertNull(findNextOffscreenItem(items, "left", south, west, north, east))
        assertNull(findNextOffscreenItem(items, "right", south, west, north, east))
    }

    @Test
    fun testTopDirection() {
        // Items above north (latitude > 10.0)
        // Item 1: lat = 12.0, lng = 0.0 (normLat = 1.2, normLng = 0.0) -> top
        // Item 2: lat = 15.0, lng = 0.0 (normLat = 1.5, normLng = 0.0) -> top
        // Item 3: lat = 11.0, lng = 15.0 (normLat = 1.1, normLng = 1.5) -> right (abs(normLng) > abs(normLat))
        val item1 = item(1, 12.0, 0.0)
        val item2 = item(2, 15.0, 0.0)
        val item3 = item(3, 11.0, 15.0)

        val items = listOf(item1, item2, item3)

        // For top, should find the closest one to north (min latitude among top candidates) -> item1
        // because item3 is classified as right (abs(normLng) > abs(normLat)).
        val result = findNextOffscreenItem(items, "top", south, west, north, east)
        assertEquals(item1, result)
    }

    @Test
    fun testBottomDirection() {
        // Items below south (latitude < -10.0)
        // Item 1: lat = -12.0, lng = 0.0 -> bottom
        // Item 2: lat = -15.0, lng = 0.0 -> bottom
        // Item 3: lat = -12.0, lng = -15.0 -> left
        val item1 = item(1, -12.0, 0.0)
        val item2 = item(2, -15.0, 0.0)
        val item3 = item(3, -12.0, -15.0)

        val items = listOf(item1, item2, item3)

        // For bottom, should find the closest one to south (max latitude among bottom candidates) -> item1
        val result = findNextOffscreenItem(items, "bottom", south, west, north, east)
        assertEquals(item1, result)
    }

    @Test
    fun testLeftDirection() {
        // Items left of west (longitude < -10.0)
        // Item 1: lat = 0.0, lng = -12.0 -> left
        // Item 2: lat = 0.0, lng = -15.0 -> left
        // Item 3: lat = -15.0, lng = -12.0 -> bottom
        val item1 = item(1, 0.0, -12.0)
        val item2 = item(2, 0.0, -15.0)
        val item3 = item(3, -15.0, -12.0)

        val items = listOf(item1, item2, item3)

        // For left, should find the closest one to west (max longitude among left candidates) -> item1
        val result = findNextOffscreenItem(items, "left", south, west, north, east)
        assertEquals(item1, result)
    }

    @Test
    fun testRightDirection() {
        // Items right of east (longitude > 10.0)
        // Item 1: lat = 0.0, lng = 12.0 -> right
        // Item 2: lat = 0.0, lng = 15.0 -> right
        // Item 3: lat = 15.0, lng = 12.0 -> top
        val item1 = item(1, 0.0, 12.0)
        val item2 = item(2, 0.0, 15.0)
        val item3 = item(3, 15.0, 12.0)

        val items = listOf(item1, item2, item3)

        // For right, should find the closest one to east (min longitude among right candidates) -> item1
        val result = findNextOffscreenItem(items, "right", south, west, north, east)
        assertEquals(item1, result)
    }
}
