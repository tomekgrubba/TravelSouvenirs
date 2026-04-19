package com.travelsouvenirs.main.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.travelsouvenirs.main.data.MagnetDatabase
import com.travelsouvenirs.main.data.MagnetRepository

/** Displays all items in a searchable list; tapping a row navigates to its detail screen. */
@Composable
fun ListScreen(onItemClick: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = remember {
        MagnetRepository(MagnetDatabase.getDatabase(context).magnetDao())
    }
    val viewModel: ListViewModel = viewModel(factory = ListViewModel.Factory(repository))

    val searchQuery by viewModel.searchQuery.collectAsState()
    val magnets by viewModel.filteredMagnets.collectAsState()
    val allEmpty = searchQuery.isBlank() && magnets.isEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Search items…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            allEmpty -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No items yet.\nTap + to add your first!")
                }
            }
            magnets.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results.")
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(magnets, key = { it.id }) { magnet ->
                        ListItem(
                            headlineContent = { Text(magnet.name) },
                            supportingContent = {
                                val place = magnet.placeName.ifBlank { "No location" }
                                val date = "${magnet.dateAcquired.dayOfMonth} " +
                                    magnet.dateAcquired.month.name.lowercase()
                                        .replaceFirstChar { it.uppercase() } +
                                    " ${magnet.dateAcquired.year}"
                                Text("$place · $date")
                            },
                            leadingContent = {
                                AsyncImage(
                                    model = magnet.photoPath,
                                    contentDescription = magnet.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                )
                            },
                            modifier = Modifier.clickable { onItemClick(magnet.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
