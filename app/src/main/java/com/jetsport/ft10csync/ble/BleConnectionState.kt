package com.jetsport.ft10csync.ble

/**
 * Immutable snapshot of the GATT connection lifecycle.
 * Scanning is owned separately by Ft10cScanCoordinator because scanning and an existing
 * GATT session may overlap on some Android devices.
 */
sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    data class Connecting(val address: String) : BleConnectionState()
    data class Discovering(val address: String) : BleConnectionState()
    data class ConnectedUnknown(val address: String) : BleConnectionState()
    data class Ready(val address: String, val modelId: String) : BleConnectionState()
    data class Disconnecting(val address: String) : BleConnectionState()
    data class Failed(val address: String, val reason: String) : BleConnectionState()
}

/**
 * Small lifecycle store extracted from MainActivity. It intentionally contains no
 * Android Bluetooth objects and does not perform GATT operations.
 */
class BleConnectionStateStore {
    @Volatile
    var state: BleConnectionState = BleConnectionState.Disconnected
        private set

    /** Mirrors the former MainActivity.connectedAddress semantics. */
    val connectedAddress: String?
        get() = when (val current = state) {
            is BleConnectionState.Discovering -> current.address
            is BleConnectionState.ConnectedUnknown -> current.address
            is BleConnectionState.Ready -> current.address
            else -> null
        }

    @Synchronized
    fun connecting(address: String) {
        state = BleConnectionState.Connecting(address)
    }

    @Synchronized
    fun discovering(address: String) {
        state = BleConnectionState.Discovering(address)
    }

    @Synchronized
    fun connectedUnknown(address: String) {
        state = BleConnectionState.ConnectedUnknown(address)
    }

    @Synchronized
    fun ready(address: String, modelId: String) {
        state = BleConnectionState.Ready(address, modelId)
    }

    @Synchronized
    fun disconnecting(address: String) {
        state = BleConnectionState.Disconnecting(address)
    }

    @Synchronized
    fun disconnected() {
        state = BleConnectionState.Disconnected
    }

    @Synchronized
    fun fail(address: String, reason: String) {
        state = BleConnectionState.Failed(address, reason)
    }
}
