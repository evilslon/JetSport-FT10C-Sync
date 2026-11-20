package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothDevice
import android.os.Handler

/** Transport operations required by the connection workflow. Implementations own GATT. */
interface Ft10cConnectionTransport {
    fun connect(device: BluetoothDevice)
    fun failAndClose(address: String, reason: String)
    fun disconnectAndClose()
}

/**
 * Owns connection deadlines and pending target state without depending on the concrete
 * GATT controller or an Activity. UI-facing timeout results are published as immutable
 * events; listener hooks are reserved for application/session cleanup only.
 */
internal class Ft10cConnectionCoordinator(
    private val handler: Handler,
    private val transport: Ft10cConnectionTransport,
    private val isConnected: (String) -> Boolean,
    private val logger: (String) -> Unit,
    private val events: BleUiEventDispatcher,
    private val listener: Listener,
    private val timeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS
) {
    interface Listener {
        /** Cancel work belonging to the previous connection before starting a new one. */
        fun onBeforeConnect()

        /** Cancel app-level work before an explicit user disconnect. */
        fun onBeforeDisconnect(reason: String)
    }

    @Volatile
    var pendingAddress: String? = null
        private set

    @Volatile
    private var discoveryAddress: String? = null

    private val connectTimeoutTask = Runnable {
        val address = pendingAddress ?: return@Runnable
        if (!isConnected(address)) {
            pendingAddress = null
            logger("GATT CONNECT TIMEOUT: no connection callback within ${timeoutMs / 1000} seconds; target=$address. Closing incomplete session")
            transport.failAndClose(address, "GATT connect timeout")
            events.dispatch(BleUiEvent.GattConnectTimedOut(address))
        }
    }

    private val discoveryTimeoutTask = Runnable {
        val address = discoveryAddress ?: return@Runnable
        discoveryAddress = null
        logger("GATT SERVICE DISCOVERY TIMEOUT: no services-discovered callback within ${SERVICE_DISCOVERY_TIMEOUT_MS / 1000} seconds; target=$address. Closing incomplete session")
        transport.failAndClose(address, "GATT service discovery timeout")
        events.dispatch(BleUiEvent.GattServiceDiscoveryTimedOut(address))
    }

    @Synchronized
    fun connect(device: BluetoothDevice, address: String) {
        listener.onBeforeConnect()
        pendingAddress = address
        logger("Connecting to $address…")
        try {
            handler.removeCallbacks(connectTimeoutTask)
            handler.removeCallbacks(discoveryTimeoutTask)
            discoveryAddress = null
            handler.postDelayed(connectTimeoutTask, timeoutMs)
            transport.connect(device)
        } catch (error: Exception) {
            pendingAddress = null
            handler.removeCallbacks(connectTimeoutTask)
            handler.removeCallbacks(discoveryTimeoutTask)
            discoveryAddress = null
            transport.failAndClose(
                address,
                if (error is SecurityException) "permission denied" else "connect start failed"
            )
            throw error
        }
    }

    /** Called as soon as Android reports STATE_CONNECTED. */
    @Synchronized
    fun onConnected() {
        val address = pendingAddress
        pendingAddress = null
        handler.removeCallbacks(connectTimeoutTask)
        if (address != null) {
            discoveryAddress = address
            handler.removeCallbacks(discoveryTimeoutTask)
            handler.postDelayed(discoveryTimeoutTask, SERVICE_DISCOVERY_TIMEOUT_MS)
        }
    }

    /** Called for every services-discovered callback, successful or not. */
    @Synchronized
    fun onServicesDiscovered(status: Int) {
        val address = discoveryAddress
        discoveryAddress = null
        handler.removeCallbacks(discoveryTimeoutTask)
        if (status != android.bluetooth.BluetoothGatt.GATT_SUCCESS && address != null) {
            logger("GATT SERVICE DISCOVERY FAILED: status=$status; target=$address. Closing session")
            transport.failAndClose(address, "GATT service discovery failed: status=$status")
            events.dispatch(BleUiEvent.GattServiceDiscoveryFailed(address, status))
        }
    }

    /** Called when Android reports STATE_DISCONNECTED, including remote disconnects. */
    @Synchronized
    fun onDisconnected() {
        pendingAddress = null
        discoveryAddress = null
        handler.removeCallbacks(connectTimeoutTask)
        handler.removeCallbacks(discoveryTimeoutTask)
    }

    @Synchronized
    fun disconnect(reason: String) {
        listener.onBeforeDisconnect(reason)
        pendingAddress = null
        discoveryAddress = null
        handler.removeCallbacks(connectTimeoutTask)
        handler.removeCallbacks(discoveryTimeoutTask)
        transport.disconnectAndClose()
    }

    /** Removes only coordinator-owned callbacks; transport ownership stays with its runtime. */
    @Synchronized
    fun cancelPendingWorkflow() {
        pendingAddress = null
        discoveryAddress = null
        handler.removeCallbacks(connectTimeoutTask)
        handler.removeCallbacks(discoveryTimeoutTask)
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L
        const val SERVICE_DISCOVERY_TIMEOUT_MS = 15_000L
    }
}
