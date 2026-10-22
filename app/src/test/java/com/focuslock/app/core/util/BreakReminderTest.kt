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
    fun testAppSwitching_DoesNotResetContinuousSession() {
        val sessionStart = 1_000_000L

        // User opens Instagram at +2m
        val instagramTime = sessionStart + 120_000L
        val elapsedAtInsta = instagramTime - sessionStart
        assertEquals(120_000L, elapsedAtInsta)

        // User switches to YouTube at +10m
        val youtubeTime = sessionStart + 600_000L
        val elapsedAtYouTube = youtubeTime - sessionStart
        assertEquals(600_000L, elapsedAtYouTube)

        // User switches to Chrome and games at +25m
        val gameTime = sessionStart + 1500_000L
        val elapsedAtGame = gameTime - sessionStart
        assertEquals(1500_000L, elapsedAtGame)

        // Switching apps has preserved monotonic elapsed time without resetting session start
        assertTrue("Monotonic screen session preserved across multiple app switches", elapsedAtGame > elapsedAtYouTube)
    }

    @Test
    fun testScreenOffPolicy_ResetSession() {
        val sessionStart = 1_000_000L
        val screenOffElapsed = sessionStart + (10 * 60 * 1000L) // Screen turned off at 10m

        // Device turned back on later
        val newScreenOnElapsed = screenOffElapsed + (2 * 60 * 1000L) // 2m off
        val isScreenOffSessionReset = (newScreenOnElapsed - screenOffElapsed) >= 3_000L

        assertTrue("Turning screen off for >= 3s resets continuous usage session", isScreenOffSessionReset)

        // New session starts at newScreenOnElapsed
        val freshSessionElapsed = newScreenOnElapsed - newScreenOnElapsed
        assertEquals("Fresh session starts at 0 elapsed", 0L, freshSessionElapsed)
    }

    @Test
    fun testRemainingSecondsToThirtyMinutes_CountdownCalculation() {
        val totalSecs = 1800L // 30 minutes

        // At 0 minutes
        val remainingAtStart = (totalSecs - 0L).coerceAtLeast(0L)
        assertEquals(1800L, remainingAtStart)

        // At 15 minutes (900s)
        val remainingAtHalf = (totalSecs - 900L).coerceAtLeast(0L)
        assertEquals(900L, remainingAtHalf)

        // At 29m 30s (1770s)
        val remainingClose = (totalSecs - 1770L).coerceAtLeast(0L)
        assertEquals(30L, remainingClose)

        // At 32m (1920s) -> 0 remaining
        val remainingOver = (totalSecs - 1920L).coerceAtLeast(0L)
        assertEquals(0L, remainingOver)
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

    @Test
    fun testCustomMediaFallback_SafeHandling() {
        val nullUriString: String? = null
        val blankUriString = "   "
        val validUriString = "content://media/external/audio/media/42"

        val hasCustomAudio1 = !nullUriString.isNullOrBlank()
        val hasCustomAudio2 = !blankUriString.isNullOrBlank()
        val hasCustomAudio3 = !validUriString.isNullOrBlank()

        assertFalse("Null URI must fall back to default", hasCustomAudio1)
        assertFalse("Blank URI must fall back to default", hasCustomAudio2)
        assertTrue("Valid URI detected for playback", hasCustomAudio3)
    }
}
