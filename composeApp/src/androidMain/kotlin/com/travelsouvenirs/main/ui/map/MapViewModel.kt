package com.travelsouvenirs.main.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.domain.Magnet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.cos
import kotlin.math.sin

data class MagnetPin(val magnet: Magnet, val position: LatLng)
data class MagnetGroup(val magnets: List<Magnet>, val centerLat: Double, val centerLng: Double)

class MapViewModel(repository: MagnetRepository) : ViewModel() {

    val magnetPins: StateFlow<List<MagnetPin>> = repository.allMagnets
        .map(::spreadOverlapping)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val magnetGroups: StateFlow<List<MagnetGroup>> = repository.allMagnets
        .map(::computeGroups)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val magnets: StateFlow<List<Magnet>> = repository.allMagnets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        private const val SPREAD_RADIUS = 0.0004

        // Groups magnets that would visually overlap at the given zoom level.
        // threshold = ~100px of map space in degrees at this zoom.
        fun groupByZoom(magnets: List<Magnet>, zoom: Float): List<MagnetGroup> {
            if (magnets.isEmpty()) return emptyList()
            val threshold = 60.0 * 360.0 / (256.0 * Math.pow(2.0, zoom.toDouble()))
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

        private fun computeGroups(magnets: List<Magnet>): List<MagnetGroup> =
            magnets.groupBy {
                Pair(
                    Math.round(it.latitude * 10_000) / 10_000.0,
                    Math.round(it.longitude * 10_000) / 10_000.0
                )
            }.map { (key, group) ->
                MagnetGroup(group, key.first, key.second)
            }

        private fun spreadOverlapping(magnets: List<Magnet>): List<MagnetPin> {
            // Round to 4 dp (~11 m grid) to detect "same location"
            val groups = magnets.groupBy {
                Pair(
                    Math.round(it.latitude * 10_000) / 10_000.0,
                    Math.round(it.longitude * 10_000) / 10_000.0
                )
            }
            return groups.values.flatMap { group ->
                if (group.size == 1) {
                    listOf(MagnetPin(group[0], LatLng(group[0].latitude, group[0].longitude)))
                } else {
                    group.mapIndexed { i, magnet ->
                        val angle = 2 * Math.PI * i / group.size
                        MagnetPin(
                            magnet,
                            LatLng(
                                magnet.latitude + SPREAD_RADIUS * cos(angle),
                                magnet.longitude + SPREAD_RADIUS * sin(angle)
                            )
                        )
                    }
                }
            }
        }
    }

    class Factory(private val repository: MagnetRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MapViewModel(repository) as T
    }
}
