package com.travelsouvenirs.main.ui.settings

import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val isPolaroid = appStyle == AppStyle.POLAROID
    val buttonShape = if (isPolaroid) RoundedCornerShape(2.dp) else RoundedCornerShape(50.dp)
    val fieldShape = if (isPolaroid) RoundedCornerShape(2.dp) else RoundedCornerShape(16.dp)

    var newCategoryInput by remember { mutableStateOf("") }
    var duplicateCategoryError by remember { mutableStateOf(false) }
    var commaError by remember { mutableStateOf(false) }
    var pendingDeleteCategory by remember { mutableStateOf<String?>(null) }
    var devOptionsExpanded by remember { mutableStateOf(false) }

    pendingDeleteCategory?.let { categoryToDelete ->
        val affectedCount = allItems.count { it.category == categoryToDelete }
        val bodyText = when (affectedCount) {
            0 -> stringResource(Res.string.dialog_delete_category_no_items, categoryToDelete)
            1 -> stringResource(Res.string.dialog_delete_category_one_item, categoryToDelete)
            else -> stringResource(Res.string.dialog_delete_category_many_items, affectedCount, categoryToDelete)
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

    // ── Shared section content lambdas ──────────────────────────────────────
    // Each captures the surrounding state so it can be placed inside either
    // a PolaroidSectionCard (Polaroid mode) or a plain Card (other themes).

    val allRows = buildList {
        add(DEFAULT_CATEGORY to false)
        customCategories.forEach { add(it to true) }
    }

    val syncContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (currentUser != null) {
                currentUser?.email?.let { email ->
                    Text(
                        stringResource(Res.string.text_signed_in_as, email),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                    shape = buttonShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(Res.string.btn_sign_out)) }
            } else {
                Text(
                    stringResource(Res.string.text_sync_sign_in_prompt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onSignInClick,
                    shape = buttonShape,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(Res.string.btn_sign_in)) }
            }
        }
    }

    val categoriesContent: @Composable () -> Unit = {
        Column {
            allRows.forEachIndexed { index, (name, deletable) ->
                if (isPolaroid) {
                    PolaroidCategoryRow(
                        name = name,
                        index = index,
                        deletable = deletable,
                        onDeleteRequest = { pendingDeleteCategory = name }
                    )
                } else {
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
            }
            if (vm.canAddCategory) {
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
                            commaError = ',' in it
                        },
                        placeholder = { Text(stringResource(Res.string.label_new_category)) },
                        singleLine = true,
                        isError = duplicateCategoryError || commaError,
                        supportingText = when {
                            commaError -> { { Text(stringResource(Res.string.error_category_no_comma)) } }
                            duplicateCategoryError -> { { Text(stringResource(Res.string.error_category_already_exists)) } }
                            else -> null
                        },
                        shape = fieldShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val added = vm.addCategory(newCategoryInput)
                            if (added) { newCategoryInput = ""; duplicateCategoryError = false; commaError = false }
                            else duplicateCategoryError = true
                        },
                        enabled = newCategoryInput.isNotBlank() && !commaError
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

    val devOptionsContent: @Composable () -> Unit = {
        if (isPolaroid) {
            // Flat layout inside the photo area — no nested cards
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.label_appearance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(Res.string.label_theme),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppStyle.entries.chunked(2).forEach { rowStyles ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowStyles.forEach { style ->
                            FilterChip(
                                selected = style == appStyle,
                                onClick = { vm.setAppStyle(style) },
                                label = {
                                    Text(appStyleLabel(style), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(2 - rowStyles.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                Text(
                    when (appStyle) {
                        AppStyle.COSMIC   -> stringResource(Res.string.text_theme_cosmic)
                        AppStyle.GATEWAY  -> stringResource(Res.string.text_theme_gateway)
                        AppStyle.EMBER    -> stringResource(Res.string.text_theme_ember)
                        AppStyle.POLAROID -> stringResource(Res.string.text_theme_polaroid)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.section_map_provider),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        } else {
            Column {
                Text(
                    stringResource(Res.string.label_appearance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
                Card(
                    shape = sectionCardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(Res.string.label_theme),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppStyle.entries.chunked(2).forEach { rowStyles ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowStyles.forEach { style ->
                                    FilterChip(
                                        selected = style == appStyle,
                                        onClick = { vm.setAppStyle(style) },
                                        label = {
                                            Text(appStyleLabel(style), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(2 - rowStyles.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                        Text(
                            when (appStyle) {
                                AppStyle.COSMIC   -> stringResource(Res.string.text_theme_cosmic)
                                AppStyle.GATEWAY  -> stringResource(Res.string.text_theme_gateway)
                                AppStyle.EMBER    -> stringResource(Res.string.text_theme_ember)
                                AppStyle.POLAROID -> stringResource(Res.string.text_theme_polaroid)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    stringResource(Res.string.section_map_provider),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        }
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isPolaroid) 20.dp else 8.dp)
    ) {
        if (isPolaroid) {
            Spacer(modifier = Modifier.height(8.dp))

            PolaroidSectionCard(
                title = stringResource(Res.string.section_sync),
                rotation = -0.5f
            ) { syncContent() }

            PolaroidSectionCard(
                title = stringResource(Res.string.section_categories),
                rotation = 0.4f
            ) { categoriesContent() }

            PolaroidSectionCard(
                title = stringResource(Res.string.section_developer_options),
                rotation = -0.3f,
                collapsible = true,
                expanded = devOptionsExpanded,
                onExpandToggle = { devOptionsExpanded = !devOptionsExpanded }
            ) { devOptionsContent() }

            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // ── Sync & Account ──────────────────────────────────────────────
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
            ) { syncContent() }

            // ── Categories ──────────────────────────────────────────────────
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
            Card(
                shape = sectionCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) { categoriesContent() }

            // ── Developer Options ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { devOptionsExpanded = !devOptionsExpanded }
                    .padding(top = 16.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(Res.string.section_developer_options),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (devOptionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (devOptionsExpanded) { devOptionsContent() }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Upside-down polaroid card: section title sits in the wide top "white space",
 * content fills the "photo" area below — framed by the card's surface colour.
 */
@Composable
private fun PolaroidSectionCard(
    title: String,
    rotation: Float = 0f,
    collapsible: Boolean = false,
    expanded: Boolean = true,
    onExpandToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        modifier = modifier.fillMaxWidth().rotate(rotation)
    ) {
        Column {
            // Caption area — the wide white border at the top (polaroid flipped upside-down)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (collapsible) Modifier.clickable(onClick = onExpandToggle) else Modifier)
                    .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (collapsible) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Photo area — framed inside the polaroid border
            if (!collapsible || expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(1.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun PolaroidCategoryRow(
    name: String,
    index: Int,
    deletable: Boolean,
    onDeleteRequest: () -> Unit
) {
    val rotation = ((index * 3 + name.length % 5) % 9 - 4).toFloat() * 0.8f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.rotate(rotation),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
        ) {
            Text(
                name.uppercase(),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.weight(1f))
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

@Composable
private fun appStyleLabel(style: AppStyle): String = when (style) {
    AppStyle.COSMIC   -> "Cosmic"
    AppStyle.GATEWAY  -> "Gateway"
    AppStyle.EMBER    -> "Ember"
    AppStyle.POLAROID -> "Polaroid"
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
