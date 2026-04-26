package com.travelsouvenirs.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController
import com.russhwolf.settings.NSUserDefaultsSettings
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.data.buildItemDatabase
import com.travelsouvenirs.main.di.LocalImageStorage
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalSettings
import com.travelsouvenirs.main.image.IosImageStorage
import com.travelsouvenirs.main.location.IosLocationService
import com.travelsouvenirs.main.navigation.AppNavGraph
import com.travelsouvenirs.main.theme.AppTheme
import platform.UIKit.UIViewController

private val db by lazy { buildItemDatabase() }
private val repository by lazy { ItemRepository(db.itemDao()) }
private val locationService by lazy { IosLocationService() }
private val imageStorage by lazy { IosImageStorage() }
private val settings by lazy { NSUserDefaultsSettings.Factory().create(null) }

/** iOS entry point — creates all platform implementations and wires up the Compose UI. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    AppTheme {
        CompositionLocalProvider(
            LocalItemRepository provides repository,
            LocalLocationService provides locationService,
            LocalImageStorage provides imageStorage,
            LocalSettings provides settings
        ) {
            AppNavGraph(rememberNavController())
        }
    }
}
