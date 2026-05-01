package com.travelsouvenirs.main.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelsouvenirs.main.di.LocalAuthRepository
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalSettings
import com.travelsouvenirs.main.di.LocalSyncRepository
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.Res
import com.travelsouvenirs.main.platform.PlatformBackHandler
import com.travelsouvenirs.main.ui.auth.SignInScreen
import com.travelsouvenirs.main.platform.PlatformMapContent
import com.travelsouvenirs.main.ui.list.ListScreen
import com.travelsouvenirs.main.ui.settings.SettingsScreen
import com.travelsouvenirs.main.ui.shared.CategoryFilterViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.*

/** The two available content tabs (Settings is accessed via the app bar icon). */
enum class MainTab { MAP, LIST }

/** Root scaffold with app bar, Material 3 top tab row, FAB, and tab content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onAddClick: () -> Unit, onItemClick: (Long) -> Unit) {
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.MAP.name) }
    val selectedTab = MainTab.valueOf(selectedTabName)
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSignIn by rememberSaveable { mutableStateOf(false) }

    val settings = LocalSettings.current
    val repository = LocalItemRepository.current
    val authRepository = LocalAuthRepository.current
    val syncRepository = LocalSyncRepository.current
    val currentUser by authRepository.currentUser.collectAsState()
    val isSyncing by syncRepository.isSyncing.collectAsState()
    val isSyncingImages by syncRepository.isSyncingImages.collectAsState()
    val categoryFilterVM: CategoryFilterViewModel = viewModel { CategoryFilterViewModel(settings, repository) }

    // After login: sync DB (locks screen), then sync images (small indicator)
    LaunchedEffect(currentUser) {
        if (currentUser != null && showSignIn) {
            syncRepository.syncData()
            showSignIn = false
            syncRepository.syncImages()
        } else if (currentUser != null) {
            showSignIn = false
        }
    }

    // Swallow back presses while sync is blocking the screen
    PlatformBackHandler(enabled = showSettings || showSignIn || isSyncing) {
        when {
            isSyncing -> { /* block navigation during DB sync */ }
            showSignIn -> showSignIn = false
            else -> { showSettings = false; categoryFilterVM.refreshCategories() }
        }
    }

    CompositionLocalProvider(LocalCategoryFilter provides categoryFilterVM) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(Res.drawable.ic_app_logo),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(Res.string.app_name))
                        }
                    },
                    actions = {
                        if (isSyncingImages) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 4.dp).size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            enabled = !isSyncing && !showSignIn,
                            onClick = {
                                if (showSettings) {
                                    showSettings = false
                                    categoryFilterVM.refreshCategories()
                                } else {
                                    showSettings = true
                                }
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.cd_settings))
                        }
                    }
                )
                if (!showSettings) {
                    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                        Tab(
                            selected = selectedTab == MainTab.MAP,
                            enabled = !isSyncing,
                            onClick = {
                                if (showSettings) { showSettings = false; categoryFilterVM.refreshCategories() }
                                selectedTabName = MainTab.MAP.name
                            },
                            text = { Text(stringResource(Res.string.tab_map), style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)) },
                            icon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == MainTab.LIST,
                            enabled = !isSyncing,
                            onClick = {
                                if (showSettings) { showSettings = false; categoryFilterVM.refreshCategories() }
                                selectedTabName = MainTab.LIST.name
                            },
                            text = { Text(stringResource(Res.string.tab_list), style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)) },
                            icon = { Icon(Icons.Default.List, contentDescription = null) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!showSettings && !isSyncing) {
                ExtendedFloatingActionButton(onClick = onAddClick) {
                    Text(stringResource(Res.string.fab_add_item))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSignIn) {
                SignInScreen()
            } else if (showSettings) {
                SettingsScreen(onSignInClick = { showSignIn = true })
            } else {
                when (selectedTab) {
                    MainTab.MAP -> PlatformMapContent(onPinClick = onItemClick, onAddClick = onAddClick)
                    MainTab.LIST -> ListScreen(onItemClick = onItemClick, onAddClick = onAddClick)
                }
            }

            // Full-screen lock during DB sync — prevents all interaction
            if (isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
    } // CompositionLocalProvider
}
