package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

/**
 * Owns the serialized FT-10C command write path for one active GATT session.
 *
 * The channel keeps packet scheduling, Android characteristic writes, and write
 * callback completion together. Application-specific completion reactions remain
 * injected by the owner, so this class does not depend on an Activity or Service.
 */
internal class Ft10cGattCommandChannel(
    private val gattProvider: () -> BluetoothGatt?,
    scheduler: Ft10cQueueScheduler,
    private val callbackHandler: Handler,
    private val serviceUuid: UUID,
    private val characteristicUuid: UUID,
    private val logger: (String) -> Unit,
    private val addressOf: (BluetoothGatt) -> String?
) {
    private val queue = Ft10cCommandQueue(
        scheduler = scheduler,
        writer = ::writePacket,
        logger = logger
    )

    val writeEventHandler = Ft10cGattWriteEventHandler(
        timeWriteCharacteristicUuid = characteristicUuid,
        onWriteCallback = { callbackGatt, status ->
            // Android may invoke BluetoothGattCallback off the main thread. Recheck
            // the GATT identity when the posted event is consumed: the connection
            // may have been replaced after the callback was first received.
            dispatchQueueAction {
                if (callbackGatt === gattProvider()) {
                    queue.onWriteCallback(status)
                } else {
                    logger("BLE QUEUE: ignored write callback queued by a stale GATT session")
                }
            }
        },
        logger = logger,
        addressOf = addressOf,
        isCurrentGatt = { callbackGatt -> callbackGatt === gattProvider() }
    )

    val isBusy: Boolean
        get() = queue.isBusy

    fun enqueue(
        name: String,
        packet: ByteArray,
        onStarted: () -> Unit = {},
        onAccepted: () -> Unit = {},
        onCompleted: (status: Int, timedOut: Boolean) -> Unit = { _, _ -> }
    ) {
        queue.enqueue(name, packet, onStarted, onAccepted, onCompleted)
    }

    fun resetForNewConnection() {
        dispatchQueueAction { queue.resetForNewConnection() }
    }

    fun cancelAll(reason: String) {
        dispatchQueueAction { queue.cancelAll(reason) }
    }

    private fun dispatchQueueAction(action: () -> Unit) {
        if (Looper.myLooper() === callbackHandler.looper) {
            action()
        } else {
            callbackHandler.post(action)
        }
    }

    private fun writePacket(packet: ByteArray): Int {
        val currentGatt = gattProvider() ?: return BluetoothGatt.GATT_FAILURE
        val characteristic = currentGatt.getService(serviceUuid)
            ?.getCharacteristic(characteristicUuid)
            ?: return BluetoothGatt.GATT_FAILURE
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
            return BluetoothGatt.GATT_FAILURE
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            currentGatt.writeCharacteristic(
                characteristic,
                packet.copyOf(),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            characteristic.setValue(packet.copyOf())
            @Suppress("DEPRECATION")
            if (currentGatt.writeCharacteristic(characteristic)) BluetoothGatt.GATT_SUCCESS
            else BluetoothGatt.GATT_FAILURE
        }
    }
}
