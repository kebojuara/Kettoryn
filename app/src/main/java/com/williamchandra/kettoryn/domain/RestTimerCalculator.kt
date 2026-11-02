package com.williamchandra.kettoryn.domain

/** A wall-clock change must never lengthen an already running countdown. */
object RestTimerCalculator {
    fun remainingSeconds(endWall: Long?, endElapsed: Long?, nowWall: Long, nowElapsed: Long): Long {
        if (endWall == null) return 0
        val wall = ((endWall - nowWall + 999) / 1000).coerceIn(0, 3600)
        val monotonic = endElapsed?.let { ((it - nowElapsed + 999) / 1000).coerceIn(0, 3600) }
        return minOf(wall, monotonic ?: wall)
    }
}
