package com.jetsport.ft10csync.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepHistoryMergePolicyTest {
    @Test fun preservesLiveTotalReceivedAfterHistoryRequestStarted() {
        assertTrue(StepHistoryMergePolicy.shouldPreserveLiveTotal(1_001L, 1_000L))
    }

    @Test fun preservesLiveTotalAtSameMillisecondAsRequestStart() {
        assertTrue(StepHistoryMergePolicy.shouldPreserveLiveTotal(1_000L, 1_000L))
    }

    @Test fun letsHistoryReplaceLiveTotalReceivedBeforeRequest() {
        assertFalse(StepHistoryMergePolicy.shouldPreserveLiveTotal(999L, 1_000L))
    }

    @Test fun doesNotPreserveWhenRequestTimestampIsMissing() {
        assertFalse(StepHistoryMergePolicy.shouldPreserveLiveTotal(1_001L, 0L))
    }
}
