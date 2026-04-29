package com.travelsouvenirs.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.russhwolf.settings.SharedPreferencesSettings
import com.travelsouvenirs.main.auth.AndroidGoogleSignInHelper
import com.travelsouvenirs.main.auth.FirebaseAuthRepository
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
import com.travelsouvenirs.main.image.AndroidImageStorage
import com.travelsouvenirs.main.location.AndroidLocationService
import com.travelsouvenirs.main.navigation.AppNavGraph
import com.travelsouvenirs.main.network.AndroidNetworkMonitor
import com.travelsouvenirs.main.sync.ImageSyncHelper
import com.travelsouvenirs.main.sync.SyncRepository
import com.travelsouvenirs.main.platform.rememberAppStyle
import com.travelsouvenirs.main.theme.AppTheme
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
        val networkMonitor = AndroidNetworkMonitor(applicationContext)
        val authRepository = FirebaseAuthRepository()
        val googleSignInHelper = AndroidGoogleSignInHelper(this)
        val imageSyncHelper = ImageSyncHelper(Firebase.storage)
        val syncRepository = SyncRepository(
            dao = db.itemDao(),
            firestore = Firebase.firestore,
            imageSyncHelper = imageSyncHelper,
            authRepository = authRepository,
            settings = settings,
            imageStorage = imageStorage,
        )

        observeAndSync(networkMonitor, syncRepository, settings)

        setContent {
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
                val appStyle = rememberAppStyle()
                AppTheme(style = appStyle) {
                    AppNavGraph(rememberNavController())
                }
            }
        }
    }

    private fun observeAndSync(
        networkMonitor: AndroidNetworkMonitor,
        syncRepository: SyncRepository,
        settings: SharedPreferencesSettings,
    ) {
        // Trigger sync whenever the Activity is visible AND network conditions are met.
        // combine emits immediately with current values, so the first sync fires on foreground.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(networkMonitor.isConnected, networkMonitor.isWifi) { connected, wifi ->
                    connected to wifi
                }.collect { (connected, wifi) ->
                    val wifiOnly = settings.getBoolean("wifi_only_sync", false)
                    if (connected && (!wifiOnly || wifi)) {
                        syncRepository.sync()
                    }
                }
            }
        }
    }
}
