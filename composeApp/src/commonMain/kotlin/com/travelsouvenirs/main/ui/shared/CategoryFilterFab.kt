package com.travelsouvenirs.main.ui.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.Res
import travelsouvenirs.composeapp.generated.resources.cd_filter_category
import travelsouvenirs.composeapp.generated.resources.filter_by_category

@Composable
fun CategoryFilterFab(
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    categoryCounts: Map<String, Int>,
    onToggleCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    isTablet: Boolean = false,
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    val isFilterActive = selectedCategories.size < availableCategories.size
    val containerColor = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isFilterActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val iconSize = if (isTablet) 28.dp else 24.dp

    Box(modifier = modifier) {
        if (isTablet) {
            FloatingActionButton(
                onClick = { showFilterMenu = true },
                containerColor = containerColor,
                contentColor = contentColor,
            ) {
                Icon(Icons.Default.FilterList, contentDescription = stringResource(Res.string.cd_filter_category), modifier = Modifier.size(iconSize))
            }
        } else {
            SmallFloatingActionButton(
                onClick = { showFilterMenu = true },
                containerColor = containerColor,
                contentColor = contentColor,
            ) {
                Icon(Icons.Default.FilterList, contentDescription = stringResource(Res.string.cd_filter_category))
            }
        }

        DropdownMenu(
            expanded = showFilterMenu,
            onDismissRequest = { showFilterMenu = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(220.dp)
        ) {
            CategoryFilterMenuSection(
                availableCategories = availableCategories,
                selectedCategories = selectedCategories,
                categoryCounts = categoryCounts,
                onToggleCategory = onToggleCategory
            )
        }
    }
}

@Composable
fun CategoryFilterMenuSection(
    availableCategories: List<String>,
    selectedCategories: Set<String>,
    categoryCounts: Map<String, Int>,
    onToggleCategory: (String) -> Unit
) {
    Text(
        text = stringResource(Res.string.filter_by_category),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
    availableCategories.forEach { category ->
        DropdownMenuItem(
            text = {
                val count = categoryCounts[category] ?: 0
                Text("$category ($count)")
            },
            leadingIcon = {
                Checkbox(
                    checked = category in selectedCategories,
                    onCheckedChange = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            onClick = { onToggleCategory(category) }
        )
    }
}
