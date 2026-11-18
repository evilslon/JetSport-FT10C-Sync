package com.jetsport.ft10csync.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementStateTest {
    @Test fun heartRateUpdatePreservesBloodPressure() {
        val initial = MeasurementState().withBloodPressure(120, 80, "10:00:00")
        assertEquals(
            MeasurementState(72, "10:01:00", 120, 80, "10:00:00"),
            initial.withHeartRate(72, "10:01:00")
        )
    }

    @Test fun bloodPressureUpdatePreservesHeartRate() {
        val initial = MeasurementState().withHeartRate(72, "10:00:00")
        assertEquals(
            MeasurementState(72, "10:00:00", 121, 81, "10:01:00"),
            initial.withBloodPressure(121, 81, "10:01:00")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHeartRateIsRejected() { MeasurementState().withHeartRate(0, "10:00:00") }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBloodPressureIsRejected() { MeasurementState().withBloodPressure(120, 0, "10:00:00") }
}
