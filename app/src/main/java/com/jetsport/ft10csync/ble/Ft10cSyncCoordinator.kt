package com.jetsport.ft10csync.ble

import android.os.Handler

/**
 * Coordinates the verified post-connect and foreground synchronization schedule.
 *
 * This class owns delays and the per-connection automatic-time-sync guard only.
 * It does not build packets, access GATT characteristics, enqueue commands,
 * decode notifications, or persist measurements. Those operations remain in
 * their existing, independently tested components.
 */
internal class Ft10cSyncCoordinator(
    private val handler: Handler
) {
    private var profileTask: Runnable? = null
    private var timeTask: Runnable? = null
    private var historyTask: Runnable? = null
    private var batteryTask: Runnable? = null
    private var foregroundTask: Runnable? = null

    fun onDisconnected() {
        cancelConnectionWorkflow()
        foregroundTask?.let(handler::removeCallbacks)
        foregroundTask = null
    }

    fun scheduleConnectionWorkflow(
        isCurrentConnection: () -> Boolean,
        automaticTimeSyncEnabled: () -> Boolean,
        automaticTimeSyncAlreadyAttempted: () -> Boolean,
        onProfile: () -> Unit,
        onAutomaticTimeSync: () -> Unit,
        onHistory: () -> Unit,
        onBattery: () -> Unit
    ) {
        cancelConnectionWorkflow()

        profileTask = Runnable {
            if (isCurrentConnection()) onProfile()
        }.also { handler.postDelayed(it, PROFILE_DELAY_MS) }

        if (automaticTimeSyncEnabled()) {
            timeTask = Runnable {
                if (automaticTimeSyncEnabled() &&
                    !automaticTimeSyncAlreadyAttempted() &&
                    isCurrentConnection()
                ) {
                    onAutomaticTimeSync()
                }
            }.also { handler.postDelayed(it, TIME_SYNC_DELAY_MS) }
        }

        historyTask = Runnable {
            if (isCurrentConnection()) onHistory()
        }.also { handler.postDelayed(it, HISTORY_DELAY_MS) }

        batteryTask = Runnable {
            if (isCurrentConnection()) onBattery()
        }.also { handler.postDelayed(it, BATTERY_DELAY_MS) }
    }

    fun scheduleForegroundRefresh(
        isConnected: () -> Boolean,
        onHistory: () -> Unit,
        onBattery: () -> Unit
    ) {
        foregroundTask?.let(handler::removeCallbacks)
        foregroundTask = Runnable {
            if (isConnected()) {
                onHistory()
                onBattery()
            }
        }.also { handler.postDelayed(it, FOREGROUND_REFRESH_DELAY_MS) }
    }

    /** Cancels the one-shot refresh scheduled for the current foreground visit. */
    fun cancelForegroundRefresh() {
        foregroundTask?.let(handler::removeCallbacks)
        foregroundTask = null
    }

    fun cancelConnectionWorkflow() {
        profileTask?.let(handler::removeCallbacks)
        timeTask?.let(handler::removeCallbacks)
        historyTask?.let(handler::removeCallbacks)
        batteryTask?.let(handler::removeCallbacks)
        profileTask = null
        timeTask = null
        historyTask = null
        batteryTask = null
    }

    companion object {
        private const val PROFILE_DELAY_MS = 1_800L
        private const val TIME_SYNC_DELAY_MS = 4_000L
        private const val HISTORY_DELAY_MS = 6_500L
        private const val BATTERY_DELAY_MS = 10_000L
        private const val FOREGROUND_REFRESH_DELAY_MS = 700L
    }
}
