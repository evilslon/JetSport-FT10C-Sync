package com.jetsport.ft10csync.data

/**
 * Resolves the only safe precedence rule available for overlapping B1/B2 data:
 * a live B1 total received after the current B2 request began is newer than
 * that request's history snapshot and must remain the hourly total.
 */
object StepHistoryMergePolicy {
    fun shouldPreserveLiveTotal(liveUpdatedAtMs: Long, historyRequestStartedAtMs: Long): Boolean =
        historyRequestStartedAtMs > 0L && liveUpdatedAtMs >= historyRequestStartedAtMs
}
