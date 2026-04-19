package com.travelsouvenirs.main.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import com.travelsouvenirs.main.ui.list.ListScreen
import com.travelsouvenirs.main.ui.map.MapContent

enum class MainTab { MAP, LIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onAddClick: () -> Unit, onItemClick: (Long) -> Unit) {
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.MAP.name) }
    val selectedTab = MainTab.valueOf(selectedTabName)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("My Magnets") })
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    Tab(
                        selected = selectedTab == MainTab.MAP,
                        onClick = { selectedTabName = MainTab.MAP.name },
                        text = { Text("Map") },
                        icon = { Icon(Icons.Default.Map, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == MainTab.LIST,
                        onClick = { selectedTabName = MainTab.LIST.name },
                        text = { Text("List") },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddClick) {
                Text("Add new magnet")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                MainTab.MAP -> MapContent(onPinClick = onItemClick)
                MainTab.LIST -> ListScreen(onItemClick = onItemClick)
            }
        }
    }
}
