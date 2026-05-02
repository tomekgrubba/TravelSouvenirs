package com.travelsouvenirs.main.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
class IosNetworkMonitor : NetworkMonitor {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isWifi = MutableStateFlow(false)
    override val isWifi: StateFlow<Boolean> = _isWifi.asStateFlow()

    init {
        val monitor = nw_path_monitor_create()
        val queue = dispatch_get_main_queue()
        nw_path_monitor_set_update_handler(monitor) { path ->
            val satisfied = nw_path_get_status(path) == nw_path_status_satisfied
            _isConnected.value = satisfied
            _isWifi.value = satisfied && nw_path_uses_interface_type(path, nw_interface_type_wifi)
        }
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }
}
