package com.travelsouvenirs.main.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Place
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.travelsouvenirs.main.di.LocalImageStorage
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.platform.PlatformMapPreview
import com.travelsouvenirs.main.util.formatDisplay
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*

/** Shows a single item's photo, metadata, and map preview; supports fullscreen photo, edit, and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val repository = LocalItemRepository.current
    val imageStorage = LocalImageStorage.current
    val viewModel: ItemDetailViewModel = viewModel(key = itemId.toString()) {
        ItemDetailViewModel(repository, itemId, imageStorage)
    }
    val item by viewModel.item.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFullscreenPhoto by remember { mutableStateOf(false) }

    if (showFullscreenPhoto) {
        item?.let { m ->
            Dialog(
                onDismissRequest = { showFullscreenPhoto = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { showFullscreenPhoto = false },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = m.photoPath,
                        contentDescription = stringResource(Res.string.cd_item_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.dialog_delete_item_title)) },
            text = { Text(stringResource(Res.string.dialog_delete_item_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteItem(onBack)
                }) { Text(stringResource(Res.string.btn_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(Res.string.btn_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.cd_edit))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.cd_delete))
                    }
                }
            )
        }
    ) { padding ->
        item?.let { m ->
            val hasLocation = m.latitude != 0.0 || m.longitude != 0.0
            val minMapHeight = 180.dp

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                val maxContentHeight = if (hasLocation) maxHeight - minMapHeight else maxHeight
                val mapMaxHeight = maxWidth - 32.dp

                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = maxContentHeight)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .padding(horizontal = 16.dp)
                        ) {
                            AsyncImage(
                                model = m.photoPath,
                                contentDescription = stringResource(Res.string.cd_item_photo),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showFullscreenPhoto = true },
                                contentScale = ContentScale.Crop
                            )
                            if (m.category.isNotBlank()) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Label,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            m.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (m.placeName.isNotBlank()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Place,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(m.placeName, style = MaterialTheme.typography.titleMedium)
                                    }
                                }

                                Text(
                                    m.dateAcquired.formatDisplay(),
                                    modifier = Modifier.padding(start = 32.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (m.notes.isNotBlank()) {
                                    Text(m.notes, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    if (hasLocation) {
                        PlatformMapPreview(
                            latitude = m.latitude,
                            longitude = m.longitude,
                            label = m.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(min = minMapHeight, max = mapMaxHeight)
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clipToBounds()
                        )
                    }
                }
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
