package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothDevice
import android.os.Handler

/**
 * Owns the application-level connect/disconnect workflow around Ft10cDeviceManager.
 *
 * The coordinator handles the pending connection target, connection timeout,
 * and service-discovery deadline, and invokes narrowly-scoped hooks for app-specific
 * cleanup/UI reactions. GATT operations, service-layout inspection, protocol commands
 * and sync scheduling remain in their existing components.
 */
class Ft10cConnectionCoordinator(
    private val handler: Handler,
    private val deviceManager: Ft10cDeviceManager,
    private val isConnected: (String) -> Boolean,
    private val logger: (String) -> Unit,
    private val listener: Listener,
    private val timeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS
) {
    interface Listener {
        /** Cancel work belonging to the previous connection before starting a new one. */
        fun onBeforeConnect()

        /** App/UI reaction when the GATT connection did not arrive before the timeout. */
        fun onConnectTimeout(address: String)

        /** GATT connected, but Android did not complete service discovery in time. */
        fun onServiceDiscoveryTimeout(address: String)

        /** Android completed service discovery with a failure status. */
        fun onServiceDiscoveryFailed(address: String, status: Int)

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
            logger("GATT CONNECT TIMEOUT: нет callback подключения за ${timeoutMs / 1000} секунд; target=$address. Закрываю незавершённую сессию")
            deviceManager.failAndClose(address, "GATT connect timeout")
            listener.onConnectTimeout(address)
        }
    }

    private val discoveryTimeoutTask = Runnable {
        val address = discoveryAddress ?: return@Runnable
        discoveryAddress = null
        logger("GATT SERVICE DISCOVERY TIMEOUT: нет callback services-discovered за ${SERVICE_DISCOVERY_TIMEOUT_MS / 1000} секунд; target=$address. Закрываю незавершённую сессию")
        deviceManager.failAndClose(address, "GATT service discovery timeout")
        listener.onServiceDiscoveryTimeout(address)
    }

    @Synchronized
    fun connect(device: BluetoothDevice, address: String) {
        listener.onBeforeConnect()
        pendingAddress = address
        logger("Подключение к $address…")
        try {
            handler.removeCallbacks(connectTimeoutTask)
            handler.removeCallbacks(discoveryTimeoutTask)
            discoveryAddress = null
            handler.postDelayed(connectTimeoutTask, timeoutMs)
            deviceManager.connect(device)
        } catch (error: Exception) {
            pendingAddress = null
            handler.removeCallbacks(connectTimeoutTask)
            handler.removeCallbacks(discoveryTimeoutTask)
            discoveryAddress = null
            deviceManager.failAndClose(
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
            deviceManager.failAndClose(address, "GATT service discovery failed: status=$status")
            listener.onServiceDiscoveryFailed(address, status)
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
        deviceManager.disconnectAndClose()
    }

    /** Removes only coordinator-owned callbacks; GATT ownership stays with the caller. */
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
