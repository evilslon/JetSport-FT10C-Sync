package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import com.jetsport.ft10csync.sync.Ft10cHistoryCoordinator
import java.util.UUID

/**
 * Owns the B2 FA history-request command workflow independently of Activity UI.
 * The history stream parser/timeout remains in the application notification layer;
 * this class only schedules and submits the serialized GATT request.
 */
internal class Ft10cHistoryCommandCoordinator(
    private val handler: Handler,
    private val gattProvider: () -> BluetoothGatt?,
    private val isCurrentConnection: (BluetoothGatt) -> Boolean,
    private val hasBlePermissions: () -> Boolean,
    private val isBusy: () -> Boolean,
    private val historyCoordinator: Ft10cHistoryCoordinator,
    private val notificationCoordinator: Ft10cNotificationCoordinator,
    private val commandChannel: Ft10cGattCommandChannel,
    private val serviceUuid: UUID,
    private val characteristicUuid: UUID,
    private val setPendingWriteKind: (String?) -> Unit,
    private val onCommandCompleted: (String, Int, Boolean) -> Unit,
    private val logger: (String) -> Unit
) {
    private var deferredRetry: Runnable? = null

    fun request(trigger: String, force: Boolean = false, isDeferredRetry: Boolean = false) {
        val currentGatt = gattProvider()
        if (currentGatt == null || !isCurrentConnection(currentGatt) || !hasBlePermissions()) {
            logger("STEP HISTORY: no active connection or Bluetooth permission; trigger=$trigger")
            return
        }
        if (!isDeferredRetry && deferredRetry == null) {
            historyCoordinator.resetDeferredRetryBudget()
        }
        val now = System.currentTimeMillis()
        when (historyCoordinator.requestDecision(now, force)) {
            Ft10cHistoryCoordinator.RequestDecision.BLOCKED_UNTIL_RECONNECT -> {
                logger("STEP HISTORY: blocked after previous timeout; reconnect FT-10C before retry; trigger=$trigger")
                return
            }
            Ft10cHistoryCoordinator.RequestDecision.ALREADY_IN_FLIGHT -> {
                logger("STEP HISTORY: already in flight; skip trigger=$trigger")
                return
            }
            Ft10cHistoryCoordinator.RequestDecision.TOO_SOON -> {
                logger("STEP HISTORY: recently requested; skip trigger=$trigger")
                return
            }
            Ft10cHistoryCoordinator.RequestDecision.READY -> Unit
        }
        if (commandChannel.isBusy || isBusy()) {
            scheduleDeferredRetry(currentGatt, trigger, force)
            return
        }
        val characteristic = currentGatt.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
        if (characteristic == null || characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
            logger("STEP HISTORY: 0x33F1 unavailable or does not support WRITE")
            return
        }
        commandChannel.enqueue(
            name = "HISTORY",
            packet = byteArrayOf(0xB2.toByte(), 0xFA.toByte()),
            onStarted = { setPendingWriteKind("HISTORY") },
            onAccepted = {
                cancelPending()
                historyCoordinator.onRequestAccepted(now)
                notificationCoordinator.onHistoryRequestAccepted()
                logger("STEP HISTORY TX B2 FA accepted; trigger=$trigger")
            },
            onCompleted = { status, timedOut -> onCommandCompleted("HISTORY", status, timedOut) }
        )
    }

    fun cancelPending() {
        deferredRetry?.let(handler::removeCallbacks)
        deferredRetry = null
        historyCoordinator.resetDeferredRetryBudget()
    }

    private fun scheduleDeferredRetry(gatt: BluetoothGatt, trigger: String, force: Boolean) {
        if (deferredRetry != null) return
        if (!historyCoordinator.tryScheduleDeferredRetry()) {
            logger("STEP HISTORY: deferred retry budget exhausted; waiting for the next trigger")
            return
        }
        logger("STEP HISTORY: BLE command pending; defer trigger=$trigger")
        lateinit var retry: Runnable
        retry = Runnable {
            if (deferredRetry === retry) deferredRetry = null
            if (isCurrentConnection(gatt) && gattProvider() === gatt) {
                request(trigger, force, isDeferredRetry = true)
            }
        }
        deferredRetry = retry
        handler.postDelayed(retry, Ft10cHistoryCoordinator.DEFER_RETRY_MS)
    }
}
