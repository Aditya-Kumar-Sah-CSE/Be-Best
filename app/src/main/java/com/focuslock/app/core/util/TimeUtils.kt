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
        const val DAILY_RESET_HOUR = 0 // Quotas reset at 12:00 AM (midnight) local time
    }

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()

    override fun getZoneId(): ZoneId = zoneIdProvider()

    /**
     * Determines the active daily quota cycle (12:00 AM midnight to midnight).
     */
    override fun getTodayLocalDate(): LocalDate {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis()), getZoneId())
        return now.toLocalDate()
    }

    /**
     * Start of today (12:00 AM midnight) in epoch milliseconds.
     */
    override fun getStartOfDayMillis(): Long {
        val cycleDate = getTodayLocalDate()
        val cycleStart = cycleDate.atStartOfDay(getZoneId())
        return cycleStart.toInstant().toEpochMilli()
    }

    /**
     * Next 12:00 AM midnight reset in epoch milliseconds.
     */
    override fun getNextMidnightMillis(): Long {
        val cycleDate = getTodayLocalDate()
        val nextReset = cycleDate.plusDays(1).atStartOfDay(getZoneId())
        return nextReset.toInstant().toEpochMilli()
    }

    override fun getNextDailyResetMillis(): Long = getNextMidnightMillis()
}

object TimeUtils {

    const val DAILY_RESET_HOUR = 0

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
