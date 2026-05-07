package com.travelsouvenirs.main.di

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.travelsouvenirs.main.image.AndroidImageStorage
import com.travelsouvenirs.main.image.ImageStorage
import com.travelsouvenirs.main.location.AndroidLocationService
import com.travelsouvenirs.main.location.LocationService
import com.travelsouvenirs.main.network.AndroidNetworkMonitor
import com.travelsouvenirs.main.network.NetworkMonitor
import com.travelsouvenirs.main.util.AppSettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val platformModule = module {
    single<AppSettings> {
        AppSettings(
            SharedPreferencesSettings(
                androidContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            )
        )
    }
    single<LocationService> { AndroidLocationService(androidContext()) }
    single<ImageStorage> { AndroidImageStorage(androidContext()) }
    single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }
}
