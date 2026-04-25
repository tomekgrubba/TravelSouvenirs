package com.travelsouvenirs.main

import android.app.Application
import android.preference.PreferenceManager
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
    }

    companion object {
        lateinit var instance: TravelSouvenirsApp
            private set
    }
}
