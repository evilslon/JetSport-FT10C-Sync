package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import java.util.UUID

/**
 * Handles application-level write and CCCD callback reporting.
 *
 * This component deliberately does not initiate GATT operations. It forwards the
 * verified characteristic-write completion to the existing command queue and
 * leaves all protocol scheduling and packet semantics unchanged.
 */
internal class Ft10cGattWriteEventHandler(
    private val timeWriteCharacteristicUuid: UUID,
    private val isCommandQueueInitialized: () -> Boolean,
    private val onWriteCallback: (Int) -> Unit,
    private val logger: (String) -> Unit,
    private val addressOf: (BluetoothGatt) -> String?,
    private val isCurrentGatt: (BluetoothGatt) -> Boolean
) {
    fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        logger("GATT callback device=${addressOf(gatt)}: WRITE ${characteristic.uuid}: status=$status")
        if (characteristic.uuid != timeWriteCharacteristicUuid) return
        if (!isCurrentGatt(gatt)) {
            logger("GATT callback ignored: write belongs to a stale GATT session")
            return
        }
        if (isCommandQueueInitialized()) {
            onWriteCallback(status)
        }
    }

    fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        logger("GATT callback device=${addressOf(gatt)}: DESCRIPTOR ${descriptor.uuid}: status=$status")
    }
}
