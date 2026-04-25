package com.travelsouvenirs.main.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.Magnet
import com.travelsouvenirs.main.domain.LatLon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin

/** A single map pin with its (possibly spread) position to avoid exact overlap. */
data class MagnetPin(val magnet: Magnet, val position: LatLon)
/** A cluster of items that share approximately the same coordinates, with a computed centre. */
data class MagnetGroup(val magnets: List<Magnet>, val centerLat: Double, val centerLng: Double)

/** Supplies the map screen with pre-computed pin positions, groups, and raw item list. */
class MapViewModel(repository: MagnetRepository) : ViewModel() {

    /** Cached native map view (MKMapView / WKWebView on iOS, MapView on Android OSM); survives nav pops. */
    var nativeMapView: Any? = null
    /** True once the map has been given its initial camera position; prevents re-zoom on back-nav. */
    var initialCameraSet: Boolean = false
    /** Tracks the last-active provider key; triggers camera reset when the provider changes. */
    var lastProvider: String? = null
    /** Called from onCleared() to release platform-specific native view resources. */
    var onClearNativeView: (() -> Unit)? = null

    override fun onCleared() {
        super.onCleared()
        onClearNativeView?.invoke()
        nativeMapView = null
    }

    /** Individual pin positions with overlapping items spread in a small circle. */
    val magnetPins: StateFlow<List<MagnetPin>> = repository.allMagnets
        .map(::spreadOverlapping)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Pre-computed exact-location groups used at low zoom. */
    val magnetGroups: StateFlow<List<MagnetGroup>> = repository.allMagnets
        .map(::computeGroups)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Raw list of all items, used for initial camera bounds and empty-state detection. */
    val magnets: StateFlow<List<Magnet>> = repository.allMagnets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        private const val SPREAD_RADIUS = 0.0004

        /** Clusters items that would visually overlap at [zoom]; threshold ≈ 100 px of map space. */
        fun groupByZoom(magnets: List<Magnet>, zoom: Float): List<MagnetGroup> {
            if (magnets.isEmpty()) return emptyList()
            val threshold = 60.0 * 360.0 / (256.0 * 2.0.pow(zoom.toDouble()))
            val thresholdSq = threshold * threshold
            val remaining = magnets.toMutableList()
            val groups = mutableListOf<MagnetGroup>()
            while (remaining.isNotEmpty()) {
                val seed = remaining.removeAt(0)
                val group = mutableListOf(seed)
                val it = remaining.iterator()
                while (it.hasNext()) {
                    val m = it.next()
                    val dLat = m.latitude - seed.latitude
                    val dLng = m.longitude - seed.longitude
                    if (dLat * dLat + dLng * dLng <= thresholdSq) {
                        group.add(m); it.remove()
                    }
                }
                groups.add(MagnetGroup(
                    group,
                    group.sumOf { it.latitude } / group.size,
                    group.sumOf { it.longitude } / group.size
                ))
            }
            return groups
        }

        private fun roundCoord(value: Double): Double =
            (value * 10_000).roundToLong() / 10_000.0

        private fun computeGroups(magnets: List<Magnet>): List<MagnetGroup> =
            magnets.groupBy { Pair(roundCoord(it.latitude), roundCoord(it.longitude)) }
                .map { (key, group) ->
                    MagnetGroup(group, key.first, key.second)
                }

        private fun spreadOverlapping(magnets: List<Magnet>): List<MagnetPin> {
            val groups = magnets.groupBy { Pair(roundCoord(it.latitude), roundCoord(it.longitude)) }
            return groups.values.flatMap { group ->
                if (group.size == 1) {
                    listOf(MagnetPin(group[0], LatLon(group[0].latitude, group[0].longitude)))
                } else {
                    group.mapIndexed { i, magnet ->
                        val angle = 2 * PI * i / group.size
                        MagnetPin(
                            magnet,
                            LatLon(
                                magnet.latitude + SPREAD_RADIUS * cos(angle),
                                magnet.longitude + SPREAD_RADIUS * sin(angle)
                            )
                        )
                    }
                }
            }
        }
    }
}
