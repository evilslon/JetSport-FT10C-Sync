package com.jetsport.ft10csync.ble

/**
 * Owns application/domain cleanup at FT-10C session boundaries.
 *
 * Dependencies are narrow cleanup operations supplied by AppCore. This coordinator
 * deliberately knows nothing about Activity, views, BluetoothGatt, or the transport
 * command channel. The current GATT owner still cancels transport writes and its
 * command-specific retry handles; this class resets shared application workflows.
 */
internal class Ft10cSessionLifecycleCoordinator(
    private val cancelSyncWorkflow: () -> Unit,
    private val cancelBatteryRetry: () -> Unit,
    private val clearPendingTimeWrite: () -> Unit,
    private val resetTimeSyncSession: () -> Unit,
    private val clearCommandExecution: () -> Unit,
    private val cancelHistorySession: () -> Unit,
    private val disconnectHistorySession: () -> Unit,
    private val cancelHistoryTimeout: () -> Unit
) {
    /** Called before a new GATT connection replaces the previous session. */
    fun onBeforeConnect() {
        cancelSyncWorkflow()
        cancelBatteryRetry()
        clearCommandExecution()
        // Preserve the existing replacement behavior: clear a pending write without
        // resetting the automatic-sync attempt until the next connected callback.
        clearPendingTimeWrite()
        cancelHistorySession()
        cancelHistoryTimeout()
    }

    /** Called for explicit, remote, failed, or otherwise terminal GATT disconnection. */
    fun onDisconnected() {
        cancelSyncWorkflow()
        cancelBatteryRetry()
        resetTimeSyncSession()
        clearCommandExecution()
        disconnectHistorySession()
        cancelHistoryTimeout()
    }
}
