package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import com.jetsport.ft10csync.sync.Ft10cTimeSyncState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * Owns the FT-10C A3 time-write workflow independently of Activity UI.
 * The current Activity still supplies session facts and presentation callbacks;
 * the command can later move with the GATT runtime without changing its packet.
 */
internal class Ft10cTimeCommandCoordinator(
    private val handler: Handler,
    private val gattProvider: () -> BluetoothGatt?,
    private val isCurrentConnection: (BluetoothGatt) -> Boolean,
    private val hasBlePermissions: () -> Boolean,
    private val requestBlePermissions: () -> Unit,
    private val isFt10cProfileConfirmed: () -> Boolean,
    private val isOtherWorkflowBusy: () -> Boolean,
    private val timeSyncState: Ft10cTimeSyncState,
    private val commandChannel: Ft10cGattCommandChannel,
    private val serviceUuid: UUID,
    private val characteristicUuid: UUID,
    private val setPendingWriteKind: (String?) -> Unit,
    private val onCommandCompleted: (String, Int, Boolean) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onCanSyncTimeChanged: (Boolean) -> Unit,
    private val logger: (String) -> Unit
) {
    private var retry: Runnable? = null

    fun sync(automatic: Boolean = false) {
        val currentGatt = gattProvider()
        if (currentGatt == null || !isCurrentConnection(currentGatt)) {
            logger("TIME SYNC cancelled: no active BLE connection")
            return
        }
        if (!hasBlePermissions()) {
            logger("TIME SYNC cancelled: Bluetooth Connect permission is missing")
            requestBlePermissions()
            return
        }
        if (timeSyncState.writePending) {
            logger("TIME SYNC: previous write has not completed yet")
            return
        }
        if (commandChannel.isBusy || isOtherWorkflowBusy()) {
            scheduleRetry(currentGatt, automatic)
            return
        }
        if (!isFt10cProfileConfirmed()) {
            logger("TIME SYNC blocked: active FT-10C profile is not verified")
            onStatus("Синхронизация недоступна для неподдерживаемой модели")
            return
        }
        val characteristic = currentGatt.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
        if (characteristic == null) {
            logger("TIME SYNC: characteristic 0x33F1 was not found in service 0x55FF")
            onStatus("Не найдена характеристика 0x33F1")
            return
        }
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
            logger("TIME SYNC: 0x33F1 does not advertise WRITE")
            onStatus("0x33F1 не поддерживает WRITE")
            return
        }

        val calendar = timeSyncState.calendarForSync(automatic)
        val packet = Ft10cTimeSyncState.encodeOriginalTimePacket(calendar)
        val localStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(calendar.time)
        logger("TIME SYNC: ${if (automatic) "phone time" else "manual date/time"}=$localStamp")
        logger("TIME SYNC: original JetSport packet (${packet.size} bytes)=${packet.toHex()}")

        cancelPending()
        onCanSyncTimeChanged(false)
        onStatus("Отправка команды времени…")
        commandChannel.enqueue(
            name = "TIME",
            packet = packet,
            onStarted = { setPendingWriteKind("TIME") },
            onAccepted = {
                timeSyncState.markWriteAccepted()
                onStatus("Запрос записи принят Android. Ожидание BLE callback…")
                logger("TIME SYNC: writeCharacteristic accepted by Android; waiting for onCharacteristicWrite (5-second timeout)")
                if (automatic) logger("AUTO TIME SYNC: automatic A3 command accepted by GATT queue")
            },
            onCompleted = { status, timedOut -> onCommandCompleted("TIME", status, timedOut) }
        )
    }

    fun cancelPending() {
        retry?.let(handler::removeCallbacks)
        retry = null
    }

    private fun scheduleRetry(gatt: BluetoothGatt, automatic: Boolean) {
        if (retry != null) return
        logger("TIME SYNC: GATT is busy; retrying in 2 seconds")
        val next = Runnable {
            retry = null
            if (isCurrentConnection(gatt) && gattProvider() === gatt) sync(automatic)
        }
        retry = next
        handler.postDelayed(next, RETRY_DELAY_MS)
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private companion object {
        const val RETRY_DELAY_MS = 2_000L
    }
}
