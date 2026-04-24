package com.travelsouvenirs.main.di

import androidx.compose.runtime.compositionLocalOf
import com.russhwolf.settings.Settings
import com.travelsouvenirs.main.data.MagnetRepository
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.location.LocationService
import com.travelsouvenirs.main.ui.shared.CategoryFilterViewModel

val LocalMagnetRepository = compositionLocalOf<MagnetRepository> {
    error("LocalMagnetRepository not provided")
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
