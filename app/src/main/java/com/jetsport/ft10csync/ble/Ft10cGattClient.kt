package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import java.util.UUID
import com.jetsport.ft10csync.device.BraceletDeviceProfile
import com.jetsport.ft10csync.device.Ft10cDeviceProfile

/**
 * Owns the FT-10C GATT connection lifecycle and notification subscription.
 * Packet interpretation and command scheduling deliberately remain in MainActivity.
 */
class Ft10cGattClient(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int)
        fun onServicesDiscovered(gatt: BluetoothGatt, status: Int)
        fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray)
        fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int)
        fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int)
    }

    @Volatile
    var gatt: BluetoothGatt? = null
        private set

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== g) {
                listenerLog("GATT callback from stale session ignored: device=${safeAddress(g)} connection status=$status state=$newState")
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Keep the object available to the listener during its callback; close afterwards.
                listener.onConnectionStateChange(g, status, newState)
                closeIfCurrent(g)
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && gatt === g) {
                listener.onConnectionStateChange(g, status, newState)
                try {
                    val started = g.discoverServices()
                    listenerLog("GATT discoverServices() вызван: accepted=$started")
                } catch (e: SecurityException) {
                    listenerLog("GATT discoverServices(): SecurityException: ${e.message}")
                } catch (e: Exception) {
                    listenerLog("GATT discoverServices(): ${e.javaClass.simpleName}: ${e.message}")
                }
                return
            }
            listener.onConnectionStateChange(g, status, newState)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (gatt === g) listener.onServicesDiscovered(g, status)
            else listenerLog("GATT callback from stale session ignored: device=${safeAddress(g)} services-discovered status=$status")
        }

        @Deprecated("Deprecated by Android 13; retained for API 23–32")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (gatt === g) listener.onCharacteristicChanged(g, characteristic, characteristic.value ?: byteArrayOf())
            else listenerLog("GATT callback from stale session ignored: device=${safeAddress(g)} notify=${characteristic.uuid}")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (gatt === g) listener.onCharacteristicChanged(g, characteristic, value.copyOf())
            else listenerLog("GATT callback from stale session ignored: device=${safeAddress(g)} notify=${characteristic.uuid}")
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (gatt === g) listener.onCharacteristicWrite(g, characteristic, status)
            else listenerLog("GATT callback from stale session ignored: device=${safeAddress(g)} write=${characteristic.uuid} status=$status")
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt === g) listener.onDescriptorWrite(g, descriptor, status)
            else listenerLog("GATT callback from stale session ignored: device=${safeAddress(g)} descriptor=${descriptor.uuid} status=$status")
        }
    }

    @Synchronized
    fun connect(device: BluetoothDevice) {
        val previous = gatt
        if (previous != null) {
            listenerLog("GATT replacing session: closing previous device=${safeAddress(previous)} before new target")
            close()
        }
        val address = try { device.address } catch (e: SecurityException) { "<address permission denied>" }
        listenerLog("GATT connectGatt(): target=$address; transport=LE; autoConnect=false; API=${Build.VERSION.SDK_INT}")
        try {
            val created = if (Build.VERSION.SDK_INT >= 23) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback)
            }
            gatt = created
            listenerLog(if (created != null) "GATT connectGatt(): returned BluetoothGatt instance" else "GATT connectGatt(): returned null")
        } catch (e: SecurityException) {
            gatt = null
            listenerLog("GATT connectGatt(): SecurityException: ${e.message}")
            throw e
        } catch (e: Exception) {
            gatt = null
            listenerLog("GATT connectGatt(): ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    /** Enables notifications for the supplied model profile using the Android-version-appropriate API. */
    fun enableNotifications(g: BluetoothGatt, profile: BraceletDeviceProfile): Boolean {
        val characteristic = g.getService(profile.serviceUuid)?.getCharacteristic(profile.notifyCharacteristicUuid)
        if (characteristic == null) {
            listenerLog("${profile.displayName}: notify characteristic ${profile.notifyCharacteristicUuid} not found; subscription skipped")
            return false
        }
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
            listenerLog("${profile.displayName}: ${profile.notifyCharacteristicUuid} does not declare NOTIFY; subscription skipped")
            return false
        }
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            listenerLog("${profile.displayName}: CCCD 0x2902 is missing")
            return false
        }
        return try {
            val localEnabled = g.setCharacteristicNotification(characteristic, true)
            listenerLog("${profile.notifyCharacteristicUuid}: локальное включение уведомлений=$localEnabled")
            if (!localEnabled) return false
            val result = if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                if (g.writeDescriptor(descriptor)) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
            }
            listenerLog("${profile.notifyCharacteristicUuid}: запрос записи CCCD 0x2902, результат=$result")
            result == BluetoothGatt.GATT_SUCCESS
        } catch (e: SecurityException) {
            listenerLog("${profile.notifyCharacteristicUuid}: нет разрешения для подписки: ${e.message}")
            false
        } catch (e: Exception) {
            listenerLog("${profile.notifyCharacteristicUuid}: ошибка включения уведомлений ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** Backward-compatible FT-10C entry point; protocol behavior is unchanged. */
    fun enableFt10cNotifications(g: BluetoothGatt): Boolean =
        enableNotifications(g, Ft10cDeviceProfile)

    @Synchronized
    fun disconnectAndClose() {
        val old = gatt
        if (old != null) listenerLog("GATT manual disconnect: device=${safeAddress(old)}")
        gatt = null
        try { old?.disconnect() } catch (_: SecurityException) { }
        try { old?.close() } catch (_: Exception) { }
    }

    @Synchronized
    fun close() {
        val old = gatt
        if (old != null) listenerLog("GATT close: device=${safeAddress(old)}")
        gatt = null
        try { old?.close() } catch (_: Exception) { }
    }

    private fun safeAddress(g: BluetoothGatt): String = try { g.device.address } catch (_: SecurityException) { "<address permission denied>" } catch (_: Exception) { "<address unavailable>" }

    private fun closeIfCurrent(g: BluetoothGatt) {
        synchronized(this) {
            if (gatt === g) {
                gatt = null
                try { g.close() } catch (_: Exception) { }
            }
        }
    }

    // Logging is injected as a tiny callback to avoid coupling this BLE component to Activity/UI.
    private var logSink: ((String) -> Unit)? = null
    fun setLogSink(sink: (String) -> Unit) { logSink = sink }
    private fun listenerLog(message: String) { logSink?.invoke(message) }

    companion object {
        val FT10C_SERVICE_UUID: UUID = Ft10cDeviceProfile.serviceUuid
        val FT10C_NOTIFY_UUID: UUID = Ft10cDeviceProfile.notifyCharacteristicUuid
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
