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
import com.travelsouvenirs.main.platform.rememberAppStyle
import com.travelsouvenirs.main.theme.AppTheme
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

fun initKoin() {
    startKoin {
        modules(dataModule, syncModule, authModule, useCaseModule, viewModelModule, platformModule)
    }
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    val appStyle = rememberAppStyle()
    AppTheme(style = appStyle) {
        AppNavGraph(rememberNavController())
    }
}
