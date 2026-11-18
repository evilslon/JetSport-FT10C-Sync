package com.jetsport.ft10csync

import org.junit.Assert.assertEquals
import org.junit.Test

class StepMetricsCalculatorTest {
    @Test fun reproducesManufacturerMaleWalkingFormula() {
        val result = StepMetricsCalculator.calculate(1000, 0, 1000, StepProfile(187, 120, true))
        assertEquals(0.76719929, result.distanceKm, 1e-7)
        assertEquals(54.3848796, result.caloriesKcal, 1e-6)
        assertEquals(1000, result.walkingSteps)
        assertEquals(0, result.runningSteps)
    }

    @Test fun reproducesManufacturerMaleRunningFormula() {
        val result = StepMetricsCalculator.calculate(1000, 1000, 0, StepProfile(187, 120, true))
        assertEquals(1.021217285, result.distanceKm, 1e-7)
        assertEquals(113.8267617, result.caloriesKcal, 1e-6)
    }

    @Test fun profileSexChangesDistanceAndCaloriesAsObserved() {
        val male = StepMetricsCalculator.calculate(2938, 0, 2938, StepProfile(155, 120, true))
        val female = StepMetricsCalculator.calculate(2938, 0, 2938, StepProfile(155, 120, false))
        assertEquals(1.872, male.distanceKm, 0.01)
        assertEquals(1.890, female.distanceKm, 0.01)
        assertEquals(132.0, male.caloriesKcal, 1.0)
        assertEquals(179.0, female.caloriesKcal, 1.0)
    }

    @Test fun missingActivityBreakdownTreatsAllStepsAsWalking() {
        val result = StepMetricsCalculator.calculate(1000, 0, 0, StepProfile(187, 120, true))
        assertEquals(1000, result.walkingSteps)
        assertEquals(0, result.runningSteps)
    }

    @Test fun preservesTotalWhenActivityBreakdownIsPartial() {
        val result = StepMetricsCalculator.calculate(1000, 200, 300, StepProfile(187, 120, true))
        assertEquals(800, result.walkingSteps)
        assertEquals(200, result.runningSteps)
    }

    @Test fun clampsNegativeCountersAndExcessiveSubcounts() {
        val negative = StepMetricsCalculator.calculate(-10, -1, -1, StepProfile(187, 120, true))
        assertEquals(0.0, negative.distanceKm, 0.0)
        assertEquals(0.0, negative.caloriesKcal, 0.0)
        val excessive = StepMetricsCalculator.calculate(100, 150, 0, StepProfile(187, 120, true))
        assertEquals(100, excessive.runningSteps)
        assertEquals(0, excessive.walkingSteps)
    }
}
