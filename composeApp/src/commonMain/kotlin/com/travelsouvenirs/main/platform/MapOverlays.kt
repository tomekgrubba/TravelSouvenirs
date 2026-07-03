package com.travelsouvenirs.main.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.travelsouvenirs.main.domain.Item
import kotlin.math.abs

internal data class EdgeCounts(val top: Int, val bottom: Int, val left: Int, val right: Int)

@Composable
internal fun EdgeIndicator(
    arrow: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$arrow  $count",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.extraSmall
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

internal fun computeEdgeCounts(
    items: List<Item>,
    south: Double,
    west: Double,
    north: Double,
    east: Double
): EdgeCounts {
    val centerLat = (south + north) / 2.0
    val centerLng = (west + east) / 2.0
    val halfLat = (north - south) / 2.0
    val halfLng = (east - west) / 2.0
    if (halfLat <= 0 || halfLng <= 0) return EdgeCounts(0, 0, 0, 0)
    var top = 0; var bottom = 0; var left = 0; var right = 0
    items.filter { (it.latitude != 0.0 || it.longitude != 0.0) &&
        (it.latitude > north || it.latitude < south ||
         it.longitude > east || it.longitude < west)
    }.forEach { m ->
        val normLat = (m.latitude - centerLat) / halfLat
        val normLng = (m.longitude - centerLng) / halfLng
        if (abs(normLat) >= abs(normLng)) {
            if (normLat > 0) top++ else bottom++
        } else {
            if (normLng > 0) right++ else left++
        }
    }
    return EdgeCounts(top, bottom, left, right)
}

internal fun findNextOffscreenItem(
    items: List<Item>,
    direction: String,
    south: Double,
    west: Double,
    north: Double,
    east: Double
): Item? {
    val activeItems = items.filter { it.latitude != 0.0 || it.longitude != 0.0 }
    return when (direction) {
        "top" -> {
            activeItems.filter { it.latitude > north }
                .minByOrNull { it.latitude }
        }
        "bottom" -> {
            activeItems.filter { it.latitude < south }
                .maxByOrNull { it.latitude }
        }
        "left" -> {
            activeItems.filter { it.longitude < west }
                .maxByOrNull { it.longitude }
        }
        "right" -> {
            activeItems.filter { it.longitude > east }
                .minByOrNull { it.longitude }
        }
        else -> null
    }
}
