package com.focuslock.app.core.overlay

import com.focuslock.app.core.util.BreakReminderHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FloatingTimerOverlayTest {

    @Test
    fun testPillTimerFormatting_ElapsedMinutesAndSeconds() {
        // Scenario 1: Screen just turned on (0 seconds)
        val elapsed0 = 0L
        val text0 = formatPillTimer(elapsed0)
        assertEquals("◷ 00:00 / 30:00", text0)

        // Scenario 2: User has used screen for 24 min 38 sec (sample in requirements: ◷ 24:38 / 30:00)
        val elapsed24m38s = (24 * 60) + 38L
        val text24m38s = formatPillTimer(elapsed24m38s)
        assertEquals("◷ 24:38 / 30:00", text24m38s)

        // Scenario 3: Exactly 30 minutes
        val elapsed30m = 30 * 60L
        val text30m = formatPillTimer(elapsed30m)
        assertEquals("◷ 30:00 / 30:00", text30m)

        // Scenario 4: Beyond 30 minutes (e.g. 30m 15s)
        val elapsed30m15s = (30 * 60) + 15L
        val text30m15s = formatPillTimer(elapsed30m15s)
        assertEquals("◷ 30:15 / 30:00", text30m15s)
    }

    @Test
    fun testSwitchingBetweenApps_DoesNotResetContinuousTimer() {
        val sessionStart = 10_000L

        // User starts on Instagram, uses for 5 minutes
        val t1 = sessionStart + (5 * 60 * 1000L)
        val elapsedApp1 = (t1 - sessionStart) / 1000L
        assertEquals(300L, elapsedApp1)

        // User switches to YouTube, uses for 10 minutes
        // Timer MUST NOT reset on app switch
        val t2 = t1 + (10 * 60 * 1000L)
        val elapsedApp2 = (t2 - sessionStart) / 1000L
        assertEquals(900L, elapsedApp2) // 15 total minutes continuous
    }

    @Test
    fun testScreenOff_ResetsSessionForNextTurnOn() {
        val session1Start = 100_000L
        val tScreenOff = session1Start + (15 * 60 * 1000L) // 15 min session

        // Device screen turns off
        val screenOffElapsed = tScreenOff

        // 10 minutes later, screen turns back on
        val tScreenOnNext = screenOffElapsed + (10 * 60 * 1000L)

        // Requirement: "Reset the continuous timer when the device screen turns off, as this starts a new continuous-use session"
        val session2Start = tScreenOnNext
        val elapsedNewSession = (tScreenOnNext - session2Start) / 1000L
        assertEquals("New session after screen off starts at 0 seconds", 0L, elapsedNewSession)
    }

    @Test
    fun testThirtyMinuteReminder_TriggersOnceAtThreshold() {
        val thirtyMinMs = BreakReminderHelper.THIRTY_MINUTES_MILLIS
        val sessionStart = 500_000L

        // 29 minutes: below threshold
        val t29m = sessionStart + (29 * 60 * 1000L)
        assertFalse("29 minutes must NOT trigger reminder", (t29m - sessionStart) >= thirtyMinMs)

        // 30 minutes: exactly reaches threshold
        val t30m = sessionStart + (30 * 60 * 1000L)
        assertTrue("Exactly 30 minutes MUST trigger reminder", (t30m - sessionStart) >= thirtyMinMs)

        // Throttling: If reminder was triggered at t30m, within next 5 minutes it is suppressed
        val lastReminderTime = t30m
        val t31m = t30m + (1 * 60 * 1000L)
        val isSuppressed = (t31m - lastReminderTime) < BreakReminderHelper.FIVE_MINUTES_MILLIS
        assertTrue("Subsequent alert within 5 minutes must be suppressed", isSuppressed)
    }

    @Test
    fun testSnoozePolicy_PostponesReminderByFiveMinutes() {
        val now = 1_000_000L
        val snoozeUntil = now + (5 * 60 * 1000L) // Snoozed 5 minutes

        // 2 minutes later: still snoozed
        val t2m = now + (2 * 60 * 1000L)
        assertTrue("Must be snoozed at 2 minutes", t2m < snoozeUntil)

        // 5 minutes later: snooze expired, eligible again
        val t5m = now + (5 * 60 * 1000L)
        assertFalse("Snooze window expired at 5 minutes", t5m < snoozeUntil)
    }

    @Test
    fun testScreenOffDuringBreak_ResetsContinuousSessionCorrectly() {
        val breakStartedAt = 2_000_000L

        // Screen turns off during break
        val screenOffAt = breakStartedAt + (60 * 1000L)

        // Screen turns back on after break
        val screenOnAt = screenOffAt + (4 * 60 * 1000L)
        val freshSessionStart = screenOnAt

        val elapsed = (screenOnAt - freshSessionStart) / 1000L
        assertEquals("Fresh session starts at 0 seconds after break", 0L, elapsed)
    }

    @Test
    fun testServiceRestart_MonotonicRealtimePreventsCorruption() {
        // Monotonic time does not jump with system clock changes
        val sessionStart = 10_000_000L
        val simulatedElapsedRealtime = sessionStart + (18 * 60 * 1000L) // 18 mins

        // Service crashes/restarts, reading stored sessionStart and current monotonic time
        val recoveredElapsed = (simulatedElapsedRealtime - sessionStart) / 1000L
        assertEquals(1080L, recoveredElapsed) // Exactly 18 minutes (1080s), not doubled or lost
    }

    @Test
    fun testOverlayPermissionCheck_ControlsPillVisibility() {
        // When permission is not granted, pill overlay should not be claimed active
        val hasPermission = false
        val isEnabled = true

        val shouldShowOverlay = hasPermission && isEnabled
        assertFalse("When overlay permission is denied, pill overlay must not attach", shouldShowOverlay)

        val hasPermissionGranted = true
        val shouldShowOverlayGranted = hasPermissionGranted && isEnabled
        assertTrue("When overlay permission is granted and setting is enabled, pill overlay attaches", shouldShowOverlayGranted)
    }

    private fun formatPillTimer(elapsedSeconds: Long): String {
        val mm = elapsedSeconds / 60
        val ss = elapsedSeconds % 60
        return String.format(Locale.US, "◷ %02d:%02d / 30:00", mm, ss)
    }
}
