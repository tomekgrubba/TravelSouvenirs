package com.travelsouvenirs.main.ui.settings

import com.travelsouvenirs.main.domain.DEFAULT_CATEGORY
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.data.ItemRepository
import org.koin.compose.koinInject
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.currentKoinScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*

private val sectionCardShape = RoundedCornerShape(16.dp)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}, onSignInClick: () -> Unit = {}) {
    val koinScope = currentKoinScope()
    val vm: SettingsViewModel = viewModel { koinScope.get<SettingsViewModel>() }
    val authRepository: AuthRepository = koinInject()
    val repository: ItemRepository = koinInject()
    val currentUser by authRepository.currentUser.collectAsState()
    val uiState by vm.uiState.collectAsState()
    val customCategories by vm.customCategories.collectAsState()
    val wifiOnlySync by vm.wifiOnlySync.collectAsState()
    val allItems by repository.allItems.collectAsState(initial = emptyList())

    val buttonShape = RoundedCornerShape(2.dp)
    val fieldShape = RoundedCornerShape(2.dp)

    var newCategoryInput by remember { mutableStateOf("") }
    var duplicateCategoryError by remember { mutableStateOf(false) }
    var commaError by remember { mutableStateOf(false) }
    var pendingDeleteCategory by remember { mutableStateOf<String?>(null) }

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
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
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
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { vm.signOut() },
                    shape = buttonShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(Res.string.btn_sign_out)) }
            } else {
                Text(
                    stringResource(Res.string.text_sync_sign_in_prompt),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
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
                PolaroidCategoryRow(
                    name = name,
                    index = index,
                    deletable = deletable,
                    onDeleteRequest = { pendingDeleteCategory = name }
                )
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
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                title = { Text(stringResource(Res.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            PolaroidSectionCard(
                title = stringResource(Res.string.section_sync),
                rotation = -0.5f
            ) { syncContent() }

            if (uiState.modelDownloadable || uiState.isDownloadingModel) {
                PolaroidSectionCard(
                    title = stringResource(Res.string.section_ai),
                    rotation = -0.3f
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(Res.string.hint_download_ai_model),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = vm::downloadAiModel,
                                enabled = !uiState.isDownloadingModel,
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(Res.string.btn_download)) }
                            if (uiState.isDownloadingModel) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }

            PolaroidSectionCard(
                title = stringResource(Res.string.section_categories),
                rotation = 0.4f
            ) { categoriesContent() }

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
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, letterSpacing = 0.5.sp),
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
