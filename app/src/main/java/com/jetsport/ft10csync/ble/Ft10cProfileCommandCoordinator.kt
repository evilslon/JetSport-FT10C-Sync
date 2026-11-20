package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import com.jetsport.ft10csync.Ft10cProfileEncoder
import com.jetsport.ft10csync.StepProfile

/**
 * Owns the FT-10C A9 profile command workflow independently of Activity UI.
 *
 * Connection ownership and UI feedback are injected by the current session owner.
 * This is an intentional migration seam: the future BLE runtime can own this
 * coordinator without changing the packet format or serialized GATT channel.
 */
internal class Ft10cProfileCommandCoordinator(
    private val handler: Handler,
    private val gattProvider: () -> BluetoothGatt?,
    private val isCurrentConnection: (BluetoothGatt) -> Boolean,
    private val hasBlePermissions: () -> Boolean,
    private val isFt10cProfileConfirmed: () -> Boolean,
    private val isOtherWorkflowBusy: () -> Boolean,
    private val profileProvider: () -> StepProfile,
    private val commandChannel: Ft10cGattCommandChannel,
    private val serviceUuid: java.util.UUID,
    private val characteristicUuid: java.util.UUID,
    private val setPendingWriteKind: (String?) -> Unit,
    private val onCommandCompleted: (String, Int, Boolean) -> Unit,
    private val logger: (String) -> Unit
) {
    private var retry: Runnable? = null

    fun send(trigger: String) {
        val currentGatt = gattProvider() ?: return
        if (!isCurrentConnection(currentGatt) || !hasBlePermissions()) return
        if (!isFt10cProfileConfirmed()) {
            logger("PROFILE A9: blocked; active device has no verified FT-10C profile")
            return
        }
        if (commandChannel.isBusy || isOtherWorkflowBusy()) {
            scheduleRetry(currentGatt, trigger)
            return
        }
        val characteristic = currentGatt.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
        if (characteristic == null || characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
            logger("PROFILE A9: 0x33F1 WRITE unavailable")
            return
        }
        val profile = profileProvider()
        val packet = try {
            Ft10cProfileEncoder.encode(profile)
        } catch (error: Exception) {
            logger("PROFILE A9: invalid profile: ${error.message}")
            return
        }
        cancelRetry()
        commandChannel.enqueue(
            name = "PROFILE",
            packet = packet,
            onStarted = { setPendingWriteKind("PROFILE") },
            onAccepted = {
                logger("PROFILE A9 TX accepted by Android; trigger=$trigger; height=${profile.heightCm} weight=${profile.weightKg} age=${profile.ageYears} sex=${if (profile.isMale) "male" else "female"}; HEX=${packet.toHex()}")
            },
            onCompleted = { status, timedOut -> onCommandCompleted("PROFILE", status, timedOut) }
        )
    }

    fun cancelPending() {
        cancelRetry()
    }

    private fun scheduleRetry(gatt: BluetoothGatt, trigger: String) {
        if (retry != null) return
        logger("PROFILE A9: GATT busy; retry in 2s (trigger=$trigger)")
        val next = Runnable {
            retry = null
            if (isCurrentConnection(gatt) && gattProvider() === gatt) send(trigger)
        }
        retry = next
        handler.postDelayed(next, RETRY_DELAY_MS)
    }

    private fun cancelRetry() {
        retry?.let(handler::removeCallbacks)
        retry = null
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private companion object {
        const val RETRY_DELAY_MS = 2_000L
    }
}
