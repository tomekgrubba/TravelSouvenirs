package com.travelsouvenirs.main.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalSettings
import com.travelsouvenirs.main.domain.Item
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*

/** Displays all items in a searchable, filterable list or tile grid; tapping a card navigates to its detail screen. */
@Composable
fun ListScreen(onItemClick: (Long) -> Unit, onAddClick: () -> Unit) {
    val repository = LocalItemRepository.current
    val categoryFilter = LocalCategoryFilter.current
    val settings = LocalSettings.current
    val viewModel: ListViewModel = viewModel { ListViewModel(settings, repository) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val selectedCategories by categoryFilter.selectedCategories.collectAsState()
    val availableCategories by categoryFilter.availableCategories.collectAsState()
    val sortedItems by viewModel.sortedItems.collectAsState()

    // Apply category filter at screen level so it stays in sync with the map
    val items = remember(sortedItems, selectedCategories) {
        sortedItems.filter { m ->
            m.category in selectedCategories || m.category !in categoryFilter.allCategoriesSet
        }
    }

    val isIconHighlighted = sortOption != SortOption.NAME ||
        selectedCategories != categoryFilter.allCategoriesSet ||
        viewMode != ViewMode.LIST
    val hasActiveFilter = searchQuery.isNotBlank() || selectedCategories != categoryFilter.allCategoriesSet
    val allEmpty = !hasActiveFilter && items.isEmpty()

    var showMenu by remember { mutableStateOf(false) }

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
                    modifier = Modifier.width(240.dp)
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
                            text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
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

                    // Filter by category
                    Text(
                        text = stringResource(Res.string.filter_by_category),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                    availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category, style = MaterialTheme.typography.bodyMedium) },
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
                    Text(stringResource(Res.string.empty_state_no_items))
                }
            }
            items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.empty_state_no_results))
                }
            }
            viewMode == ViewMode.GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        TileCard(item = item, onClick = { onItemClick(item.id) })
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Card(
                            onClick = { onItemClick(item.id) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = item.photoPath,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val place = item.placeName.ifBlank { stringResource(Res.string.no_location) }
                                    val date = "${item.dateAcquired.dayOfMonth} " +
                                        item.dateAcquired.month.name.lowercase()
                                            .replaceFirstChar { it.uppercase() } +
                                        " ${item.dateAcquired.year}"
                                    Text(
                                        text = "$place · $date",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
        MaterialTheme.colorScheme.primary
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
private fun TileCard(item: Item, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            AsyncImage(
                model = item.photoPath,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            )
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val place = item.placeName.ifBlank { stringResource(Res.string.no_location) }
                val month = item.dateAcquired.month.name.lowercase()
                    .replaceFirstChar { it.uppercase() }.take(3)
                val year2d = item.dateAcquired.year % 100
                val tileDate = "$month '${year2d.toString().padStart(2, '0')}"
                Text(
                    text = "$place · $tileDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
