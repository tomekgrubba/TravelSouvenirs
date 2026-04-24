package com.travelsouvenirs.main.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.di.LocalMagnetRepository

/** Displays all items in a searchable, filterable list; tapping a row navigates to its detail screen. */
@Composable
fun ListScreen(onItemClick: (Long) -> Unit) {
    val repository = LocalMagnetRepository.current
    val categoryFilter = LocalCategoryFilter.current
    val viewModel: ListViewModel = viewModel { ListViewModel(repository) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val selectedCategories by categoryFilter.selectedCategories.collectAsState()
    val availableCategories by categoryFilter.availableCategories.collectAsState()
    val sortedMagnets by viewModel.sortedMagnets.collectAsState()

    // Apply category filter at screen level so it stays in sync with the map
    val magnets = remember(sortedMagnets, selectedCategories) {
        sortedMagnets.filter { m ->
            m.category in selectedCategories || m.category !in categoryFilter.allCategoriesSet
        }
    }

    val isIconHighlighted = sortOption != SortOption.NAME || selectedCategories != categoryFilter.allCategoriesSet
    val hasActiveFilter = searchQuery.isNotBlank() || selectedCategories != categoryFilter.allCategoriesSet
    val allEmpty = !hasActiveFilter && magnets.isEmpty()

    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search items…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort and filter",
                        tint = if (isIconHighlighted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.width(240.dp)
                ) {
                    // — Sort group —
                    Text(
                        text = "Sort by",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    listOf(
                        SortOption.NAME to "Name",
                        SortOption.DATE to "Date acquired",
                        SortOption.LOCATION to "Location name"
                    ).forEach { (option, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            trailingIcon = {
                                if (sortOption == option) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                viewModel.onSortChange(option)
                                showMenu = false
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // — Filter group —
                    Text(
                        text = "Filter by category",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            leadingIcon = {
                                Checkbox(
                                    checked = category in selectedCategories,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = { categoryFilter.toggleCategoryFilter(category) }
                        )
                    }
                }
            }
        }

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
