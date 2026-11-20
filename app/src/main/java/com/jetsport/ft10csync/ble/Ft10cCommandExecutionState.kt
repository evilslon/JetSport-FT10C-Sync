package com.jetsport.ft10csync.ble

/**
 * Application-scoped bookkeeping for the command currently awaiting its GATT write
 * callback. This state belongs to the BLE command domain, not to an Activity screen.
 * All mutations are synchronized because platform callbacks may arrive off-main-thread.
 */
internal class Ft10cCommandExecutionState {
    @Volatile
    private var activeCommand: String? = null

    val pendingWriteKind: String?
        get() = activeCommand

    val isBusy: Boolean
        get() = activeCommand != null

    @Synchronized
    fun markStarted(command: String?) {
        activeCommand = command
    }

    @Synchronized
    fun clearIfMatches(command: String): Boolean {
        if (activeCommand != command) return false
        activeCommand = null
        return true
    }

    @Synchronized
    fun clear() {
        activeCommand = null
    }
}
