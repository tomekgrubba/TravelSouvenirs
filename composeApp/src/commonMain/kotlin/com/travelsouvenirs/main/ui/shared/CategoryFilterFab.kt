package com.travelsouvenirs.main.ui.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.Res
import travelsouvenirs.composeapp.generated.resources.cd_filter_category
import travelsouvenirs.composeapp.generated.resources.filter_by_category
import travelsouvenirs.composeapp.generated.resources.filter_all

@Composable
fun CategoryFilterFab(
    availableCategories: List<String>,
    selectedCategory: String?,
    categoryCounts: Map<String, Int>,
    onSelectCategory: (String?) -> Unit,
    modifier: Modifier = Modifier,
    isTablet: Boolean = false,
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    // Filter is active if a specific category is selected AND there are multiple categories available
    val isFilterActive = selectedCategory != null && availableCategories.size > 1
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
                selectedCategory = selectedCategory,
                categoryCounts = categoryCounts,
                onSelectCategory = {
                    onSelectCategory(it)
                    showFilterMenu = false
                }
            )
        }
    }
}

@Composable
fun CategoryFilterMenuSection(
    availableCategories: List<String>,
    selectedCategory: String?,
    categoryCounts: Map<String, Int>,
    onSelectCategory: (String?) -> Unit
) {
    Text(
        text = stringResource(Res.string.filter_by_category),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
    )

    // Only show "All" option if there are multiple categories with items
    if (availableCategories.size > 1) {
        DropdownMenuItem(
            modifier = Modifier.height(38.dp),
            text = {
                Text(
                    text = stringResource(Res.string.filter_all),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal
                )
            },
            trailingIcon = {
                if (selectedCategory == null) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            onClick = { onSelectCategory(null) }
        )
    }

    availableCategories.forEach { category ->
        DropdownMenuItem(
            modifier = Modifier.height(38.dp),
            text = {
                val count = categoryCounts[category] ?: 0
                Text(
                    text = "$category ($count)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal
                )
            },
            trailingIcon = {
                if (selectedCategory == category) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            onClick = { onSelectCategory(category) }
        )
    }
}
