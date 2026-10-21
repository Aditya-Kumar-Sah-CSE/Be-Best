package com.focuslock.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

interface TimeProvider {
    fun currentTimeMillis(): Long
    fun elapsedRealtime(): Long
    fun getZoneId(): ZoneId
    fun getTodayLocalDate(): LocalDate
    fun getNextMidnightMillis(): Long
    fun getNextDailyResetMillis(): Long = getNextMidnightMillis()
    fun getStartOfDayMillis(): Long
}

class SystemTimeProvider(
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) : TimeProvider {

    companion object {
        const val DAILY_RESET_HOUR = 4 // Quotas reset at 4:00 AM local time
    }

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()

    override fun getZoneId(): ZoneId = zoneIdProvider()

    /**
     * Determines the active daily quota cycle.
     * If the time is before 4:00 AM, the cycle belongs to yesterday's 4 AM window.
     * At 4:00 AM, the new cycle begins.
     */
    override fun getTodayLocalDate(): LocalDate {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis()), getZoneId())
        return if (now.hour < DAILY_RESET_HOUR) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
    }

    /**
     * Start of the current 4:00 AM quota cycle in epoch milliseconds.
     */
    override fun getStartOfDayMillis(): Long {
        val cycleDate = getTodayLocalDate()
        val cycleStart = cycleDate.atTime(DAILY_RESET_HOUR, 0).atZone(getZoneId())
        return cycleStart.toInstant().toEpochMilli()
    }

    /**
     * Next 4:00 AM reset in epoch milliseconds.
     */
    override fun getNextMidnightMillis(): Long {
        val cycleDate = getTodayLocalDate()
        val nextReset = cycleDate.plusDays(1).atTime(DAILY_RESET_HOUR, 0).atZone(getZoneId())
        return nextReset.toInstant().toEpochMilli()
    }

    override fun getNextDailyResetMillis(): Long = getNextMidnightMillis()
}

object TimeUtils {

    const val DAILY_RESET_HOUR = 4

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    fun formatDate(date: LocalDate): String = date.format(DATE_FORMATTER)

    fun parseDate(dateStr: String): LocalDate = LocalDate.parse(dateStr, DATE_FORMATTER)

    fun formatCountdown(totalSeconds: Long): String {
        val sec = totalSeconds.coerceAtLeast(0L)
        val hours = TimeUnit.SECONDS.toHours(sec)
        val minutes = TimeUnit.SECONDS.toMinutes(sec) % 60
        val seconds = sec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDurationShort(totalSeconds: Long): String {
        val sec = totalSeconds.coerceAtLeast(0L)
        val hours = TimeUnit.SECONDS.toHours(sec)
        val minutes = TimeUnit.SECONDS.toMinutes(sec) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            sec > 0 -> "${sec}s"
            else -> "0m"
        }
    }

    // Convenience functions delegating to default SystemTimeProvider
    private val defaultProvider = SystemTimeProvider()

    fun getTodayDateString(): String = formatDate(defaultProvider.getTodayLocalDate())

    fun getSecondsUntilNextDailyReset(): Long {
        val diff = defaultProvider.getNextDailyResetMillis() - defaultProvider.currentTimeMillis()
        return (diff / 1000).coerceAtLeast(0L)
    }

    fun getSecondsUntilNextMidnight(): Long = getSecondsUntilNextDailyReset()

    fun getDayStartMillis(): Long = defaultProvider.getStartOfDayMillis()
}
