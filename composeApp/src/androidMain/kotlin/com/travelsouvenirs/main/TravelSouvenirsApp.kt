package com.travelsouvenirs.main

import android.app.Application

class TravelSouvenirsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: TravelSouvenirsApp
            private set
    }
}
