package com.travelsouvenirs.main.ui.main

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.travelsouvenirs.main.platform.PlatformBackHandler
import com.travelsouvenirs.main.platform.PlatformMapContent
import com.travelsouvenirs.main.ui.list.ListScreen
import com.travelsouvenirs.main.ui.settings.SettingsScreen
import org.jetbrains.compose.resources.painterResource
import travelsouvenirs.composeapp.generated.resources.Res
import travelsouvenirs.composeapp.generated.resources.ic_app_logo

/** The two available content tabs (Settings is accessed via the app bar icon). */
enum class MainTab { MAP, LIST }

/** Root scaffold with app bar, Material 3 top tab row, FAB, and tab content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onAddClick: () -> Unit, onItemClick: (Long) -> Unit) {
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.MAP.name) }
    val selectedTab = MainTab.valueOf(selectedTabName)
    var showSettings by rememberSaveable { mutableStateOf(false) }

    PlatformBackHandler(enabled = showSettings) { showSettings = false }

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
                            Text("Travel Souvenirs")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
                if (!showSettings) {
                    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                        Tab(
                            selected = selectedTab == MainTab.MAP,
                            onClick = { selectedTabName = MainTab.MAP.name },
                            text = { Text("Map") },
                            icon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == MainTab.LIST,
                            onClick = { selectedTabName = MainTab.LIST.name },
                            text = { Text("List") },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!showSettings) {
                ExtendedFloatingActionButton(onClick = onAddClick) {
                    Text("Add item")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSettings) {
                SettingsScreen()
            } else {
                when (selectedTab) {
                    MainTab.MAP -> PlatformMapContent(onPinClick = onItemClick)
                    MainTab.LIST -> ListScreen(onItemClick = onItemClick)
                }
            }
        }
    }
}
