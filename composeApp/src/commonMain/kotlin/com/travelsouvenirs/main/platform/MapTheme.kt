package com.travelsouvenirs.main.platform

enum class MapTheme {
    LIGHT, DARK;

    companion object {
        val DEFAULT = LIGHT
        fun fromString(value: String?) = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
