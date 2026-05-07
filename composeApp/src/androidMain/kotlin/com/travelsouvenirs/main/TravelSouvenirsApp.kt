package com.travelsouvenirs.main

import android.app.Application
import android.preference.PreferenceManager
import com.travelsouvenirs.main.di.authModule
import com.travelsouvenirs.main.di.dataModule
import com.travelsouvenirs.main.di.platformModule
import com.travelsouvenirs.main.di.syncModule
import com.travelsouvenirs.main.di.useCaseModule
import com.travelsouvenirs.main.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.osmdroid.config.Configuration

class TravelSouvenirsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        @Suppress("DEPRECATION")
        Configuration.getInstance().apply {
            load(this@TravelSouvenirsApp, PreferenceManager.getDefaultSharedPreferences(this@TravelSouvenirsApp))
            userAgentValue = packageName
        }
        startKoin {
            androidContext(this@TravelSouvenirsApp)
            modules(dataModule, syncModule, authModule, useCaseModule, viewModelModule, platformModule)
        }
    }

    companion object {
        lateinit var instance: TravelSouvenirsApp
            private set
    }
}
