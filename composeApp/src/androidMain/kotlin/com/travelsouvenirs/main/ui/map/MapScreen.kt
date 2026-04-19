package com.travelsouvenirs.main.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.travelsouvenirs.main.data.MagnetDatabase
import com.travelsouvenirs.main.data.MagnetRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onAddClick: () -> Unit,
    onPinClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val repository = remember {
        MagnetRepository(MagnetDatabase.getDatabase(context).magnetDao())
    }
    val viewModel: MapViewModel = viewModel(factory = MapViewModel.Factory(repository))
    val magnets by viewModel.magnets.collectAsState()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Magnets") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add magnet")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false)
            ) {
                magnets.forEach { magnet ->
                    Marker(
                        state = MarkerState(LatLng(magnet.latitude, magnet.longitude)),
                        title = magnet.name,
                        snippet = magnet.placeName,
                        onClick = {
                            onPinClick(magnet.id)
                            true
                        }
                    )
                }
            }

            if (magnets.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                ) {
                    Text(
                        "No magnets yet.\nTap + to add your first!",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
