package com.travelsouvenirs.main.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.ui.shared.CategoryFilterMenuSection
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.currentKoinScope
import com.travelsouvenirs.main.domain.Item
import com.travelsouvenirs.main.domain.SortOption
import com.travelsouvenirs.main.util.formatDisplayDate
import com.travelsouvenirs.main.util.formatTileDisplay
import com.travelsouvenirs.main.util.localImageModel
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*

/** Displays all items in a searchable, filterable list or tile grid; tapping a card navigates to its detail screen. */
@Composable
fun ListScreen(onItemClick: (Long) -> Unit, onAddClick: () -> Unit) {
    val categoryFilter = LocalCategoryFilter.current
    val koinScope = currentKoinScope()
    val viewModel: ListViewModel = viewModel { koinScope.get<ListViewModel>() }

    val uiState by viewModel.uiState.collectAsState()
    val selectedCategories by categoryFilter.selectedCategories.collectAsState()
    val availableCategories by categoryFilter.availableCategories.collectAsState()
    val categoryCounts by categoryFilter.categoryCounts.collectAsState()
    val selectedCategory by categoryFilter.selectedCategory.collectAsState()
    val sortedItems by viewModel.sortedItems.collectAsState()

    val searchQuery = uiState.searchQuery
    val sortOption = uiState.sortOption
    val viewMode = uiState.viewMode

    val items = remember(sortedItems, selectedCategories) { categoryFilter.filterItems(sortedItems) }

    val isIconHighlighted = sortOption != SortOption.NAME ||
        selectedCategories != categoryFilter.allCategoriesSet ||
        viewMode != ViewMode.LIST
    val hasActiveFilter = searchQuery.isNotBlank() || selectedCategories != categoryFilter.allCategoriesSet
    val allEmpty = !hasActiveFilter && items.isEmpty()

    var showMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxDropdownHeight = maxHeight - 144.dp
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text(stringResource(Res.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(Res.string.cd_sort_filter),
                            tint = if (isIconHighlighted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .width(240.dp)
                            .heightIn(max = maxDropdownHeight),
                        offset = DpOffset(x = (-192).dp, y = 48.dp)
                    ) {
                        // View as — top of menu with side-by-side icon+label buttons
                        Text(
                            text = stringResource(Res.string.view_as),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ViewModeButton(
                                icon = Icons.Default.ViewList,
                                label = stringResource(Res.string.view_list),
                                selected = viewMode == ViewMode.LIST,
                                onClick = { viewModel.onViewModeChange(ViewMode.LIST); showMenu = false },
                                modifier = Modifier.weight(1f)
                            )
                            ViewModeButton(
                                icon = Icons.Default.GridView,
                                label = stringResource(Res.string.view_tile),
                                selected = viewMode == ViewMode.GRID,
                                onClick = { viewModel.onViewModeChange(ViewMode.GRID); showMenu = false },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.size(8.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        // Sort by
                        Text(
                            text = stringResource(Res.string.sort_by),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                        listOf(
                            SortOption.NAME to stringResource(Res.string.sort_name),
                            SortOption.DATE to stringResource(Res.string.sort_date),
                            SortOption.LOCATION to stringResource(Res.string.sort_location)
                        ).forEach { (option, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
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

                        Spacer(modifier = Modifier.size(4.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        CategoryFilterMenuSection(
                            availableCategories = availableCategories,
                            selectedCategory = selectedCategory,
                            categoryCounts = categoryCounts,
                            onSelectCategory = {
                                categoryFilter.selectCategory(it)
                                showMenu = false
                            }
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                }
            }

            when {
                allEmpty -> {
                    Box(
                        modifier = Modifier.fillMaxSize().clickable { onAddClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(32.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Text(stringResource(Res.string.empty_state_no_items), modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
                items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier.padding(32.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Text(stringResource(Res.string.empty_state_no_results), modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
                viewMode == ViewMode.GRID -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val columns = if (maxWidth >= 600.dp) 3 else 2
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(items, key = { it.id }) { item ->
                                PolaroidTileCard(item = item, onClick = { onItemClick(item.id) })
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            PolaroidListCard(item = item, onClick = { onItemClick(item.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun PolaroidListCard(item: Item, onClick: () -> Unit) {
    val rotation = ((item.id % 9L) - 4L).toFloat() * 0.45f
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .rotate(rotation)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini-polaroid thumbnail: equal frame on sides/top, thicker at bottom
            Box(modifier = Modifier.padding(top = 3.dp, start = 3.dp, end = 3.dp, bottom = 8.dp)) {
                AsyncImage(
                    model = localImageModel(item.photoPath),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val place = item.placeName.ifBlank { stringResource(Res.string.no_location) }
                val date = item.dateAcquired.formatDisplayDate(stringResource(Res.string.date_none))
                Text(
                    text = "$place · $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PolaroidTileCard(item: Item, onClick: () -> Unit) {
    val rotation = ((item.id % 9L) - 4L).toFloat() * 0.45f
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        modifier = Modifier.rotate(rotation)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp)) {
                AsyncImage(
                    model = localImageModel(item.photoPath),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val place = item.placeName.ifBlank { stringResource(Res.string.no_location) }
                val tileDate = item.dateAcquired.formatTileDisplay()
                val displayInfo = if (tileDate.isEmpty()) place else "$place · $tileDate"
                Text(
                    text = displayInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

