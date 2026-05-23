package com.travelsouvenirs.main.util

import com.russhwolf.settings.Settings

/** Single source of truth for all SharedPreferences keys and typed accessors. */
class AppSettings(private val settings: Settings) {

    var viewModeName: String
        get() = settings.getString(KEY_VIEW_MODE, "LIST")
        set(value) { settings.putString(KEY_VIEW_MODE, value) }

    var wifiOnlySync: Boolean
        get() = settings.getBoolean(KEY_WIFI_ONLY_SYNC, false)
        set(value) { settings.putBoolean(KEY_WIFI_ONLY_SYNC, value) }

    var notes: String
        get() = settings.getStringOrNull(KEY_NOTES) ?: ""
        set(value) { settings.putString(KEY_NOTES, value) }

    var categoriesUpdatedAt: Long
        get() = settings.getLong(KEY_CATEGORIES_UPDATED_AT, 0L)
        set(value) { settings.putLong(KEY_CATEGORIES_UPDATED_AT, value) }

    var lastSyncMillis: Long
        get() = settings.getLong(KEY_LAST_SYNC_MILLIS, 0L)
        set(value) { settings.putLong(KEY_LAST_SYNC_MILLIS, value) }

    fun clear() {
        settings.clear()
    }

    companion object {
        private const val KEY_VIEW_MODE = "list_view_mode"
        private const val KEY_WIFI_ONLY_SYNC = "wifi_only_sync"
        private const val KEY_NOTES = "notes"
        private const val KEY_CATEGORIES_UPDATED_AT = "categories_updated_at"
        private const val KEY_LAST_SYNC_MILLIS = "last_sync_millis"
    }
}
