package com.focuslock.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

interface TimeProvider {
    fun currentTimeMillis(): Long
    fun elapsedRealtime(): Long
    fun getZoneId(): ZoneId
    fun getTodayLocalDate(): LocalDate
    fun getNextMidnightMillis(): Long
    fun getStartOfDayMillis(): Long
}

class SystemTimeProvider(
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) : TimeProvider {

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()

    override fun getZoneId(): ZoneId = zoneIdProvider()

    override fun getTodayLocalDate(): LocalDate = LocalDate.now(getZoneId())

    override fun getStartOfDayMillis(): Long {
        val today = getTodayLocalDate()
        val startOfDay = today.atStartOfDay(getZoneId())
        return startOfDay.toInstant().toEpochMilli()
    }

    override fun getNextMidnightMillis(): Long {
        val tomorrow = getTodayLocalDate().plusDays(1)
        val midnight = tomorrow.atStartOfDay(getZoneId())
        return midnight.toInstant().toEpochMilli()
    }
}

object TimeUtils {

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

    fun getSecondsUntilNextMidnight(): Long {
        val diff = defaultProvider.getNextMidnightMillis() - defaultProvider.currentTimeMillis()
        return (diff / 1000).coerceAtLeast(0L)
    }

    fun getDayStartMillis(): Long = defaultProvider.getStartOfDayMillis()
}
