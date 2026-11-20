package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

/**
 * Routes platform GATT callbacks to the existing application-level handlers.
 *
 * Keeping this adapter outside MainActivity removes the raw BluetoothGattCallback
 * dispatch wiring from the Activity. The handlers retain their existing protocol
 * and workflow responsibilities; this class does not initiate GATT operations.
 */
internal class Ft10cGattCallbackRouter(
    private val sessionEvents: Ft10cGattSessionEventHandler,
    private val writeEvents: Ft10cGattWriteEventHandler,
    private val onServicesDiscovered: (Int) -> Unit
) : Ft10cBleSessionController.Listener {

    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: Int,
        newState: Int
    ) {
        sessionEvents.onConnectionStateChange(gatt, status, newState)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        sessionEvents.onServicesDiscovered(gatt, status)
        onServicesDiscovered(status)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        sessionEvents.onNotification(gatt, characteristic, value)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        writeEvents.onCharacteristicWrite(gatt, characteristic, status)
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        sessionEvents.onDescriptorWrite(gatt, descriptor, status)
        writeEvents.onDescriptorWrite(gatt, descriptor, status)
    }
}
