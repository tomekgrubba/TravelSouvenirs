package com.travelsouvenirs.main

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController
import com.russhwolf.settings.NSUserDefaultsSettings
import com.travelsouvenirs.main.auth.FirebaseAuthRepository
import com.travelsouvenirs.main.auth.IosGoogleSignInHelper
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.data.buildItemDatabase
import com.travelsouvenirs.main.di.LocalAuthRepository
import com.travelsouvenirs.main.di.LocalGoogleSignInHelper
import com.travelsouvenirs.main.di.LocalImageStorage
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.di.LocalNetworkMonitor
import com.travelsouvenirs.main.di.LocalSettings
import com.travelsouvenirs.main.di.LocalSyncRepository
import com.travelsouvenirs.main.image.IosImageStorage
import com.travelsouvenirs.main.location.IosLocationService
import com.travelsouvenirs.main.navigation.AppNavGraph
import com.travelsouvenirs.main.network.IosNetworkMonitor
import com.travelsouvenirs.main.sync.ImageSyncHelper
import com.travelsouvenirs.main.sync.SyncRepository
import com.travelsouvenirs.main.theme.AppTheme
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import platform.UIKit.UIViewController

private val db by lazy { buildItemDatabase() }
private val repository by lazy { ItemRepository(db.itemDao()) }
private val locationService by lazy { IosLocationService() }
private val imageStorage by lazy { IosImageStorage() }
private val settings by lazy { NSUserDefaultsSettings.Factory().create(null) }
private val networkMonitor by lazy { IosNetworkMonitor() }
private val authRepository by lazy { FirebaseAuthRepository() }
private val googleSignInHelper by lazy { IosGoogleSignInHelper() }
private val imageSyncHelper by lazy { ImageSyncHelper(Firebase.storage) }
private val syncRepository by lazy {
    SyncRepository(
        dao = db.itemDao(),
        firestore = Firebase.firestore,
        imageSyncHelper = imageSyncHelper,
        authRepository = authRepository,
        settings = settings,
        imageStorage = imageStorage,
    )
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    AppTheme {
        CompositionLocalProvider(
            LocalItemRepository provides repository,
            LocalLocationService provides locationService,
            LocalImageStorage provides imageStorage,
            LocalSettings provides settings,
            LocalAuthRepository provides authRepository,
            LocalSyncRepository provides syncRepository,
            LocalNetworkMonitor provides networkMonitor,
            LocalGoogleSignInHelper provides googleSignInHelper,
        ) {
            AppNavGraph(rememberNavController())
        }
    }
}
