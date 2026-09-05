package com.travelsouvenirs.main

import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController
import com.travelsouvenirs.main.di.authModule
import com.travelsouvenirs.main.di.dataModule
import com.travelsouvenirs.main.di.platformModule
import com.travelsouvenirs.main.di.syncModule
import com.travelsouvenirs.main.di.useCaseModule
import com.travelsouvenirs.main.di.viewModelModule
import com.travelsouvenirs.main.navigation.AppNavGraph
import com.travelsouvenirs.main.sync.SyncCoordinator
import com.travelsouvenirs.main.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

// Captured at initKoin() time so appDidBecomeActive() can trigger syncs without
// needing a GlobalContext lookup (not available in Koin 4.x on iOS).
private var syncCoordinator: SyncCoordinator? = null

fun initKoin() {
    val koin = startKoin {
        modules(dataModule, syncModule, authModule, useCaseModule, viewModelModule, platformModule)
    }.koin
    syncCoordinator = koin.get()
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    AppTheme {
        AppNavGraph(rememberNavController())
    }
}

/**
 * Called by Swift from UIApplication.didBecomeActiveNotification to trigger a sync
 * whenever the app comes to the foreground. This is more reliable than
 * LifecycleEventEffect(ON_RESUME) on iOS.
 */
fun appDidBecomeActive() {
    val coordinator = syncCoordinator ?: return
    CoroutineScope(Dispatchers.Default).launch {
        coordinator.syncData()
        launch { coordinator.syncImages() }
    }
}
