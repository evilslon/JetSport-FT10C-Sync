package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothGatt
import android.os.Handler
import java.util.ArrayDeque

interface Ft10cQueueScheduler {
    fun postDelayed(task: Runnable, delayMs: Long)
    fun removeCallbacks(task: Runnable)
}

internal class HandlerFt10cQueueScheduler(
    private val handler: Handler
) : Ft10cQueueScheduler {
    override fun postDelayed(task: Runnable, delayMs: Long) {
        handler.postDelayed(task, delayMs)
    }

    override fun removeCallbacks(task: Runnable) {
        handler.removeCallbacks(task)
    }
}

/**
 * Serializes writes to the FT-10C command characteristic.
 * A command remains active until Android reports onCharacteristicWrite or the
 * bounded callback timeout expires. Protocol notifications are handled elsewhere.
 */
class Ft10cCommandQueue(
    private val scheduler: Ft10cQueueScheduler,
    private val writer: (ByteArray) -> Int,
    private val logger: (String) -> Unit,
    private val callbackTimeoutMs: Long = 5_000L
) {
    private data class Command(
        val name: String,
        val packet: ByteArray,
        val onStarted: () -> Unit,
        val onAccepted: () -> Unit,
        val onCompleted: (status: Int, timedOut: Boolean) -> Unit
    )

    private val pending = ArrayDeque<Command>()
    private var active: Command? = null
    private var blockedUntilReconnect = false
    private val callbackTimeout = Runnable {
        val command = active ?: return@Runnable
        logger("BLE QUEUE: ${command.name} callback timeout after ${callbackTimeoutMs}ms")
        finish(BluetoothGatt.GATT_FAILURE, timedOut = true)
    }

    val isBusy: Boolean get() = blockedUntilReconnect || active != null || pending.isNotEmpty()

    /** Re-arm the queue only after a new GATT connection has been established. */
    fun resetForNewConnection() {
        scheduler.removeCallbacks(callbackTimeout)
        active = null
        pending.clear()
        blockedUntilReconnect = false
        logger("BLE QUEUE: reset for new GATT connection")
    }

    fun enqueue(
        name: String,
        packet: ByteArray,
        onStarted: () -> Unit = {},
        onAccepted: () -> Unit = {},
        onCompleted: (status: Int, timedOut: Boolean) -> Unit = { _, _ -> }
    ) {
        if (blockedUntilReconnect) {
            logger("BLE QUEUE: rejected $name; queue is blocked until reconnect after a write timeout")
            onCompleted(BluetoothGatt.GATT_FAILURE, true)
            return
        }
        pending.addLast(Command(name, packet.copyOf(), onStarted, onAccepted, onCompleted))
        logger("BLE QUEUE: queued $name (${packet.size} bytes); waiting=${pending.size}")
        dispatchNext()
    }

    fun onWriteCallback(status: Int) {
        if (active == null) {
            logger("BLE QUEUE: unexpected write callback status=$status; no active command")
            return
        }
        finish(status, timedOut = false)
    }

    fun cancelAll(reason: String) {
        val dropped = pending.size + if (active != null) 1 else 0
        scheduler.removeCallbacks(callbackTimeout)
        active = null
        pending.clear()
        blockedUntilReconnect = false
        if (dropped > 0) logger("BLE QUEUE: cancelled $dropped command(s): $reason")
    }

    private fun dispatchNext() {
        if (active != null) return
        val command = pending.pollFirst() ?: return
        active = command
        try {
            command.onStarted()
        } catch (error: Exception) {
            logger("BLE QUEUE: ${command.name} start handler failed: ${error.javaClass.simpleName}: ${error.message}")
            finish(BluetoothGatt.GATT_FAILURE, timedOut = false)
            return
        }

        val result = try {
            writer(command.packet.copyOf())
        } catch (error: SecurityException) {
            logger("BLE QUEUE: ${command.name} SecurityException while writing: ${error.message}")
            BluetoothGatt.GATT_FAILURE
        } catch (error: Exception) {
            logger("BLE QUEUE: ${command.name} write failed: ${error.javaClass.simpleName}: ${error.message}")
            BluetoothGatt.GATT_FAILURE
        }

        if (result != BluetoothGatt.GATT_SUCCESS) {
            logger("BLE QUEUE: ${command.name} rejected immediately, status=$result")
            finish(result, timedOut = false)
            return
        }

        // Arm the callback deadline before invoking application-side observers.
        // An observer failure must never release the active slot: Android may
        // still deliver onCharacteristicWrite for the accepted GATT operation.
        scheduler.removeCallbacks(callbackTimeout)
        scheduler.postDelayed(callbackTimeout, callbackTimeoutMs)
        logger("BLE QUEUE: ${command.name} accepted by Android; awaiting write callback")
        try {
            command.onAccepted()
        } catch (error: Exception) {
            logger("BLE QUEUE: ${command.name} accepted-handler failed: ${error.javaClass.simpleName}: ${error.message}; still waiting for write callback")
        }
    }

    private fun finish(status: Int, timedOut: Boolean) {
        val completed = active ?: return
        scheduler.removeCallbacks(callbackTimeout)
        active = null
        if (timedOut) {
            blockedUntilReconnect = true
            val dropped = pending.size
            pending.clear()
            if (dropped > 0) logger("BLE QUEUE: dropped $dropped queued command(s) after timeout; reconnect required")
        }
        try {
            completed.onCompleted(status, timedOut)
        } catch (error: Exception) {
            logger("BLE QUEUE: completion handler for ${completed.name} failed: ${error.message}")
        }
        if (!blockedUntilReconnect) dispatchNext()
    }
}
