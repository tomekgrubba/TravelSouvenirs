package com.travelsouvenirs.main.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.travelsouvenirs.main.data.MagnetDatabase
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.image.ImageStorageHelper
import com.yalantis.ucrop.UCrop
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMagnetScreen(onSaved: () -> Unit) {
    val context = LocalContext.current
    val repository = remember {
        MagnetRepository(MagnetDatabase.getDatabase(context).magnetDao())
    }
    val viewModel: AddMagnetViewModel = viewModel(
        factory = AddMagnetViewModel.Factory(repository, context)
    )

    val photoPath by viewModel.photoPath.collectAsState()
    val name by viewModel.name.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val dateAcquired by viewModel.dateAcquired.collectAsState()
    val placeName by viewModel.placeName.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val showLocationDialog by viewModel.showLocationDialog.collectAsState()

    LaunchedEffect(isSaved) {
        if (isSaved) onSaved()
    }

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                UCrop.getOutput(data)?.let { viewModel.onPhotoSelected(it, context) }
            }
        }
    }

    fun launchCrop(sourceUri: Uri) {
        val (destUri, _) = ImageStorageHelper.createCameraOutputUri(context)
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setToolbarColor(0xFF1C1C1E.toInt())
            setStatusBarColor(0xFF1C1C1E.toInt())
            setToolbarWidgetColor(android.graphics.Color.WHITE)
            setActiveControlsWidgetColor(0xFFBBBBBB.toInt())
            setDimmedLayerColor(0xCC000000.toInt())
        }
        val intent = UCrop.of(sourceUri, destUri)
            .withOptions(options)
            .getIntent(context)
            .setClass(context, CropActivity::class.java)
        cropLauncher.launch(intent)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraOutputUri?.let { launchCrop(it) }
        cameraOutputUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, _) = ImageStorageHelper.createCameraOutputUri(context)
            cameraOutputUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { launchCrop(it) } }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.fetchCurrentLocation()
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val (uri, _) = ImageStorageHelper.createCameraOutputUri(context)
            cameraOutputUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.fetchCurrentLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showLocationDialog) {
        LocationPickerDialog(
            viewModel = viewModel,
            onRequestGps = { requestLocation() }
        )
    }

    val isFormValid = photoPath != null && name.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Item") },
                navigationIcon = {
                    IconButton(onClick = onSaved) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (photoPath != null) {
                AsyncImage(
                    model = photoPath,
                    contentDescription = "Item photo",
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
                        "No photo selected",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { launchCamera() },
                    modifier = Modifier.weight(1f)
                ) { Text("Take Photo") }

                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Gallery") }
            }

            OutlinedTextField(
                value = name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

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
                        Icon(Icons.Default.DateRange, contentDescription = "Pick date")
                    }
                }
            )

            // Location picker button
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
                    if (placeName.isBlank()) "Set location…" else placeName,
                    maxLines = 1
                )
            }

            Button(
                onClick = viewModel::saveMagnet,
                enabled = isFormValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Item")
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

    Dialog(onDismissRequest = { viewModel.closeLocationDialog() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Set Location", style = MaterialTheme.typography.titleLarge)

                Spacer(modifier = Modifier.height(16.dp))

                // GPS button
                Button(
                    onClick = onRequestGps,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLocating
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Getting location…")
                    } else {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Use Current Location")
                    }
                }

                locationError?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text("or search", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    label = { Text("City or place name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 240.dp)) {
                    when {
                        isSearching -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center)
                            )
                        }
                        searchResults.isNotEmpty() -> {
                            LazyColumn {
                                items(searchResults) { place ->
                                    ListItem(
                                        headlineContent = { Text(place.name) },
                                        modifier = Modifier.clickable {
                                            viewModel.onPlaceSelected(place)
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                        searchQuery.length >= 2 -> {
                            Text(
                                "No results found",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { viewModel.closeLocationDialog() },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Cancel") }
            }
        }
    }
}
