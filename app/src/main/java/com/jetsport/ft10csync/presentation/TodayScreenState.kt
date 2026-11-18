package com.jetsport.ft10csync.presentation

import com.jetsport.ft10csync.StepMetrics

/** Immutable data contract consumed by the Compose Today screen. */
data class TodayScreenState(
    val steps: Int,
    val activity: StepMetrics,
    val hasTodayData: Boolean = false,
    val heartRateBpm: Int? = null,
    val heartRateTime: String? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val pressureTime: String? = null,
    val batteryPercent: Int? = null,
    val stepHistoryStatus: String? = null
)
