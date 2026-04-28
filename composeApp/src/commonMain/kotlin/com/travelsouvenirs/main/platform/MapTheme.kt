package com.travelsouvenirs.main.platform

enum class MapTheme {
    LIGHT, DARK;

    companion object {
        const val SETTINGS_KEY = "map_theme"
        val DEFAULT = LIGHT
        fun fromString(value: String?) = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
