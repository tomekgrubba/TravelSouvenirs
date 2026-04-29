package com.travelsouvenirs.main.ui.settings

import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelsouvenirs.main.di.LocalAuthRepository
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalSettings
import com.travelsouvenirs.main.platform.MapProviderType
import com.travelsouvenirs.main.platform.MapTheme
import com.travelsouvenirs.main.platform.nativeMapProviderName
import com.travelsouvenirs.main.theme.AppStyle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*

private val sectionCardShape = RoundedCornerShape(16.dp)

@Composable
fun SettingsScreen(onSignInClick: () -> Unit = {}) {
    val settings = LocalSettings.current
    val repository = LocalItemRepository.current
    val authRepository = LocalAuthRepository.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(settings, repository) }
    val currentUser by authRepository.currentUser.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refreshCategories() }

    val customCategories by vm.customCategories.collectAsState()
    val appStyle by vm.appStyle.collectAsState()
    val mapProvider by vm.mapProvider.collectAsState()
    val mapTheme by vm.mapTheme.collectAsState()
    val wifiOnlySync by vm.wifiOnlySync.collectAsState()
    val allItems by repository.allItems.collectAsState(initial = emptyList())

    var newCategoryInput by remember { mutableStateOf("") }
    var duplicateCategoryError by remember { mutableStateOf(false) }
    var pendingDeleteCategory by remember { mutableStateOf<String?>(null) }

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
                    onClick = { vm.deleteCategory(categoryToDelete); pendingDeleteCategory = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(Res.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCategory = null }) { Text(stringResource(Res.string.btn_cancel)) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Sync & Account ──────────────────────────────────────────────────
        Text(
            stringResource(Res.string.section_sync),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
        )
        Card(
            shape = sectionCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentUser != null) {
                    currentUser?.email?.let { email ->
                        Text(
                            "Signed in as $email",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(Res.string.label_wifi_only_sync),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = wifiOnlySync, onCheckedChange = vm::setWifiOnlySync)
                    }
                    Text(
                        stringResource(Res.string.hint_wifi_only_sync),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { scope.launch { authRepository.signOut() } },
                        shape = RoundedCornerShape(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign out") }
                } else {
                    Text(
                        "Sign in to sync your items across devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onSignInClick,
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign in") }
                }
            }
        }

        // ── Appearance ───────────────────────────────────────────────────────
        Text(
            "Appearance",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
        Card(
            shape = sectionCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppStyle.entries.forEach { style ->
                        FilterChip(
                            selected = style == appStyle,
                            onClick = { vm.setAppStyle(style) },
                            label = {
                                Text(
                                    appStyleLabel(style),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    when (appStyle) {
                        AppStyle.COSMIC -> "Cool deep-space purples and indigos."
                        AppStyle.EMBER  -> "Warm charcoal with amber and copper accents."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Map ──────────────────────────────────────────────────────────────
        Text(
            stringResource(Res.string.section_map_provider),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
        Text(
            stringResource(Res.string.text_map_provider_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            shape = sectionCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                Text(
                    stringResource(Res.string.section_map_theme),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = theme == mapTheme,
                            onClick = { vm.setMapTheme(theme) },
                            label = { Text(mapThemeLabel(theme), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ── Categories ──────────────────────────────────────────────────────
        Text(
            stringResource(Res.string.section_categories),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
        Text(
            stringResource(Res.string.text_categories_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val allRows = buildList {
            add(DEFAULT_CATEGORY to false)
            customCategories.forEach { add(it to true) }
        }
        Card(
            shape = sectionCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                allRows.forEachIndexed { index, (name, deletable) ->
                    val rowBg = if (index % 2 == 0)
                        MaterialTheme.colorScheme.surfaceContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    CategoryRow(
                        name = name,
                        deletable = deletable,
                        background = rowBg,
                        onDeleteRequest = { pendingDeleteCategory = name }
                    )
                }

                if (vm.canAddCategory) {
                    val rowBg = if (allRows.size % 2 == 0)
                        MaterialTheme.colorScheme.surfaceContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
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
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val added = vm.addCategory(newCategoryInput)
                                if (added) { newCategoryInput = ""; duplicateCategoryError = false }
                                else duplicateCategoryError = true
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
                    Text(
                        stringResource(Res.string.max_categories_reached),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun appStyleLabel(style: AppStyle): String = when (style) {
    AppStyle.COSMIC -> "Cosmic"
    AppStyle.EMBER  -> "Ember"
}

@Composable
private fun mapProviderLabel(provider: MapProviderType): String = when (provider) {
    MapProviderType.NATIVE -> nativeMapProviderName()
    MapProviderType.OPEN_STREET_MAP -> stringResource(Res.string.map_provider_osm)
}

@Composable
private fun mapThemeLabel(theme: MapTheme): String = when (theme) {
    MapTheme.LIGHT -> stringResource(Res.string.map_theme_light)
    MapTheme.DARK -> stringResource(Res.string.map_theme_dark)
}

@Composable
private fun CategoryRow(
    name: String,
    deletable: Boolean,
    background: Color,
    onDeleteRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
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
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
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
