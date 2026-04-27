package com.travelsouvenirs.main.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelsouvenirs.main.di.LocalAuthRepository
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalSettings
import com.travelsouvenirs.main.di.LocalSyncRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import travelsouvenirs.composeapp.generated.resources.Res
import travelsouvenirs.composeapp.generated.resources.syncing_data
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
    val categoryFilterVM: CategoryFilterViewModel = viewModel { CategoryFilterViewModel(settings, repository) }

    // Show loading after login until the first sync for this session completes
    val startedSignedIn = remember { currentUser != null }
    var initialSyncDone by remember { mutableStateOf(startedSignedIn) }
    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null && !initialSyncDone) {
            withTimeoutOrNull(5_000L) {
                syncRepository.isSyncing.first { it }
                syncRepository.isSyncing.first { !it }
            }
            initialSyncDone = true
        }
    }

    // Auto-close sign-in screen when the user successfully logs in
    LaunchedEffect(currentUser) {
        if (currentUser != null) showSignIn = false
    }

    PlatformBackHandler(enabled = showSettings || showSignIn) {
        if (showSignIn) {
            showSignIn = false
        } else {
            showSettings = false
            categoryFilterVM.refreshCategories()
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
                        IconButton(onClick = {
                            if (showSettings) {
                                showSettings = false
                                categoryFilterVM.refreshCategories()
                            } else {
                                showSettings = true
                            }
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.cd_settings))
                        }
                    }
                )
                if (!showSettings) {
                    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                        Tab(
                            selected = selectedTab == MainTab.MAP,
                            onClick = {
                                if (showSettings) { showSettings = false; categoryFilterVM.refreshCategories() }
                                selectedTabName = MainTab.MAP.name
                            },
                            text = { Text(stringResource(Res.string.tab_map)) },
                            icon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == MainTab.LIST,
                            onClick = {
                                if (showSettings) { showSettings = false; categoryFilterVM.refreshCategories() }
                                selectedTabName = MainTab.LIST.name
                            },
                            text = { Text(stringResource(Res.string.tab_list)) },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!showSettings) {
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
            if (!initialSyncDone) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.syncing_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (showSignIn) {
                SignInScreen()
            } else if (showSettings) {
                SettingsScreen(onSignInClick = { showSignIn = true })
            } else {
                when (selectedTab) {
                    MainTab.MAP -> PlatformMapContent(onPinClick = onItemClick, onAddClick = onAddClick)
                    MainTab.LIST -> ListScreen(onItemClick = onItemClick, onAddClick = onAddClick)
                }
            }
        }
    }
    } // CompositionLocalProvider
}
