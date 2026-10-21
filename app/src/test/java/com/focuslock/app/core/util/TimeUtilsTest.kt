package com.focuslock.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

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

    @Test
    fun test4AmDailyResetCycle_Before4AmBelongsToYesterdayCycle() {
        val zone = ZoneId.of("Asia/Kolkata")
        // 2:30 AM on Oct 22
        val earlyMorning = ZonedDateTime.of(2026, 10, 22, 2, 30, 0, 0, zone)
        val testProvider = object : TimeProvider {
            override fun currentTimeMillis(): Long = earlyMorning.toInstant().toEpochMilli()
            override fun elapsedRealtime(): Long = 1000L
            override fun getZoneId(): ZoneId = zone
            override fun getTodayLocalDate(): LocalDate {
                val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(currentTimeMillis()), getZoneId())
                return if (now.hour < 4) now.toLocalDate().minusDays(1) else now.toLocalDate()
            }
            override fun getNextMidnightMillis(): Long {
                val cycleDate = getTodayLocalDate()
                return cycleDate.plusDays(1).atTime(4, 0).atZone(getZoneId()).toInstant().toEpochMilli()
            }
            override fun getStartOfDayMillis(): Long {
                val cycleDate = getTodayLocalDate()
                return cycleDate.atTime(4, 0).atZone(getZoneId()).toInstant().toEpochMilli()
            }
        }

        // At 2:30 AM on Oct 22, the cycle belongs to Oct 21
        assertEquals(LocalDate.of(2026, 10, 21), testProvider.getTodayLocalDate())

        // Next reset is at 4:00 AM on Oct 22 (1.5 hours later)
        val nextResetZdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(testProvider.getNextMidnightMillis()), zone)
        assertEquals(2026, nextResetZdt.year)
        assertEquals(10, nextResetZdt.monthValue)
        assertEquals(22, nextResetZdt.dayOfMonth)
        assertEquals(4, nextResetZdt.hour)
        assertEquals(0, nextResetZdt.minute)
    }

    @Test
    fun test4AmDailyResetCycle_After4AmBelongsToTodayCycle() {
        val zone = ZoneId.of("Asia/Kolkata")
        // 10:00 AM on Oct 22
        val daytime = ZonedDateTime.of(2026, 10, 22, 10, 0, 0, 0, zone)
        val testProvider = object : TimeProvider {
            override fun currentTimeMillis(): Long = daytime.toInstant().toEpochMilli()
            override fun elapsedRealtime(): Long = 1000L
            override fun getZoneId(): ZoneId = zone
            override fun getTodayLocalDate(): LocalDate {
                val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(currentTimeMillis()), getZoneId())
                return if (now.hour < 4) now.toLocalDate().minusDays(1) else now.toLocalDate()
            }
            override fun getNextMidnightMillis(): Long {
                val cycleDate = getTodayLocalDate()
                return cycleDate.plusDays(1).atTime(4, 0).atZone(getZoneId()).toInstant().toEpochMilli()
            }
            override fun getStartOfDayMillis(): Long {
                val cycleDate = getTodayLocalDate()
                return cycleDate.atTime(4, 0).atZone(getZoneId()).toInstant().toEpochMilli()
            }
        }

        // At 10:00 AM on Oct 22, the cycle belongs to Oct 22
        assertEquals(LocalDate.of(2026, 10, 22), testProvider.getTodayLocalDate())

        // Next reset is at 4:00 AM on Oct 23
        val nextResetZdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(testProvider.getNextMidnightMillis()), zone)
        assertEquals(LocalDate.of(2026, 10, 23), nextResetZdt.toLocalDate())
        assertEquals(4, nextResetZdt.hour)
    }
}
