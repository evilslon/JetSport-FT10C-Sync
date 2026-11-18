package com.jetsport.ft10csync.sync

import android.os.Handler

/**
 * Owns the FT-10C A2 battery refresh policy and its bounded retry scheduling.
 * BLE packet construction, command-queue writes, notification decoding and UI
 * updates remain in their existing layers.
 */
internal class Ft10cBatteryCoordinator(
    private val handler: Handler,
    private val lastResponseAt: () -> Long,
    private val saveResponseAt: (Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    private var retryTask: Runnable? = null
    private var retryAttempts = 0

    fun request(
        force: Boolean,
        isConnected: () -> Boolean,
        isBusy: () -> Boolean,
        onRequest: () -> Unit,
        onFreshSkip: () -> Unit,
        onDeferred: () -> Unit,
        onRetryExhausted: () -> Unit = {}
    ) {
        if (!force && !isRefreshDue(lastResponseAt(), now())) {
            clearRetry()
            onFreshSkip()
            return
        }
        if (!isConnected()) return

        if (isBusy()) {
            onDeferred()
            scheduleRetry(force, isConnected, isBusy, onRequest, onFreshSkip, onDeferred, onRetryExhausted)
            return
        }

        clearRetry()
        onRequest()
    }

    /** A valid A2 notification is the only event that advances the refresh timestamp. */
    fun onResponse() {
        saveResponseAt(now())
        clearRetry()
    }

    /** Prevent delayed work from crossing a GATT connection boundary. */
    fun onDisconnected() = clearRetry()

    private fun scheduleRetry(
        force: Boolean,
        isConnected: () -> Boolean,
        isBusy: () -> Boolean,
        onRequest: () -> Unit,
        onFreshSkip: () -> Unit,
        onDeferred: () -> Unit,
        onRetryExhausted: () -> Unit
    ) {
        if (retryAttempts >= MAX_RETRY_ATTEMPTS) {
            cancelRetryTask()
            onRetryExhausted()
            return
        }
        cancelRetryTask()
        retryAttempts++
        lateinit var task: Runnable
        task = Runnable {
            if (retryTask === task) retryTask = null
            request(force, isConnected, isBusy, onRequest, onFreshSkip, onDeferred, onRetryExhausted)
        }
        retryTask = task
        handler.postDelayed(task, RETRY_DELAY_MS)
    }

    private fun cancelRetryTask() {
        retryTask?.let(handler::removeCallbacks)
        retryTask = null
    }

    private fun clearRetry() {
        cancelRetryTask()
        retryAttempts = 0
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        const val RETRY_DELAY_MS = 5_000L
        const val MAX_RETRY_ATTEMPTS = 12

        fun isRefreshDue(lastResponseAt: Long, now: Long): Boolean =
            now - lastResponseAt >= REFRESH_INTERVAL_MS
    }
}
