package com.williamchandra.kettoryn.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerCalculatorTest {
    @Test fun countdownSurvivesRecreationAndDoesNotGrowWhenClockMovesBack() {
        assertEquals(30L, RestTimerCalculator.remainingSeconds(130000, 40000, 100000, 10000))
        assertEquals(20L, RestTimerCalculator.remainingSeconds(130000, 40000, 110000, 20000))
        assertEquals(20L, RestTimerCalculator.remainingSeconds(130000, 40000, 1000, 20000))
        assertEquals(0L, RestTimerCalculator.remainingSeconds(130000, 40000, 200000, 20000))
    }
    @Test fun zeroExpiredAndLegacyWallOnlyDeadlinesAreBounded() {
        assertEquals(0L, RestTimerCalculator.remainingSeconds(null, null, 100, 100))
        assertEquals(0L, RestTimerCalculator.remainingSeconds(100, 100, 101, 101))
        assertEquals(3600L, RestTimerCalculator.remainingSeconds(Long.MAX_VALUE / 2, null, 0, 0))
        assertEquals(90L, RestTimerCalculator.remainingSeconds(100000, null, 10000, 500))
    }
}
