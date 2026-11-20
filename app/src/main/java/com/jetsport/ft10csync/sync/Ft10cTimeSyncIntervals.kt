package com.jetsport.ft10csync.sync

/** Supported user-selectable periods for automatic FT-10C clock correction. */
internal object Ft10cTimeSyncIntervals {
    const val DEFAULT_MINUTES = 180
    val SUPPORTED_MINUTES: Set<Int> = setOf(30, 180, 360, 720)

    fun normalize(minutes: Int): Int =
        minutes.takeIf { it in SUPPORTED_MINUTES } ?: DEFAULT_MINUTES

    fun delayMillis(minutes: Int): Long = normalize(minutes) * 60_000L
}
