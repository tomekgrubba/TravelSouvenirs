package com.travelsouvenirs.main.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.travelsouvenirs.main.di.LocalMagnetRepository
import com.travelsouvenirs.main.di.LocalSettings
import com.travelsouvenirs.main.ui.settings.SettingsViewModel

@Composable
fun rememberMapProvider(): MapProviderType {
    val settings = LocalSettings.current
    val repository = LocalMagnetRepository.current
    val vm: SettingsViewModel = viewModel { SettingsViewModel(settings, repository) }
    val provider by vm.mapProvider.collectAsState()
    return provider
}
