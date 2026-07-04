package com.travelsouvenirs.main.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.currentKoinScope
import org.koin.core.parameter.parametersOf
import com.travelsouvenirs.main.platform.PlatformMapLocationPicker
import com.travelsouvenirs.main.platform.rememberCameraCapture
import com.travelsouvenirs.main.platform.rememberLocationPermissionLauncher
import com.travelsouvenirs.main.platform.rememberPhotoPicker
import com.travelsouvenirs.main.util.formatDisplayDate
import com.travelsouvenirs.main.util.localImageModel
import com.travelsouvenirs.main.di.LocalAppViewModel
import com.travelsouvenirs.main.domain.LatLon
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.draw.clip


/** Form for creating or editing an item; shows "Edit Item" title when [itemId] is non-null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(onSaved: () -> Unit, onBack: () -> Unit, itemId: Long? = null) {
    val koinScope = currentKoinScope()
    val viewModel: AddItemViewModel = viewModel(key = itemId?.toString() ?: "add") {
        koinScope.get<AddItemViewModel>(parameters = { parametersOf(itemId) })
    }

    val uiState by viewModel.uiState.collectAsState()
    val photoPath = uiState.photoPath
    val name = uiState.name
    val notes = uiState.notes
    val dateAcquired = uiState.dateAcquired
    val placeName = uiState.placeName
    val category = uiState.category
    val availableCategories = uiState.availableCategories
    val isSaved = uiState.isSaved
    val showLocationDialog = uiState.showLocationDialog

    val appViewModel = LocalAppViewModel.current

    LaunchedEffect(isSaved) {
        if (isSaved) {
            appViewModel.zoomToLocation(LatLon(uiState.latitude, uiState.longitude))
            onSaved()
        }
    }

    val launchPhotoPicker = rememberPhotoPicker { path, exifLat, exifLng, exifDate ->
        path?.let { viewModel.onPhotoSelected(it, exifLat, exifLng, exifDate) }
    }
    val launchCamera = rememberCameraCapture { path, exifLat, exifLng ->
        path?.let { viewModel.onPhotoSelected(it, exifLat, exifLng) }
    }
    val requestLocationPermission = rememberLocationPermissionLauncher(
        onGranted = { viewModel.fetchCurrentLocation() }
    )

    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var duplicateCategoryError by remember { mutableStateOf(false) }
    val isPolaroid = true
    val fieldShape = RoundedCornerShape(2.dp)
    val buttonShape = RoundedCornerShape(2.dp)

    val aiSuggestedPlace = uiState.aiSuggestedPlace
    if (aiSuggestedPlace != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissAiSuggestion() },
            title = { Text("Location detected") },
            text = { Text("This looks like $aiSuggestedPlace to me. Do you want me to prefill the fields?") },
            confirmButton = {
                Button(onClick = { viewModel.onAcceptAiSuggestion() }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissAiSuggestion() }) { Text("No, enter manually") }
            }
        )
    }

    if (showDatePicker) {
        CustomDatePickerDialog(
            initialDate = dateAcquired,
            onDismissRequest = { showDatePicker = false },
            onConfirm = { date ->
                viewModel.onDateChange(date)
                showDatePicker = false
            }
        )
    }

    if (showLocationDialog) {
        LocationPickerDialog(
            viewModel = viewModel,
            onRequestGps = { requestLocationPermission() },
            isPolaroid = isPolaroid
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddCategoryDialog = false
                newCategoryInput = ""
                duplicateCategoryError = false
            },
            title = { Text(stringResource(Res.string.dialog_add_category_title)) },
            text = {
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
                    } else null
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val added = viewModel.addCategoryOnTheFly(newCategoryInput)
                        if (added) {
                            showAddCategoryDialog = false
                            newCategoryInput = ""
                            duplicateCategoryError = false
                        } else {
                            duplicateCategoryError = true
                        }
                    },
                    enabled = newCategoryInput.isNotBlank()
                ) { Text(stringResource(Res.string.btn_add)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddCategoryDialog = false
                    newCategoryInput = ""
                    duplicateCategoryError = false
                }) { Text(stringResource(Res.string.btn_cancel)) }
            }
        )
    }

    val isFormValid = photoPath != null && name.isNotBlank() && placeName.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = if (isPolaroid) TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) else TopAppBarDefaults.topAppBarColors(),
                title = { Text(stringResource(if (itemId != null) Res.string.title_edit_item else Res.string.title_add_item)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isPolaroid) {
                Card(
                    shape = RoundedCornerShape(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .rotate(-1.5f)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                        ) {
                            if (photoPath != null) {
                                Box {
                                    AsyncImage(
                                        model = localImageModel(photoPath),
                                        contentDescription = stringResource(Res.string.cd_item_photo),
                                        modifier = Modifier.fillMaxWidth().height(200.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                            .clip(CircleShape)
                                            .clickable { viewModel.onRemovePhoto() }
                                            .padding(7.5.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(27.dp)
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        stringResource(Res.string.no_photo_selected),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = { launchCamera() },
                                            shape = buttonShape,
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.btn_take_photo),
                                                maxLines = 2,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { launchPhotoPicker() },
                                            shape = buttonShape,
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.btn_gallery),
                                                maxLines = 2,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(32.dp))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.label_photo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    if (photoPath != null) {
                        Box {
                            AsyncImage(
                                model = localImageModel(photoPath),
                                contentDescription = stringResource(Res.string.cd_item_photo),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                    .clip(CircleShape)
                                    .clickable { viewModel.onRemovePhoto() }
                                    .padding(7.5.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(27.dp)
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                stringResource(Res.string.no_photo_selected),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { launchCamera() },
                                    shape = buttonShape,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(Res.string.btn_take_photo),
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                                OutlinedButton(
                                    onClick = { launchPhotoPicker() },
                                    shape = buttonShape,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(Res.string.btn_gallery),
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.openLocationDialog() },
                shape = buttonShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    if (placeName.isBlank()) stringResource(Res.string.label_set_location) else placeName,
                    maxLines = 1
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(Res.string.label_name)) },
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(Res.string.label_notes)) },
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            ExposedDropdownMenuBox(
                expanded = showCategoryDropdown,
                onExpandedChange = { showCategoryDropdown = it }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.label_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                    shape = fieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = showCategoryDropdown,
                    onDismissRequest = { showCategoryDropdown = false }
                ) {
                    availableCategories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                viewModel.onCategoryChange(option)
                                showCategoryDropdown = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                    if (viewModel.canAddCategory) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(Res.string.menu_add_category),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                showCategoryDropdown = false
                                showAddCategoryDialog = true
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            OutlinedTextField(
                value = dateAcquired.formatDisplayDate(stringResource(Res.string.date_none)),
                onValueChange = {},
                label = { Text(stringResource(Res.string.label_date_acquired)) },
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.cd_pick_date))
                    }
                }
            )

            Button(
                onClick = viewModel::saveItem,
                enabled = isFormValid,
                shape = buttonShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.btn_save_item))
            }
        }
    }
}

@Composable
private fun LocationPickerDialog(
    viewModel: AddItemViewModel,
    onRequestGps: () -> Unit,
    isPolaroid: Boolean = false
) {
    val dialogFieldShape = if (isPolaroid) RoundedCornerShape(2.dp) else RoundedCornerShape(16.dp)
    val dialogButtonShape = if (isPolaroid) RoundedCornerShape(2.dp) else RoundedCornerShape(50.dp)
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery = uiState.searchQuery
    val searchResults = uiState.searchResults
    val isSearching = uiState.isSearching
    val isLocating = uiState.isLocating
    val locationError = uiState.locationError
    val pendingLat = uiState.pendingLat
    val pendingLng = uiState.pendingLng
    val cameraMoveId = uiState.cameraMoveId

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var isScrollEnabled by remember { mutableStateOf(true) }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(searchQuery)) }
    LaunchedEffect(searchQuery) {
        if (textFieldValue.text != searchQuery) {
            textFieldValue = TextFieldValue(searchQuery, TextRange(searchQuery.length))
        }
        if (searchQuery.isEmpty()) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    // Dismiss keyboard when a map tap or GPS sets a location
    LaunchedEffect(pendingLat, pendingLng) {
        if (pendingLat != null) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    Dialog(
        onDismissRequest = { viewModel.closeLocationDialog() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = maxHeight * 0.9f),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp
            ) {
                // Outer Column fills the bounded Surface and shrinks its inner area when keyboard shows.
                // Buttons are outside the scrollable section so they are always reachable.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    // Scrollable content: title, search, map
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState(), enabled = isScrollEnabled)
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
                    ) {
                        Text(
                            stringResource(Res.string.dialog_set_location),
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Search field + my-location icon button inline
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedTextField(
                                value = textFieldValue,
                                onValueChange = { newValue ->
                                    textFieldValue = newValue
                                    viewModel.onSearchQueryChange(newValue.text)
                                },
                                label = { Text(stringResource(Res.string.label_city_or_place)) },
                                shape = dialogFieldShape,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                            )
                            IconButton(
                                onClick = onRequestGps,
                                enabled = !isLocating
                            ) {
                                if (isLocating && pendingLat == null) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.Default.MyLocation,
                                        contentDescription = stringResource(Res.string.cd_my_location)
                                    )
                                }
                            }
                        }

                        locationError?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Map with floating search results overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            isScrollEnabled = !event.changes.any { it.pressed }
                                        }
                                    }
                                }
                        ) {
                            PlatformMapLocationPicker(
                                selectedLat = pendingLat,
                                selectedLng = pendingLng,
                                cameraMoveId = cameraMoveId,
                                onLocationPicked = viewModel::onPendingLocationChanged,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Search results float above the map
                            val showSearchOverlay = isSearching || searchResults.isNotEmpty() || searchQuery.length >= 2
                            if (showSearchOverlay) {
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 40.dp, max = 150.dp)
                                    ) {
                                        when {
                                            isSearching -> CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp).align(Alignment.Center)
                                            )
                                            searchResults.isNotEmpty() -> LazyColumn(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                                            ) {
                                                items(searchResults) { place ->
                                                    Card(
                                                        onClick = {
                                                            keyboardController?.hide()
                                                            focusManager.clearFocus()
                                                            viewModel.onPlaceSelected(place)
                                                        },
                                                        shape = RoundedCornerShape(16.dp),
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                                        ),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = place.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            else -> Text(
                                                stringResource(Res.string.no_results_found),
                                                modifier = Modifier.align(Alignment.Center).padding(8.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Hint when no pin has been placed yet
                            if (pendingLat == null && !isSearching && searchResults.isEmpty()) {
                                Card(modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                                    Text(
                                        stringResource(Res.string.map_picker_hint),
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    // Buttons pinned outside the scrollable area — always visible above the keyboard
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.closeLocationDialog() },
                            shape = dialogButtonShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(Res.string.btn_cancel))
                        }
                        Button(
                            onClick = { viewModel.confirmLocation() },
                            enabled = pendingLat != null && !isLocating,
                            shape = dialogButtonShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isLocating && pendingLat != null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            Text(stringResource(Res.string.btn_confirm))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDatePickerDialog(
    initialDate: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    val initialTab = when {
        initialDate == null -> 0
        !initialDate.contains("-") -> 0
        initialDate.count { it == '-' } == 1 -> 1
        else -> 2
    }

    var selectedTab by remember { mutableStateOf(initialTab) }

    val initialParts = initialDate?.split("-")
    val initialYear = initialParts?.getOrNull(0)?.toIntOrNull() ?: 2026
    val initialMonth = initialParts?.getOrNull(1)?.toIntOrNull() ?: 5

    var selectedYear by remember { mutableStateOf(initialYear) }
    var selectedMonth by remember { mutableStateOf(initialMonth) }

    val initialSelectedMillis = if (initialDate != null && initialParts?.size == 3) {
        val y = initialParts[0].toIntOrNull() ?: 2026
        val m = initialParts[1].toIntOrNull() ?: 5
        val d = initialParts[2].toIntOrNull() ?: 12
        runCatching {
            val localDate = LocalDate(y, m, d)
            localDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }.getOrNull()
    } else {
        kotlin.time.Clock.System.now().toEpochMilliseconds()
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedMillis
    )

    val tabs = listOf("Year", "Month", "Full Date")

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // Title
                Text(
                    text = "Select Date",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
                )

                // Pill-style segmented control
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        tabs.forEachIndexed { index, label ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable { selectedTab = index }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Content
                when (selectedTab) {
                    0 -> YearPicker(
                        selectedYear = selectedYear,
                        onYearSelected = { selectedYear = it },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    1 -> MonthYearPicker(
                        selectedYear = selectedYear,
                        selectedMonth = selectedMonth,
                        onYearChange = { selectedYear = it },
                        onMonthSelected = { selectedMonth = it },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    2 -> {
                        // Negative horizontal padding so the DatePicker uses full dialog width
                        Box(modifier = Modifier.fillMaxWidth()) {
                            DatePicker(
                                state = datePickerState,
                                showModeToggle = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onConfirm(null) }) { Text("Clear") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.btn_cancel)) }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        val result = when (selectedTab) {
                            0 -> "$selectedYear"
                            1 -> "$selectedYear-${selectedMonth.toString().padStart(2, '0')}"
                            2 -> datePickerState.selectedDateMillis?.let { millis ->
                                val localDate = Instant.fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.UTC).date
                                "${localDate.year}-${localDate.monthNumber.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
                            }
                            else -> null
                        }
                        onConfirm(result)
                    }) { Text(stringResource(Res.string.btn_ok)) }
                }
            }
        }
    }
}


@Composable
fun YearPicker(selectedYear: Int, onYearSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val currentYear = 2026
    val years = remember { (currentYear downTo 1970).toList() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.height(260.dp).fillMaxWidth(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(years) { y ->
            val isSelected = y == selectedYear
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable {
                        onYearSelected(y)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = y.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun MonthYearPicker(
    selectedYear: Int,
    selectedMonth: Int,
    onYearChange: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val months = remember {
        listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
    }
    
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            IconButton(onClick = { onYearChange(selectedYear - 1) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Year")
            }
            Text(
                text = selectedYear.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            IconButton(onClick = { onYearChange(selectedYear + 1) }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Year")
            }
        }
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(230.dp).fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(months) { index, m ->
                val monthNumber = index + 1
                val isSelected = monthNumber == selectedMonth
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onMonthSelected(monthNumber) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = m,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
