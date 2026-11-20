package com.jetsport.ft10csync.ble

/**
 * Consolidates transport-level cancellation at GATT session boundaries.
 * Ft10cBleSessionRuntime owns this policy and invokes it for reconnect, remote
 * disconnect, unsupported profiles, and terminal runtime shutdown.
 */
internal class Ft10cTransportLifecycleCoordinator(
    private val cancelProfileRetry: () -> Unit,
    private val cancelTimeRetry: () -> Unit,
    private val cancelHistoryRetry: () -> Unit,
    private val cancelGattCommands: (String) -> Unit,
    private val cancelConnectionTimeouts: () -> Unit,
    private val closeGatt: () -> Unit
) {
    /** Cancel command-specific work before the current GATT session is replaced. */
    fun onBeforeConnect() = cancelPendingCommands("replacing GATT connection")

    /** Cancel command-specific work before an explicit disconnect is initiated. */
    fun onBeforeDisconnect(reason: String) = cancelPendingCommands("manual disconnect: $reason")

    /** A remote or failed disconnect invalidates every retry tied to the old GATT. */
    fun onGattDisconnected() = cancelPendingCommands("GATT disconnected")

    /** Rejecting an unsupported profile terminates the just-opened GATT session. */
    fun onUnsupportedDevice() {
        cancelPendingCommands("unsupported GATT profile")
        cancelConnectionTimeouts()
        closeGatt()
    }

    /** Final transport teardown when the application-scoped runtime is closed. */
    fun onRuntimeClose(reason: String) {
        cancelPendingCommands(reason)
        cancelConnectionTimeouts()
        closeGatt()
    }

    private fun cancelPendingCommands(reason: String) {
        cancelProfileRetry()
        cancelTimeRetry()
        cancelHistoryRetry()
        cancelGattCommands(reason)
    }
}
