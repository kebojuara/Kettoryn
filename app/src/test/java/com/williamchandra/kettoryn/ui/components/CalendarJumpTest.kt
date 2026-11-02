package com.williamchandra.kettoryn.ui.components

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.williamchandra.kettoryn.domain.*

class CalendarJumpTest {
    @Test fun `jump from October 2026 to January 2023 without stepping months`() {
        val current = YearMonth.of(2026, 10)
        assertEquals(YearMonth.of(2023, 1), calendarMonth(current, 2023, 1))
    }
    @Test fun `leap and short month day options are real calendar days`() {
        assertEquals(29, calendarMonth(YearMonth.of(2026, 10), 2024, 2).lengthOfMonth())
        assertEquals(28, calendarMonth(YearMonth.of(2026, 10), 2023, 2).lengthOfMonth())
    }
    @Test fun `rest accepts endpoints and rejects pasted decimals negatives overflow and empty`() {
        assertEquals(0, UnitConverter.parseRestSeconds("0"))
        assertEquals(3600, UnitConverter.parseRestSeconds("3600"))
        listOf("", "-1", "1.5", "3601", "999999999999").forEach { assertNull(UnitConverter.parseRestSeconds(it)) }
    }
}
