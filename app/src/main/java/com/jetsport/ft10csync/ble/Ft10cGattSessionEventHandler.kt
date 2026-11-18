package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Handler
import android.os.Looper
import android.bluetooth.BluetoothProfile
import com.jetsport.ft10csync.device.BraceletDeviceProfile
import com.jetsport.ft10csync.device.DeviceProfileRegistry
import java.util.UUID

/**
 * Coordinates application-level GATT session events.
 *
 * It owns callback routing and GATT layout inspection only. BluetoothGatt
 * operations remain in Ft10cGattClient/Ft10cDeviceManager; protocol decoding,
 * command queueing and persistence remain in their existing components.
 */
internal class Ft10cGattSessionEventHandler(
    private val profileRegistry: DeviceProfileRegistry,
    private val timeServiceUuid: UUID,
    private val timeWriteCharacteristicUuid: UUID,
    private val addressOf: (BluetoothGatt) -> String,
    private val statusName: (Int) -> String,
    private val stateName: (Int) -> String,
    private val properties: (Int) -> String,
    private val logger: (String) -> Unit,
    private val markUnknown: (String) -> Unit,
    private val markReady: (String, String) -> Unit,
    private val enableNotifications: (BluetoothGatt, BraceletDeviceProfile) -> Boolean,
    private val onConnected: (BluetoothGatt) -> Unit,
    private val onDisconnected: (BluetoothGatt) -> Unit,
    private val onUnknownProfile: (BluetoothGatt) -> Unit,
    private val onMatchedProfile: (BluetoothGatt, BraceletDeviceProfile, Boolean, Boolean) -> Unit,
    private val onNotification: (UUID, ByteArray) -> Unit
) {
    private data class PendingSubscription(
        val profile: BraceletDeviceProfile,
        val timeCharacteristicFound: Boolean,
        val timeWriteAvailable: Boolean,
        val timeout: Runnable
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingSubscriptions = mutableMapOf<BluetoothGatt, PendingSubscription>()
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        logger("GATT callback device=${addressOf(gatt)}: connection status=$status (${statusName(status)}) state=$newState (${stateName(newState)})")
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                clearPendingSubscription(gatt, "connection reset")
                onConnected(gatt)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                clearPendingSubscription(gatt, "disconnected")
                onDisconnected(gatt)
            }
        }
    }

    fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        logger("GATT callback device=${addressOf(gatt)}: services-discovered status=$status (${statusName(status)}); serviceCount=${gatt.services.size}")
        gatt.services.forEach { service ->
            logger("SERVICE ${service.uuid}")
            service.characteristics.forEach { characteristic ->
                logger(" CHAR ${characteristic.uuid} properties=${properties(characteristic.properties)}")
            }
        }
        if (status != BluetoothGatt.GATT_SUCCESS) return

        // Keep service-to-characteristic ownership intact. A device must expose
        // both required FT-10C characteristics under the expected primary service.
        val discoveredServices = gatt.services.associate { service ->
            service.uuid to service.characteristics.map { it.uuid }.toSet()
        }
        val matchedProfile = profileRegistry.findForGatt(discoveredServices)

        if (matchedProfile == null) {
            markUnknown(addressOf(gatt))
            logger("DEVICE PROFILE: no registered profile matches this GATT layout; no model-specific commands will be sent; closing unsupported GATT connection")
            onUnknownProfile(gatt)
            return
        }

        markReady(addressOf(gatt), matchedProfile.modelId)
        logger("DEVICE PROFILE: matched ${matchedProfile.displayName} (${matchedProfile.modelId}); identity confirmed by GATT UUIDs, not by Bluetooth name or MAC")
        val subscriptionStarted = enableNotifications(gatt, matchedProfile)

        val timeCharacteristic = gatt.getService(timeServiceUuid)
            ?.getCharacteristic(timeWriteCharacteristicUuid)
        val timeWriteAvailable = timeCharacteristic != null &&
            timeCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        if (!subscriptionStarted) {
            logger("GATT subscription: request could not be started; dependent workflow withheld")
            return
        }
        clearPendingSubscription(gatt, "replaced")
        val timeout = Runnable {
            val pending = pendingSubscriptions.remove(gatt) ?: return@Runnable
            logger("GATT subscription TIMEOUT: ${pending.profile.notifyCharacteristicUuid}; CCCD confirmation not received within ${SUBSCRIPTION_TIMEOUT_MS}ms")
        }
        pendingSubscriptions[gatt] = PendingSubscription(matchedProfile, timeCharacteristic != null, timeWriteAvailable, timeout)
        mainHandler.postDelayed(timeout, SUBSCRIPTION_TIMEOUT_MS)
        logger("GATT subscription PENDING: CCCD write callback required before sync workflow")
    }


    fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (descriptor.uuid != cccdUuid) return
        val pending = pendingSubscriptions[gatt] ?: return
        if (descriptor.characteristic.uuid != pending.profile.notifyCharacteristicUuid) return
        mainHandler.removeCallbacks(pending.timeout)
        pendingSubscriptions.remove(gatt)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            logger("GATT subscription FAILED: CCCD status=$status; dependent workflow withheld")
            return
        }
        logger("GATT subscription CONFIRMED: ${pending.profile.notifyCharacteristicUuid}; starting connection workflow")
        onMatchedProfile(gatt, pending.profile, pending.timeCharacteristicFound, pending.timeWriteAvailable)
    }


    private fun clearPendingSubscription(gatt: BluetoothGatt, reason: String) {
        pendingSubscriptions.remove(gatt)?.let {
            mainHandler.removeCallbacks(it.timeout)
            logger("GATT subscription state reset ($reason)")
        }
    }

    fun onNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        logger("GATT callback device=${addressOf(gatt)}: NOTIFY ${characteristic.uuid} bytes=${value.size}")
        onNotification(characteristic.uuid, value.copyOf())
    }
    companion object { private const val SUBSCRIPTION_TIMEOUT_MS = 8_000L }
}
