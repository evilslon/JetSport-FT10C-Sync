package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.jetsport.ft10csync.sync.Ft10cBatteryCoordinator
import java.util.UUID

/**
 * Owns submission of the FT-10C A2 battery command.
 * Refresh policy remains in Ft10cBatteryCoordinator; this class binds that policy
 * to the current GATT connection and the serialized command channel.
 */
internal class Ft10cBatteryCommandCoordinator(
    private val gattProvider: () -> BluetoothGatt?,
    private val isCurrentConnection: (BluetoothGatt) -> Boolean,
    private val hasBlePermissions: () -> Boolean,
    private val isBusy: () -> Boolean,
    private val batteryCoordinator: Ft10cBatteryCoordinator,
    private val commandChannel: Ft10cGattCommandChannel,
    private val serviceUuid: UUID,
    private val characteristicUuid: UUID,
    private val setPendingWriteKind: (String?) -> Unit,
    private val onCommandCompleted: (String, Int, Boolean) -> Unit,
    private val logger: (String) -> Unit
) {
    fun request(force: Boolean = false) {
        val currentGatt = gattProvider() ?: return
        batteryCoordinator.request(
            force = force,
            isConnected = {
                isCurrentConnection(currentGatt) &&
                    gattProvider() === currentGatt &&
                    hasBlePermissions()
            },
            isBusy = isBusy,
            onFreshSkip = { logger("BATTERY A2: skip; refreshed less than 30 minutes ago") },
            onDeferred = { /* The battery policy owns bounded retry scheduling. */ },
            onRetryExhausted = {
                logger("BATTERY A2: retry budget exhausted; waiting for the next scheduled or manual refresh")
            },
            onRequest = { enqueue(currentGatt) }
        )
    }

    private fun enqueue(gatt: BluetoothGatt) {
        if (!isCurrentConnection(gatt) || gattProvider() !== gatt || !hasBlePermissions()) return
        val characteristic = gatt.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            logger("BATTERY A2: write characteristic is unavailable")
            return
        }
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
            logger("BATTERY A2: write characteristic does not support WRITE")
            return
        }
        commandChannel.enqueue(
            name = "BATTERY",
            packet = byteArrayOf(0xA2.toByte()),
            onStarted = { setPendingWriteKind("BATTERY") },
            onAccepted = {
                logger("BATTERY A2 query: write accepted; refresh interval will start only after a valid A2 response")
            },
            onCompleted = { status, timedOut -> onCommandCompleted("BATTERY", status, timedOut) }
        )
    }
}
