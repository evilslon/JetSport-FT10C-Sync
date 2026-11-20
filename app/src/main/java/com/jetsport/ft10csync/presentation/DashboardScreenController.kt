package com.jetsport.ft10csync.presentation

import com.jetsport.ft10csync.StepProfile
import com.jetsport.ft10csync.data.ActiveDayRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Immutable latest HR/BP values presented by the Today dashboard. */
data class MeasurementState(
    val heartRateBpm: Int? = null,
    val heartRateTime: String? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val pressureTime: String? = null
) {
    fun withHeartRate(bpm: Int, timeLabel: String): MeasurementState {
        require(bpm > 0) { "Heart rate must be positive" }
        return copy(heartRateBpm = bpm, heartRateTime = timeLabel)
    }

    fun withBloodPressure(top: Int, bottom: Int, timeLabel: String): MeasurementState {
        require(top > 0 && bottom > 0) { "Blood pressure values must be positive" }
        return copy(systolic = top, diastolic = bottom, pressureTime = timeLabel)
    }
}

/**
 * Coordinates the Today dashboard presentation without owning Android views.
 * The Activity forwards lifecycle and BLE events; this controller resolves the active
 * current calendar day, requests persisted data, and sends immutable UI state to the renderer.
 */
class DashboardScreenController(
    activeDayRepository: ActiveDayRepository,
    private val viewModel: DashboardViewModel,
    private val profileProvider: () -> StepProfile,
    private val languageProvider: () -> String,
    private val logger: (String) -> Unit,
    initialBatteryPercent: Int? = null
) {
    private val activeDayState = ActiveDayState(activeDayRepository.lastBraceletDayKey) { dayKey ->
        activeDayRepository.lastBraceletDayKey = dayKey
        logger("ACTIVE DAY: saved bracelet day=$dayKey")
    }

    private var todayStateListener: ((TodayScreenState) -> Unit)? = null
    private var latestActivity: com.jetsport.ft10csync.StepMetrics? = null
    private var latestSteps: Int = 0
    private var latestHasTodayData: Boolean = false
    private var requestedDayKey: String? = null
    private var measurementState = MeasurementState()
    private var batteryPercent: Int? = initialBatteryPercent?.coerceIn(0, 100)
    private var stepHistoryStatusKey: String? = null
    private var stepHistoryStatusCount: Int = 0

    /** Publishes the same immutable dashboard snapshot to the Compose presentation layer. */
    fun bindTodayStateListener(listener: (TodayScreenState) -> Unit) {
        todayStateListener = listener
        renderToday()
    }

    fun updateHeartRate(bpm: Int, timeLabel: String) {
        measurementState = measurementState.withHeartRate(bpm, timeLabel)
        renderToday()
    }

    fun updateBloodPressure(top: Int, bottom: Int, timeLabel: String) {
        measurementState = measurementState.withBloodPressure(top, bottom, timeLabel)
        renderToday()
    }

    fun updateBattery(percent: Int) {
        batteryPercent = percent.coerceIn(0, 100)
        renderToday()
    }

    fun updateStepHistoryStatus(statusKey: String, count: Int = 0) {
        stepHistoryStatusKey = statusKey
        stepHistoryStatusCount = count
        renderToday()
    }

    fun onLivePacket(dayKey: String) {
        activeDayState.onLivePacket(dayKey)
    }

    fun onDisconnected() {
        activeDayState.onDisconnected()
    }

    fun refresh() {
        // The phone calendar is the cold-start/offline fallback. After a live B1
        // packet arrives in this connection session, the bracelet-reported day
        // defines Today (including an intentional manual date change). B2 history
        // records never participate in active-day selection.
        val phoneDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val dayKey = activeDayState.resolveDayKey(phoneDay)
        if (requestedDayKey != dayKey) {
            requestedDayKey = dayKey
            latestSteps = 0
            latestHasTodayData = false
            latestActivity = com.jetsport.ft10csync.StepMetrics(0.0, 0.0, 0, 0)
            renderToday()
            logger("TODAY DASHBOARD: date changed to $dayKey; stale daily metrics cleared")
        }
        viewModel.refresh(dayKey, profileProvider())
    }

    fun render(state: DashboardUiState) {
        val snapshot = state.snapshot
        val activity = state.activity
        if (snapshot == null || activity == null) {
            state.errorMessage?.let { logger("DATABASE READ ERROR: $it") }
            return
        }

        latestSteps = snapshot.todaySteps
        latestHasTodayData = snapshot.hasTodayData
        latestActivity = activity
        renderToday()
    }

    private fun renderToday() {
        val activity = latestActivity ?: return
        val measurements = measurementState
        val state = TodayScreenState(
            steps = latestSteps,
            activity = activity,
            hasTodayData = latestHasTodayData,
            heartRateBpm = measurements.heartRateBpm,
            heartRateTime = measurements.heartRateTime,
            systolic = measurements.systolic,
            diastolic = measurements.diastolic,
            pressureTime = measurements.pressureTime,
            batteryPercent = batteryPercent,
            stepHistoryStatus = when (stepHistoryStatusKey) {
                "last_hour" -> if (languageProvider() == "ru") "Последний час: $stepHistoryStatusCount steps" else "Last hour: $stepHistoryStatusCount steps"
                "receiving" -> if (languageProvider() == "ru") "Получено записей: $stepHistoryStatusCount (ожидание завершения)" else "Received records: $stepHistoryStatusCount (waiting for completion)"
                "synced" -> if (languageProvider() == "ru") "История синхронизирована: $stepHistoryStatusCount записей" else "History synchronized: $stepHistoryStatusCount records"
                else -> null
            }
        )
        todayStateListener?.invoke(state)
    }
}
