package com.travelsouvenirs.main.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.travelsouvenirs.main.auth.GoogleSignInHelper
import com.travelsouvenirs.main.auth.IosGoogleSignInHelper
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.image.IosImageStorage
import com.travelsouvenirs.main.location.IosLocationService
import com.travelsouvenirs.main.location.LocationService
import com.travelsouvenirs.main.network.IosNetworkMonitor
import com.travelsouvenirs.main.network.NetworkMonitor
import com.travelsouvenirs.main.util.AppSettings
import org.koin.dsl.module

val platformModule = module {
    single<AppSettings> { AppSettings(NSUserDefaultsSettings.Factory().create(null)) }
    single<LocationService> { IosLocationService() }
    single<ImageStorage> { IosImageStorage() }
    single<NetworkMonitor> { IosNetworkMonitor() }
    single<GoogleSignInHelper> { IosGoogleSignInHelper() }
}
