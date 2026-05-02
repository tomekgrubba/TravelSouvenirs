package com.travelsouvenirs.main.theme

enum class AppStyle {
    COSMIC, GATEWAY, EMBER;

    companion object {
        const val SETTINGS_KEY = "app_style"
        val DEFAULT = COSMIC
        fun fromString(value: String?) = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
