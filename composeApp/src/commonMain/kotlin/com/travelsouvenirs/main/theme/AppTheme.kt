package com.travelsouvenirs.main.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun AppTheme(style: AppStyle = AppStyle.DEFAULT, content: @Composable () -> Unit) {
    val colorScheme = when (style) {
        AppStyle.COSMIC -> CosmicColorScheme
        AppStyle.EMBER  -> EmberColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
