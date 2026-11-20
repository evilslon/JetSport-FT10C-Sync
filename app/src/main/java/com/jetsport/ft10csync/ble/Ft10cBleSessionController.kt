package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import com.jetsport.ft10csync.device.BraceletDeviceProfile

/**
 * Owns one bracelet GATT session and its connection-state model.
 *
 * This class is the application-facing owner of the GATT client and connection-state
 * model. Packet parsing, command scheduling, and protocol policy remain separate.
 */
class Ft10cBleSessionController(
    context: Context,
    private val listener: Listener,
    val connectionState: BleConnectionStateStore
) : Ft10cConnectionTransport {
    interface Listener {
        fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int)
        fun onServicesDiscovered(gatt: BluetoothGatt, status: Int)
        fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        )
        fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int)
        fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int)
    }


    private val gattClient = Ft10cGattClient(context, object : Ft10cGattClient.Listener {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = safeAddress(gatt)
            when (newState) {
                android.bluetooth.BluetoothProfile.STATE_CONNECTED -> connectionState.discovering(address)
                android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> connectionState.disconnected()
            }
            listener.onConnectionStateChange(gatt, status, newState)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) =
            listener.onServicesDiscovered(gatt, status)

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) = listener.onCharacteristicChanged(gatt, characteristic, value)

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) = listener.onCharacteristicWrite(gatt, characteristic, status)

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) = listener.onDescriptorWrite(gatt, descriptor, status)
    })

    val gatt: BluetoothGatt?
        get() = gattClient.gatt

    @Synchronized
    override fun connect(device: BluetoothDevice) {
        val address = try { device.address } catch (_: SecurityException) { "<address permission denied>" }
        connectionState.connecting(address)
        try {
            gattClient.connect(device)
        } catch (e: Exception) {
            connectionState.fail(address, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    fun markUnknown(address: String) = connectionState.connectedUnknown(address)

    fun markReady(address: String, modelId: String) = connectionState.ready(address, modelId)

    fun enableNotifications(gatt: BluetoothGatt, profile: BraceletDeviceProfile): Boolean =
        gattClient.enableNotifications(gatt, profile)

    /** Closes an unfinished/failed session without overwriting its Failed state. */
    @Synchronized
    override fun failAndClose(address: String, reason: String) {
        connectionState.fail(address, reason)
        gattClient.close()
    }

    @Synchronized
    fun close() = gattClient.close()

    @Synchronized
    override fun disconnectAndClose() {
        val address = when (val current = connectionState.state) {
            is BleConnectionState.Connecting -> current.address
            is BleConnectionState.Discovering -> current.address
            is BleConnectionState.ConnectedUnknown -> current.address
            is BleConnectionState.Ready -> current.address
            is BleConnectionState.Disconnecting -> current.address
            is BleConnectionState.Failed -> current.address
            BleConnectionState.Disconnected -> null
        }
        if (address != null) connectionState.disconnecting(address)
        gattClient.disconnectAndClose()
        connectionState.disconnected()
    }

    fun setLogSink(sink: (String) -> Unit) = gattClient.setLogSink(sink)

    private fun safeAddress(gatt: BluetoothGatt): String = try {
        gatt.device.address
    } catch (_: SecurityException) {
        "<address permission denied>"
    } catch (_: Exception) {
        "<address unavailable>"
    }
}
