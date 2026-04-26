package com.travelsouvenirs.main.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.travelsouvenirs.main.di.LocalImageStorage
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.di.LocalMagnetRepository
import com.travelsouvenirs.main.di.LocalSettings
import com.travelsouvenirs.main.platform.PlatformMapLocationPicker
import com.travelsouvenirs.main.platform.rememberCameraCapture
import com.travelsouvenirs.main.platform.rememberLocationPermissionLauncher
import com.travelsouvenirs.main.platform.rememberPhotoPicker
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*

/** Form for creating or editing an item; shows "Edit Item" title when [magnetId] is non-null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMagnetScreen(onSaved: () -> Unit, magnetId: Long? = null) {
    val repository = LocalMagnetRepository.current
    val locationService = LocalLocationService.current
    val imageStorage = LocalImageStorage.current
    val settings = LocalSettings.current
    val viewModel: AddMagnetViewModel = viewModel(key = magnetId?.toString() ?: "add") {
        AddMagnetViewModel(repository, locationService, imageStorage, magnetId, settings)
    }

    val photoPath by viewModel.photoPath.collectAsState()
    val name by viewModel.name.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val dateAcquired by viewModel.dateAcquired.collectAsState()
    val placeName by viewModel.placeName.collectAsState()
    val category by viewModel.category.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val showLocationDialog by viewModel.showLocationDialog.collectAsState()

    LaunchedEffect(isSaved) {
        if (isSaved) onSaved()
    }

    val launchPhotoPicker = rememberPhotoPicker { path ->
        path?.let { viewModel.onPhotoSelected(it) }
    }
    val launchCamera = rememberCameraCapture { path ->
        path?.let { viewModel.onPhotoSelected(it) }
    }
    val requestLocationPermission = rememberLocationPermissionLauncher(
        onGranted = { viewModel.fetchCurrentLocation() }
    )

    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.onDateChange(LocalDate.fromEpochDays((millis / 86_400_000).toInt()))
                    }
                    showDatePicker = false
                }) { Text(stringResource(Res.string.btn_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.btn_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showLocationDialog) {
        LocationPickerDialog(
            viewModel = viewModel,
            onRequestGps = { requestLocationPermission() }
        )
    }

    val isFormValid = photoPath != null && name.isNotBlank() && placeName.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (magnetId != null) Res.string.title_edit_item else Res.string.title_add_item)) },
                navigationIcon = {
                    IconButton(onClick = onSaved) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.label_photo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                if (photoPath != null) {
                    AsyncImage(
                        model = photoPath,
                        contentDescription = stringResource(Res.string.cd_item_photo),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(Res.string.no_photo_selected),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { launchCamera() },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(Res.string.btn_take_photo)) }

                OutlinedButton(
                    onClick = { launchPhotoPicker() },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(Res.string.btn_gallery)) }
            }

            OutlinedButton(
                onClick = { viewModel.openLocationDialog() },
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
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(Res.string.label_notes)) },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = showCategoryDropdown,
                    onDismissRequest = { showCategoryDropdown = false }
                ) {
                    viewModel.availableCategories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                viewModel.onCategoryChange(option)
                                showCategoryDropdown = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            OutlinedTextField(
                value = "${dateAcquired.dayOfMonth} " +
                    "${dateAcquired.month.name.lowercase().replaceFirstChar { it.uppercase() }} " +
                    "${dateAcquired.year}",
                onValueChange = {},
                label = { Text("Date acquired") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.cd_pick_date))
                    }
                }
            )

            Button(
                onClick = viewModel::saveMagnet,
                enabled = isFormValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.btn_save_item))
            }
        }
    }
}

@Composable
private fun LocationPickerDialog(
    viewModel: AddMagnetViewModel,
    onRequestGps: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isLocating by viewModel.isLocating.collectAsState()
    val locationError by viewModel.locationError.collectAsState()
    val pendingLat by viewModel.pendingLat.collectAsState()
    val pendingLng by viewModel.pendingLng.collectAsState()
    val cameraMoveId by viewModel.cameraMoveId.collectAsState()

    Dialog(
        onDismissRequest = { viewModel.closeLocationDialog() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
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
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        label = { Text(stringResource(Res.string.label_city_or_place)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
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
                                .padding(8.dp)
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
                                    searchResults.isNotEmpty() -> LazyColumn {
                                        items(searchResults) { place ->
                                            ListItem(
                                                headlineContent = { Text(place.name) },
                                                modifier = Modifier.clickable { viewModel.onPlaceSelected(place) }
                                            )
                                            HorizontalDivider()
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

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.closeLocationDialog() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.btn_cancel))
                    }
                    Button(
                        onClick = { viewModel.confirmLocation() },
                        enabled = pendingLat != null && !isLocating,
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
