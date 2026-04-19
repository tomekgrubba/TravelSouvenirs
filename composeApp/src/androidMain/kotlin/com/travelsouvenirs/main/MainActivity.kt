package com.travelsouvenirs.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.travelsouvenirs.main.navigation.AppNavGraph
import com.travelsouvenirs.main.theme.AppTheme

/** App entry point — bootstraps the Compose navigation host inside the app theme. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppNavGraph(rememberNavController())
            }
        }
    }
}
