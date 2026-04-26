package com.travelsouvenirs.main.ui.settings

import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.travelsouvenirs.main.platform.MapProviderType
import com.travelsouvenirs.main.platform.nativeMapProviderName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalSettings
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*

/** Settings screen — categories management at top, persistent notes at bottom. */
@Composable
fun SettingsScreen() {
    val settings = LocalSettings.current
    val repository = LocalItemRepository.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(settings, repository) }

    // Re-read categories from Settings each time this panel enters composition, so that
    // categories created on the Add Item screen are visible without an app restart.
    LaunchedEffect(Unit) { vm.refreshCategories() }

    val notes by vm.notes.collectAsState()
    val customCategories by vm.customCategories.collectAsState()
    val mapProvider by vm.mapProvider.collectAsState()
    val allItems by repository.allItems.collectAsState(initial = emptyList())

    var newCategoryInput by remember { mutableStateOf("") }
    var duplicateCategoryError by remember { mutableStateOf(false) }
    var pendingDeleteCategory by remember { mutableStateOf<String?>(null) }

    // Confirmation dialog
    pendingDeleteCategory?.let { categoryToDelete ->
        val affectedCount = allItems.count { it.category == categoryToDelete }
        val bodyText = when (affectedCount) {
            0 -> "No items are currently using \"$categoryToDelete\"."
            1 -> "1 item is currently assigned to \"$categoryToDelete\" and will be moved to Default."
            else -> "$affectedCount items are currently assigned to \"$categoryToDelete\" and will all be moved to Default."
        }
        AlertDialog(
            onDismissRequest = { pendingDeleteCategory = null },
            title = { Text(stringResource(Res.string.dialog_delete_category_title, categoryToDelete)) },
            text = { Text(bodyText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteCategory(categoryToDelete)
                        pendingDeleteCategory = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(Res.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCategory = null }) { Text(stringResource(Res.string.btn_cancel)) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // ── Map Provider ────────────────────────────────────────────────────
        Text(
            stringResource(Res.string.section_map_provider),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        Text(
            stringResource(Res.string.text_map_provider_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapProviderType.entries.forEach { provider ->
                FilterChip(
                    selected = provider == mapProvider,
                    onClick = { vm.setMapProvider(provider) },
                    label = { Text(mapProviderLabel(provider), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        // ── Categories ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
        ) {
            Text(
                stringResource(Res.string.section_categories),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                stringResource(Res.string.text_categories_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    CategoryRow(
                        name = DEFAULT_CATEGORY,
                        deletable = false,
                        onDeleteRequest = {}
                    )

                    customCategories.forEach { name ->
                        HorizontalDivider()
                        CategoryRow(
                            name = name,
                            deletable = true,
                            onDeleteRequest = { pendingDeleteCategory = name }
                        )
                    }

                    if (vm.canAddCategory) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newCategoryInput,
                                onValueChange = {
                                    newCategoryInput = it
                                    duplicateCategoryError = false
                                },
                                placeholder = { Text(stringResource(Res.string.label_new_category)) },
                                singleLine = true,
                                isError = duplicateCategoryError,
                                supportingText = if (duplicateCategoryError) {
                                    { Text(stringResource(Res.string.error_category_already_exists)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val added = vm.addCategory(newCategoryInput)
                                    if (added) {
                                        newCategoryInput = ""
                                        duplicateCategoryError = false
                                    } else {
                                        duplicateCategoryError = true
                                    }
                                },
                                enabled = newCategoryInput.isNotBlank()
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(Res.string.cd_add_category),
                                    tint = if (newCategoryInput.isNotBlank())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }

                    if (!vm.canAddCategory) {
                        HorizontalDivider()
                        Text(
                            stringResource(Res.string.max_categories_reached),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // ── Notes ────────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)) {
            Text(
                stringResource(Res.string.section_notes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = notes,
                onValueChange = vm::onNotesChange,
                label = { Text(stringResource(Res.string.label_your_notes)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                maxLines = Int.MAX_VALUE
            )
        }
    }
}

@Composable
private fun mapProviderLabel(provider: MapProviderType): String = when (provider) {
    MapProviderType.NATIVE -> nativeMapProviderName()
    MapProviderType.OPEN_STREET_MAP -> stringResource(Res.string.map_provider_osm)
}

@Composable
private fun CategoryRow(name: String, deletable: Boolean, onDeleteRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (deletable) {
            IconButton(onClick = onDeleteRequest, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.cd_delete_item, name),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(Res.string.cd_builtin_category),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
