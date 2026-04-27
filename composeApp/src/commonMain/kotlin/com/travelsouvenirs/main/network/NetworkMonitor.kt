package com.travelsouvenirs.main.network

import kotlinx.coroutines.flow.StateFlow

interface NetworkMonitor {
    /** True when any network connection is available. */
    val isConnected: StateFlow<Boolean>
    /** True when the active connection is Wi-Fi (subset of isConnected). */
    val isWifi: StateFlow<Boolean>
}
