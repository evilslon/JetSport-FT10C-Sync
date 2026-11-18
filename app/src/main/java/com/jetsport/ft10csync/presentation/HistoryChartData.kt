package com.jetsport.ft10csync.presentation

/** Stable chronological ordering for daily step totals keyed by ISO-8601 date. */
internal fun sortDailyStepsChronologically(
    records: List<Pair<String, Int>>
): List<Pair<String, Int>> = records.sortedBy { it.first }
