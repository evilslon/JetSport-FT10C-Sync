package com.jetsport.ft10csync.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryChartDataTest {
    @Test
    fun dailyStepsAreSortedByIsoDateAscending() {
        val input = listOf(
            "2026-11-14" to 72,
            "2025-02-14" to 265,
            "2024-02-29" to 35,
            "2026-11-09" to 1966
        )

        assertEquals(
            listOf(
                "2024-02-29" to 35,
                "2025-02-14" to 265,
                "2026-11-09" to 1966,
                "2026-11-14" to 72
            ),
            sortDailyStepsChronologically(input)
        )
    }

    @Test
    fun emptyDailyStepListRemainsEmpty() {
        assertEquals(emptyList<Pair<String, Int>>(), sortDailyStepsChronologically(emptyList()))
    }
}
