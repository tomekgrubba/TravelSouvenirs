package com.travelsouvenirs.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import com.russhwolf.settings.SharedPreferencesSettings
import com.travelsouvenirs.main.data.ItemRepository
import com.travelsouvenirs.main.data.buildItemDatabase
import com.travelsouvenirs.main.di.LocalImageStorage
import com.travelsouvenirs.main.di.LocalLocationService
import com.travelsouvenirs.main.di.LocalItemRepository
import com.travelsouvenirs.main.di.LocalSettings
import com.travelsouvenirs.main.image.AndroidImageStorage
import com.travelsouvenirs.main.location.AndroidLocationService
import com.travelsouvenirs.main.navigation.AppNavGraph
import com.travelsouvenirs.main.theme.AppTheme

/** App entry point — initialises platform services and provides them via CompositionLocal. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val db = buildItemDatabase()
        val repository = ItemRepository(db.itemDao())
        val locationService = AndroidLocationService(applicationContext)
        val imageStorage = AndroidImageStorage(applicationContext)
        val settings = SharedPreferencesSettings(
            getSharedPreferences("settings", MODE_PRIVATE)
        )

        setContent {
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
    }
}
