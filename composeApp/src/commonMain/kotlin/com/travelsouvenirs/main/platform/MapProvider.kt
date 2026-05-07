package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.travelsouvenirs.main.theme.AppStyle
import com.travelsouvenirs.main.ui.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun rememberMapProvider(): MapProviderType {
    val vm: SettingsViewModel = koinViewModel()
    val provider by vm.mapProvider.collectAsState()
    return provider
}

@Composable
fun rememberMapTheme(): MapTheme {
    val vm: SettingsViewModel = koinViewModel()
    val theme by vm.mapTheme.collectAsState()
    return theme
}

@Composable
fun rememberAppStyle(): AppStyle {
    val vm: SettingsViewModel = koinViewModel()
    val style by vm.appStyle.collectAsState()
    return style
}
