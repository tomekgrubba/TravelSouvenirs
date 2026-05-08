package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.travelsouvenirs.main.ui.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun rememberMapTheme(): MapTheme {
    val vm: SettingsViewModel = koinViewModel()
    val theme by vm.mapTheme.collectAsState()
    return theme
}
