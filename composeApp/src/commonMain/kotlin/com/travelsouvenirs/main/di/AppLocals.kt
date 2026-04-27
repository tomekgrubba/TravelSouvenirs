package com.travelsouvenirs.main.di

import androidx.compose.runtime.compositionLocalOf
import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.auth.GoogleSignInHelper
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.location.LocationService
import com.travelsouvenirs.main.network.NetworkMonitor
import com.travelsouvenirs.main.sync.SyncRepository
import com.travelsouvenirs.main.ui.shared.CategoryFilterViewModel

val LocalItemRepository = compositionLocalOf<ItemRepository> {
    error("LocalItemRepository not provided")
}
val LocalLocationService = compositionLocalOf<LocationService> {
    error("LocalLocationService not provided")
}
val LocalImageStorage = compositionLocalOf<ImageStorage> {
    error("LocalImageStorage not provided")
}
val LocalSettings = compositionLocalOf<Settings> {
    error("LocalSettings not provided")
}
val LocalCategoryFilter = compositionLocalOf<CategoryFilterViewModel> {
    error("LocalCategoryFilter not provided")
}
val LocalAuthRepository = compositionLocalOf<AuthRepository> {
    error("LocalAuthRepository not provided")
}
val LocalSyncRepository = compositionLocalOf<SyncRepository> {
    error("LocalSyncRepository not provided")
}
val LocalNetworkMonitor = compositionLocalOf<NetworkMonitor> {
    error("LocalNetworkMonitor not provided")
}
val LocalGoogleSignInHelper = compositionLocalOf<GoogleSignInHelper> {
    error("LocalGoogleSignInHelper not provided")
}
