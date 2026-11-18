package com.jetsport.ft10csync.data

import android.content.Context
import com.jetsport.ft10csync.MeasurementDatabase
import com.jetsport.ft10csync.MeasurementEntry
import com.jetsport.ft10csync.StepDayAggregate
import com.jetsport.ft10csync.protocol.Ft10cStepRecord
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Serial data-access boundary. SQLite work never runs on the UI thread. */
class MeasurementRepository(context: Context, private val callbackExecutor: Executor) : AutoCloseable {
    private val database = MeasurementDatabase(context.applicationContext)
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jetsport-database").apply { isDaemon = true }
    }

    data class DashboardSnapshot(
        val todaySteps: Int,
        val stepAggregate: StepDayAggregate,
        val hasTodayData: Boolean
    )

    data class HistorySnapshot(
        val dailySteps: List<Pair<String, Int>>,
        val heartRate: List<MeasurementEntry>,
        val bloodPressure: List<MeasurementEntry>,
        val measurementCount: Int,
        val latestMeasurements: List<MeasurementEntry>
    )

    fun insertMeasurement(type: String, value1: Float, value2: Float, timestamp: Long, onComplete: (() -> Unit)? = null) =
        enqueue({ database.insert(type, value1, value2, timestamp) }, onComplete)

    fun upsertLiveStepHour(hourStart: Long, dayKey: String, steps: Int, onComplete: (() -> Unit)? = null) =
        enqueue({ database.upsertStepHour(hourStart, dayKey, steps) }, onComplete)

    fun commitStepHistory(records: List<Ft10cStepRecord>, onComplete: (Result<Unit>) -> Unit) {
        worker.execute {
            val result = runCatching { database.commitStepHistory(records) }
            callbackExecutor.execute { onComplete(result) }
        }
    }

    fun loadDashboard(dayKey: String, onComplete: (Result<DashboardSnapshot>) -> Unit) {
        worker.execute {
            val result = runCatching {
                DashboardSnapshot(
                    todaySteps = database.stepsForDay(dayKey),
                    stepAggregate = database.stepAggregateForDay(dayKey),
                    hasTodayData = database.hasStepDataForDay(dayKey)
                )
            }
            callbackExecutor.execute { onComplete(result) }
        }
    }

    /** Loads only History-tab data; independent from the Today dashboard query. */
    fun loadHistory(onComplete: (Result<HistorySnapshot>) -> Unit) {
        worker.execute {
            val result = runCatching {
                HistorySnapshot(
                    dailySteps = database.dailySteps(14),
                    heartRate = database.latest("HR", 30).reversed(),
                    bloodPressure = database.latest("BP", 30).reversed(),
                    measurementCount = database.count(),
                    latestMeasurements = database.latestAll(20)
                )
            }
            callbackExecutor.execute { onComplete(result) }
        }
    }

    private fun enqueue(operation: () -> Unit, onComplete: (() -> Unit)?) {
        worker.execute {
            operation()
            if (onComplete != null) callbackExecutor.execute(onComplete)
        }
    }

    override fun close() {
        worker.execute { database.close() }
        worker.shutdown()
    }
}
