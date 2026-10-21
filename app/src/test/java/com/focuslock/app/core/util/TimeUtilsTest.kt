package com.focuslock.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TimeUtilsTest {

    @Test
    fun testFormatCountdown_CorrectFormatting() {
        assertEquals("00:00:00", TimeUtils.formatCountdown(0L))
        assertEquals("00:01:30", TimeUtils.formatCountdown(90L))
        assertEquals("01:00:00", TimeUtils.formatCountdown(3600L))
        assertEquals("08:29:42", TimeUtils.formatCountdown(30582L))
    }

    @Test
    fun testFormatDurationShort_Formatting() {
        assertEquals("0m", TimeUtils.formatDurationShort(0L))
        assertEquals("30m", TimeUtils.formatDurationShort(1800L))
        assertEquals("1h 15m", TimeUtils.formatDurationShort(4500L))
        assertEquals("45s", TimeUtils.formatDurationShort(45L))
    }

    @Test
    fun testDateFormattingAndParsing() {
        val date = LocalDate.of(2026, 10, 21)
        val formatted = TimeUtils.formatDate(date)
        assertEquals("2026-10-21", formatted)

        val parsed = TimeUtils.parseDate(formatted)
        assertEquals(date, parsed)
    }
}
