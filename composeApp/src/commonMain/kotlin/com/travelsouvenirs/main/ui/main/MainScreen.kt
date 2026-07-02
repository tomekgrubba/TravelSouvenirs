package com.travelsouvenirs.main.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.di.LocalAppViewModel
import com.travelsouvenirs.main.di.LocalCategoryFilter
import com.travelsouvenirs.main.platform.PlatformBackHandler
import com.travelsouvenirs.main.platform.PlatformMapContent
import com.travelsouvenirs.main.sync.SyncCoordinator
import com.travelsouvenirs.main.ui.list.ListScreen
import com.travelsouvenirs.main.ui.shared.CategoryFilterViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.currentKoinScope
import travelsouvenirs.composeapp.generated.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.travelsouvenirs.main.data.ItemRepository

/** The two available content tabs. Settings and SignIn are separate NavGraph destinations. */
enum class MainTab { MAP, LIST }

/** Root scaffold with app bar, Material 3 top tab row, FAB, and tab content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onAddClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onSignInClick: () -> Unit,
) {
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.MAP.name) }
    val selectedTab = MainTab.valueOf(selectedTabName)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val appViewModel = LocalAppViewModel.current
    val authRepository: AuthRepository = koinInject()
    val syncRepository: SyncCoordinator = koinInject()

    LaunchedEffect(Unit) {
        appViewModel.snackbarMessage.collect { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }
    LaunchedEffect(Unit) {
        syncRepository.errors.collect { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.LIST) {
            appViewModel.clearTargetCameraLocation()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            syncRepository.syncData()
            launch { syncRepository.syncImages() }
        }
    }

    val currentUser by authRepository.currentUser.collectAsState()
    val isSyncing by syncRepository.isSyncing.collectAsState()
    val isSyncingImages by syncRepository.isSyncingImages.collectAsState()
    val koinScope = currentKoinScope()
    val categoryFilterVM: CategoryFilterViewModel = viewModel { koinScope.get<CategoryFilterViewModel>() }
    val isPolaroid = true

    val itemRepository: ItemRepository = koinInject()
    val allItems by itemRepository.allItems.collectAsState(initial = emptyList())
    val showOnboarding = currentUser == null && allItems.isEmpty()

    // Block back navigation during metadata sync
    PlatformBackHandler(enabled = isSyncing) { /* block */ }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val isTablet = maxWidth >= 600.dp
    CompositionLocalProvider(LocalCategoryFilter provides categoryFilterVM) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = if (isPolaroid) TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) else TopAppBarDefaults.topAppBarColors(),
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
                        if (isSyncing || isSyncingImages) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 4.dp).size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        IconButton(
                            enabled = !isSyncing,
                            onClick = onSettingsClick
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.cd_settings))
                        }
                    }
                )
                if (!showOnboarding) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = if (isPolaroid)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            TabRowDefaults.primaryContainerColor,
                        contentColor = if (isPolaroid)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            TabRowDefaults.primaryContentColor,
                        indicator = {
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(selectedTab.ordinal),
                                color = if (isPolaroid) MaterialTheme.colorScheme.onPrimary else LocalContentColor.current
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == MainTab.MAP,
                            enabled = !isSyncing,
                            onClick = { selectedTabName = MainTab.MAP.name },
                            text = { Text(stringResource(Res.string.tab_map), style = MaterialTheme.typography.labelLarge.copy(fontSize = if (isTablet) 21.sp else 16.sp)) },
                            icon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(if (isTablet) 32.dp else 24.dp)) }
                        )
                        Tab(
                            selected = selectedTab == MainTab.LIST,
                            enabled = !isSyncing,
                            onClick = { selectedTabName = MainTab.LIST.name },
                            text = { Text(stringResource(Res.string.tab_list), style = MaterialTheme.typography.labelLarge.copy(fontSize = if (isTablet) 21.sp else 16.sp)) },
                            icon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(if (isTablet) 32.dp else 24.dp)) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!showOnboarding) {
                ExtendedFloatingActionButton(
                    modifier = if (isTablet) Modifier.height(64.dp) else Modifier,
                    onClick = onAddClick,
                ) {
                    Text(
                        stringResource(Res.string.fab_add_item),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = if (isTablet) 18.sp else 16.sp),
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showOnboarding) {
                // Background map (inactive)
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    PlatformMapContent(onPinClick = {}, onAddClick = {})
                }

                // Translucent touch blocker
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .rotate(-1.5f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.ic_app_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp).clip(CircleShape)
                                )
                            }

                            Text(
                                text = stringResource(Res.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "Create a new item or login to your account to get started with your travel memories collection.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = onAddClick,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Create a new item")
                                }
                            }

                            OutlinedButton(
                                onClick = onSignInClick,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Login to your account")
                                }
                            }
                        }
                    }
                }
            } else {
                when (selectedTab) {
                    MainTab.MAP -> PlatformMapContent(onPinClick = onItemClick, onAddClick = onAddClick)
                    MainTab.LIST -> ListScreen(onItemClick = onItemClick, onAddClick = onAddClick)
                }
            }
        }
    }
    } // CompositionLocalProvider
    } // BoxWithConstraints
}
