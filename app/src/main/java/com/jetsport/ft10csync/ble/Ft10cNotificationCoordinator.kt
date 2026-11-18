package com.jetsport.ft10csync.ble

import android.os.Handler
import com.jetsport.ft10csync.data.MeasurementRepository
import com.jetsport.ft10csync.protocol.Ft10cNotificationDecoder
import com.jetsport.ft10csync.protocol.Ft10cNotificationEvent
import com.jetsport.ft10csync.sync.Ft10cBatteryCoordinator
import com.jetsport.ft10csync.sync.Ft10cHistoryCoordinator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Application-layer notification workflow. It translates decoded FT-10C notifications
 * into persistence and presentation events without owning an Activity or a GATT object.
 * Android BLE callbacks should forward immutable notification values to [handle].
 */
internal class Ft10cNotificationCoordinator(
    private val historyCoordinator: Ft10cHistoryCoordinator,
    private val batteryCoordinator: Ft10cBatteryCoordinator,
    private val measurementRepository: MeasurementRepository,
    private val eventDispatcher: BleUiEventDispatcher,
    private val mainHandler: Handler,
    private val historyTimeout: Runnable,
    private val onLiveDay: (String) -> Unit,
    private val onHistoryCommitted: (Int) -> Unit,
    private val onLog: (String) -> Unit
) {
    fun handle(uuid: UUID, value: ByteArray) {
        onLog("NOTIFY $uuid: ${value.toHex()}")
        when (val event = Ft10cNotificationDecoder.decode(uuid, value)) {
            is Ft10cNotificationEvent.AuxiliaryChannel -> {
                onLog("FF00 CHANNEL RX FF02 len=${event.payload.size} HEX=${event.payload.toHex()}")
            }
            is Ft10cNotificationEvent.LiveSteps -> {
                val dayKey = "%04d-%02d-%02d".format(Locale.US, event.year, event.month, event.day)
                val hourKey = "$dayKey %02d:00".format(Locale.US, event.hour)
                onLiveDay(dayKey)
                val calendar = java.util.Calendar.getInstance().apply {
                    set(event.year, event.month - 1, event.day, event.hour, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                measurementRepository.upsertLiveStepHour(calendar.timeInMillis, dayKey, event.count)
                eventDispatcher.dispatch(BleUiEvent.LiveStepsChanged(event.count))
                onLog("STEP B1: $hourKey = ${event.count} шагов")
            }
            is Ft10cNotificationEvent.HistoryPacket -> handleHistoryPacket(event.payload)
            is Ft10cNotificationEvent.Battery -> {
                val percent = event.percent
                eventDispatcher.dispatch(BleUiEvent.BatteryReceived(percent))
                batteryCoordinator.onResponse()
                onLog("BATTERY A2: byte1=$percent -> $percent%; successful response timestamp saved (raw=${value.toHex()})")
            }
            is Ft10cNotificationEvent.HeartRate -> {
                eventDispatcher.dispatch(BleUiEvent.HeartRateReceived(event.bpm, nowTime()))
                onLog("Распознано измерение ЧСС: ${event.bpm} уд/мин")
            }
            is Ft10cNotificationEvent.BloodPressure -> {
                eventDispatcher.dispatch(BleUiEvent.BloodPressureReceived(event.systolic, event.diastolic, nowTime()))
                onLog("Распознано измерение давления: ${event.systolic}/${event.diastolic}")
            }
            Ft10cNotificationEvent.MeasurementIntermediate ->
                onLog("Получено управляющее/промежуточное сообщение измерения")
            null -> Unit
        }
    }

    private fun handleHistoryPacket(payload: ByteArray) {
        when (val result = historyCoordinator.onPacket(payload)) {
            is Ft10cHistoryCoordinator.PacketResult.Ignored ->
                onLog("STEP HISTORY RX ignored: ${result.reason}; raw=${payload.toHex()}")
            is Ft10cHistoryCoordinator.PacketResult.RecordAccepted -> {
                val record = result.record
                val key = "%04d-%02d-%02d %02d:00".format(record.year, record.month, record.day, record.hour)
                onLog("STEP HISTORY RX: $key total=${record.totalSteps}, run=${record.runSteps}/${record.runDurationMinutes}m, walk=${record.walkSteps}/${record.walkDurationMinutes}m, uncategorized=${record.uncategorizedSteps}")
                eventDispatcher.dispatch(BleUiEvent.HistoryProgress(result.recordCount))
            }
            is Ft10cHistoryCoordinator.PacketResult.Completed -> {
                mainHandler.removeCallbacks(historyTimeout)
                if (result.valid) {
                    val records = result.records
                    measurementRepository.commitStepHistory(records) { commit ->
                        commit.onSuccess {
                            onLog("STEP HISTORY COMPLETE: ${records.size} records committed; B2 FD trailing=${result.trailingByte?.let { "%02X".format(it) } ?: "none"}")
                            onHistoryCommitted(records.size)
                        }.onFailure { error ->
                            onLog("STEP HISTORY DATABASE ERROR: ${error.javaClass.simpleName}: ${error.message}; previous saved history retained")
                        }
                    }
                } else {
                    onLog("STEP HISTORY ABORTED: malformed record seen; retained previous saved history")
                }
            }
            is Ft10cHistoryCoordinator.PacketResult.Invalid ->
                onLog("STEP HISTORY INVALID: ${result.reason}; raw=${payload.toHex()}")
        }
    }

    private fun nowTime(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
