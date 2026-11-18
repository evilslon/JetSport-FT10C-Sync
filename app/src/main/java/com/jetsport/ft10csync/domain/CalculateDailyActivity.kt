package com.jetsport.ft10csync.domain

import com.jetsport.ft10csync.StepMetrics
import com.jetsport.ft10csync.StepMetricsCalculator
import com.jetsport.ft10csync.StepProfile
import com.jetsport.ft10csync.data.MeasurementRepository

/** Domain operation: convert the stored daily activity aggregate into user metrics. */
class CalculateDailyActivity {
    fun execute(snapshot: MeasurementRepository.DashboardSnapshot, profile: StepProfile): StepMetrics {
        val aggregate = snapshot.stepAggregate
        return StepMetricsCalculator.calculate(
            aggregate.totalSteps,
            aggregate.runSteps,
            aggregate.walkSteps,
            profile
        )
    }
}
