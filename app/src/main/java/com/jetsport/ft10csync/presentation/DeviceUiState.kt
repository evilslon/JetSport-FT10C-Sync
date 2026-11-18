package com.jetsport.ft10csync.presentation

data class DeviceUiRow(val address: String, val name: String, val rssi: Int, val selected: Boolean, val connected: Boolean)

data class DeviceUiState(
    val devices: List<DeviceUiRow> = emptyList(),
    val scanning: Boolean = false,
    val canConnect: Boolean = false,
    val connected: Boolean = false,
    val canSyncTime: Boolean = false,
    val canChooseDateTime: Boolean = false,
    val automaticSync: Boolean = true,
    val syncStatus: String = "",
    val selectedDateTime: String = "",
    val logText: String = "",
    val logVisible: Boolean = false
)
