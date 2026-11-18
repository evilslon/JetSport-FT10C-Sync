package com.jetsport.ft10csync

/**
 * Reimplementation of the manufacturer's PedometerUtils formulas for the
 * FT-10C profile path (feature 4096). Distances are kilometres.
 *
 * The SDK call site passes gender != FEMALE; therefore its boolean true
 * branch is the male branch.
 */
data class StepProfile(
    val heightCm: Int,
    val weightKg: Int,
    val isMale: Boolean,
    val ageYears: Int = 45
)

data class StepMetrics(
    val distanceKm: Double,
    val caloriesKcal: Double,
    val walkingSteps: Int,
    val runningSteps: Int
)

object StepMetricsCalculator {
    fun calculate(
        totalSteps: Int,
        runSteps: Int,
        walkSteps: Int,
        profile: StepProfile
    ): StepMetrics {
        val total = totalSteps.coerceAtLeast(0)
        val reportedRun = runSteps.coerceIn(0, total)
        val reportedWalk = walkSteps.coerceIn(0, total)

        // FT-10C history records observed so far can have run=0 and walk=0
        // while totalSteps is non-zero. In that case the SDK's non-running
        // fallback calculates the complete total as walking.
        val hasActivityBreakdown = reportedRun + reportedWalk > 0
        val run = if (hasActivityBreakdown) reportedRun else 0
        // Preserve the total. Any uncategorized remainder is conservatively
        // treated as walking rather than silently discarded.
        val walk = if (hasActivityBreakdown) (total - run).coerceAtLeast(0) else total

        val height = profile.heightCm.toDouble()
        val weight = profile.weightKg.toDouble()

        // PedometerUtils: true = male at DeviceManagerImpl call site.
        val walkLengthFactor = if (profile.isMale) 0.410267 else 0.4151582
        val runLengthFactor = if (profile.isMale) 0.5461055 else 0.5047813
        val walkCaloriesFactor = if (profile.isMale) 0.5907296 else 0.7918928
        val runCaloriesFactor = if (profile.isMale) 0.9288487 else 1.23493

        val walkStrideCm = height * walkLengthFactor
        val runStrideCm = height * runLengthFactor
        val walkDistance = walk * walkStrideCm / 100000.0
        val runDistance = run * runStrideCm / 100000.0
        val walkCalories = weight * walkCaloriesFactor * walkStrideCm * walk / 100000.0
        val runCalories = weight * runCaloriesFactor * runStrideCm * run / 100000.0

        return StepMetrics(
            distanceKm = walkDistance + runDistance,
            caloriesKcal = walkCalories + runCalories,
            walkingSteps = walk,
            runningSteps = run
        )
    }
}
