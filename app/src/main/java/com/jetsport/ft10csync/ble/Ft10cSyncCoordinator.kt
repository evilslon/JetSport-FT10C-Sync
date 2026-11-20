package com.jetsport.ft10csync.ble

import android.os.Handler
import com.jetsport.ft10csync.sync.Ft10cTimeSyncIntervals

/**
 * Coordinates post-connect, periodic time, and foreground synchronization schedules.
 *
 * This class owns delays and scheduling only; it does not execute BLE operations.
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
    private var periodicTimeTask: Runnable? = null

    fun onDisconnected() {
        cancelConnectionWorkflow()
        cancelPeriodicTimeSync()
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


    /** Schedules exactly one next time sync; completion of that write schedules the following one. */
    fun schedulePeriodicTimeSync(
        intervalMinutes: Int,
        isConnected: () -> Boolean,
        isEnabled: () -> Boolean,
        onTimeSync: () -> Unit
    ) {
        cancelPeriodicTimeSync()
        if (intervalMinutes !in Ft10cTimeSyncIntervals.SUPPORTED_MINUTES || !isConnected() || !isEnabled()) return
        val task = Runnable {
            periodicTimeTask = null
            if (isConnected() && isEnabled()) onTimeSync()
        }
        periodicTimeTask = task
        handler.postDelayed(task, Ft10cTimeSyncIntervals.delayMillis(intervalMinutes))
    }

    fun cancelPeriodicTimeSync() {
        periodicTimeTask?.let(handler::removeCallbacks)
        periodicTimeTask = null
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
