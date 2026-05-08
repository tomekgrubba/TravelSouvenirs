package com.travelsouvenirs.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.travelsouvenirs.main.auth.AndroidGoogleSignInHelper
import com.travelsouvenirs.main.auth.GoogleSignInHelper
import com.travelsouvenirs.main.navigation.AppNavGraph
import com.travelsouvenirs.main.network.NetworkMonitor
import com.travelsouvenirs.main.sync.SyncCoordinator
import com.travelsouvenirs.main.theme.AppTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

class MainActivity : ComponentActivity() {

    private val networkMonitor: NetworkMonitor by inject()
    private val syncRepository: SyncCoordinator by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Register Activity-scoped GoogleSignInHelper so SignInViewModel can get it from Koin
        loadKoinModules(module { single<GoogleSignInHelper> { AndroidGoogleSignInHelper(this@MainActivity) } })

        observeAndSync()

        setContent {
            AppTheme {
                AppNavGraph(rememberNavController())
            }
        }
    }

    private fun observeAndSync() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                networkMonitor.isConnected.collect { connected ->
                    if (connected) syncRepository.sync()
                }
            }
        }
    }
}
