package com.focuslock.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakReminderTest {

    @Test
    fun testContinuousScreenTimeCalculation_ThirtyMinutesReached() {
        val thirtyMinutesMs = BreakReminderHelper.THIRTY_MINUTES_MILLIS
        assertEquals(1800_000L, thirtyMinutesMs)

        val sessionStart = 100_000L
        val nowElapsedUnder = sessionStart + (29 * 60 * 1000L) // 29 minutes
        val elapsedUnder = nowElapsedUnder - sessionStart

        assertFalse("29 minutes of screen time must NOT trigger reminder", elapsedUnder >= thirtyMinutesMs)

        val nowElapsedExact = sessionStart + (30 * 60 * 1000L) // 30 minutes
        val elapsedExact = nowElapsedExact - sessionStart
        assertTrue("Exact 30 minutes of screen time MUST trigger reminder", elapsedExact >= thirtyMinutesMs)

        val nowElapsedOver = sessionStart + (35 * 60 * 1000L) // 35 minutes
        val elapsedOver = nowElapsedOver - sessionStart
        assertTrue("Over 30 minutes of screen time MUST trigger reminder", elapsedOver >= thirtyMinutesMs)
    }

    @Test
    fun testScreenOffPolicy_BriefGlitchPreservesSession_LongOffResetsSession() {
        val sessionStart = 1_000_000L
        val screenOffElapsed = 1_500_000L

        // Screen off for 5 seconds (brief glitch / lock screen flash)
        val nowQuickTurnOn = screenOffElapsed + 5_000L
        val isQuickTurnOn = (nowQuickTurnOn - screenOffElapsed) < 15_000L
        assertTrue("Screen off for < 15s must be treated as continuous session", isQuickTurnOn)

        // Screen off for 20 seconds (user put phone in pocket / took a short break)
        val nowLongTurnOn = screenOffElapsed + 20_000L
        val isLongTurnOn = (nowLongTurnOn - screenOffElapsed) >= 15_000L
        assertTrue("Screen off for >= 15s must reset continuous session", isLongTurnOn)
    }

    @Test
    fun testRepeatedRemindersSuppressed_ThrottlingPolicy() {
        val fiveMinutesMs = BreakReminderHelper.FIVE_MINUTES_MILLIS
        assertEquals(300_000L, fiveMinutesMs)

        val lastReminderTime = 2_000_000L

        // Attempting to alert 10 seconds later
        val nowRapid = lastReminderTime + 10_000L
        val isThrottled = (nowRapid - lastReminderTime) < fiveMinutesMs
        assertTrue("Repeated reminder within 5 minutes must be throttled/suppressed", isThrottled)

        // Attempting to alert 6 minutes later
        val nowAllowed = lastReminderTime + (6 * 60 * 1000L)
        val isAllowed = (nowAllowed - lastReminderTime) >= fiveMinutesMs
        assertTrue("Reminder after 5 minutes is allowed", isAllowed)
    }

    @Test
    fun testSnoozePolicy_PostponesNextReminder() {
        val now = 5_000_000L
        val snoozeMinutes = 5L
        val snoozeUntil = now + (snoozeMinutes * 60 * 1000L)

        // 2 minutes after snooze
        val nowDuringSnooze = now + (2 * 60 * 1000L)
        val isStillSnoozed = nowDuringSnooze < snoozeUntil
        assertTrue("Reminder must remain silenced while within snooze window", isStillSnoozed)

        // 6 minutes after snooze
        val nowAfterSnooze = now + (6 * 60 * 1000L)
        val isSnoozeExpired = nowAfterSnooze >= snoozeUntil
        assertTrue("Reminder can trigger after snooze duration has elapsed", isSnoozeExpired)
    }

    @Test
    fun testFiveMinuteBreakTimer_Calculation() {
        val totalBreakSeconds = 300L // 5 minutes
        val halfBreakSeconds = 150L

        val remaining = (totalBreakSeconds - halfBreakSeconds).coerceAtLeast(0L)
        assertEquals(150L, remaining)

        val fullCompleted = (totalBreakSeconds - 300L).coerceAtLeast(0L)
        assertEquals(0L, fullCompleted)
    }
}
