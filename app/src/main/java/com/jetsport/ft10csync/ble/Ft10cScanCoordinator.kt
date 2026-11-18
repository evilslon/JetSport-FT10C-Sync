package com.jetsport.ft10csync.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.Handler
import com.jetsport.ft10csync.device.DeviceProfileRegistry

/** Owns the BLE scan lifecycle, timeout, advertisement classification and scan diagnostics. */
class Ft10cScanCoordinator(
    private val adapter: BluetoothAdapter,
    private val handler: Handler,
    private val profileRegistry: DeviceProfileRegistry,
    private val listener: Listener
) {
    interface Listener {
        fun onDeviceFound(device: BluetoothDevice, address: String, name: String, rssi: Int)
        fun onLog(message: String)
        fun onScanStateChanged(scanning: Boolean)
    }

    private var totalResultCount = 0
    private var targetResultCount = 0
    private var otherResultCount = 0
    private val discoveredAddresses = linkedSetOf<String>()

    @Volatile
    var isScanning: Boolean = false
        private set

    private var scanGeneration = 0L
    private var activeScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var activeCallback: ScanCallback? = null
    private var stopTask: Runnable? = null

    private fun isCurrentScan(generation: Long): Boolean =
        isScanning && scanGeneration == generation

    private fun createCallback(generation: Long): ScanCallback =
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (isCurrentScan(generation)) processResult(callbackType, result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                if (!isCurrentScan(generation)) return
                listener.onLog("BATCH RESULTS: ${results.size}")
                results.forEach { processResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                if (!isCurrentScan(generation)) return
                stopTask?.let(handler::removeCallbacks)
                isScanning = false
                activeCallback = null
                activeScanner = null
                stopTask = null
                listener.onLog("SCAN FAILED code=$errorCode (${scanErrorName(errorCode)})")
                listener.onScanStateChanged(false)
            }
        }

    fun start() {
        check(!isScanning) { "BLE scan is already running" }
        check(adapter.isEnabled) { "Bluetooth is disabled" }
        val scanner = adapter.bluetoothLeScanner ?: error("BluetoothLeScanner is unavailable")

        totalResultCount = 0
        targetResultCount = 0
        otherResultCount = 0
        discoveredAddresses.clear()

        val generation = ++scanGeneration
        val scanCallback = createCallback(generation)
        val timeoutTask = Runnable {
            if (isCurrentScan(generation)) stop("тайм-аут 20 секунд")
        }
        activeScanner = scanner
        activeCallback = scanCallback
        stopTask = timeoutTask
        isScanning = true

        listener.onLog("Перед startScan: adapterEnabled=${adapter.isEnabled}; scanner=$scanner; API=${Build.VERSION.SDK_INT}")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0L)
            .build()

        listener.onScanStateChanged(true)
        try {
            scanner.startScan(null, settings, scanCallback)
            listener.onLog("startScan() вызван успешно; режим LOW_LATENCY, фильтров нет; длительность 20 секунд")
            if (isCurrentScan(generation)) handler.postDelayed(timeoutTask, SCAN_DURATION_MS)
        } catch (error: Exception) {
            if (scanGeneration == generation) {
                isScanning = false
                activeScanner = null
                activeCallback = null
                stopTask = null
            }
            listener.onScanStateChanged(false)
            throw error
        }
    }

    fun stop(reason: String = "по запросу") {
        val callback = activeCallback
        val scanner = activeScanner
        val timeout = stopTask
        if (!isScanning) {
            timeout?.let(handler::removeCallbacks)
            stopTask = null
            return
        }

        // Invalidate the callback before asking Android to stop. Any queued callback
        // from this session is ignored, even if another scan starts immediately.
        isScanning = false
        scanGeneration++
        activeCallback = null
        activeScanner = null
        stopTask = null
        timeout?.let(handler::removeCallbacks)

        try {
            if (scanner != null && callback != null) scanner.stopScan(callback)
            listener.onLog(
                "Сканирование остановлено ($reason). Всего callback-пакетов: $totalResultCount; " +
                    "кандидаты известных профилей: $targetResultCount; неизвестные устройства: $otherResultCount; " +
                    "уникальных устройств: ${discoveredAddresses.size}"
            )
        } catch (e: Exception) {
            listener.onLog("Ошибка остановки сканирования: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            listener.onScanStateChanged(false)
        }
    }

    private fun processResult(callbackType: Int, result: ScanResult) {
        totalResultCount++
        val record = result.scanRecord
        val name = record?.deviceName ?: safeDeviceName(result.device) ?: ""
        val advertisedUuids = record?.serviceUuids?.mapNotNull { it.uuid }?.toSet() ?: emptySet()
        val profile = profileRegistry.findForAdvertisement(name, advertisedUuids)
        val address = safeAddress(result.device) ?: return
        val firstSeen = discoveredAddresses.add(address)
        val raw = record?.bytes?.toHex() ?: "нет scanRecord"
        val connectable = if (Build.VERSION.SDK_INT >= 26) result.isConnectable.toString() else "API<26: unavailable"

        if (profile != null) {
            targetResultCount++
            listener.onLog(
                "${profile.displayName} candidate ADV #$targetResultCount type=$callbackType " +
                    "name=${name.ifBlank { "без имени" }} address=$address RSSI=${result.rssi} dBm " +
                    "connectable=$connectable data=$raw"
            )
        } else {
            otherResultCount++
            // Unknown devices remain visible; repeated advertisements are omitted from the log.
            if (firstSeen) {
                listener.onLog(
                    "BLE device discovered name=${name.ifBlank { "без имени" }} address=$address " +
                        "RSSI=${result.rssi} dBm connectable=$connectable"
                )
            }
        }

        listener.onDeviceFound(result.device, address, name.ifBlank { "Неизвестное BLE-устройство" }, result.rssi)
    }

    private fun safeAddress(device: BluetoothDevice): String? = try {
        device.address
    } catch (_: SecurityException) {
        null
    }

    private fun safeDeviceName(device: BluetoothDevice): String? = try {
        device.name
    } catch (_: SecurityException) {
        null
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun scanErrorName(code: Int): String = when (code) {
        1 -> "SCAN_FAILED_ALREADY_STARTED"
        2 -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
        3 -> "SCAN_FAILED_INTERNAL_ERROR"
        4 -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
        5 -> "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES"
        6 -> "SCAN_FAILED_SCANNING_TOO frequently"
        else -> "UNKNOWN"
    }

    companion object {
        private const val SCAN_DURATION_MS = 20_000L
    }
}
