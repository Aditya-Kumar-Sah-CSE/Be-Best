package com.focuslock.app.core.util

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FakeSharedPreferences : SharedPreferences {
    val data = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)

    override fun getString(key: String?, defValue: String?): String? =
        data[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (data[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        data[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        data[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        data[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        data[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(this)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) temp[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) {
                prefs.data.clear()
            }
            temp.forEach { (k, v) ->
                if (v == null) {
                    prefs.data.remove(k)
                } else {
                    prefs.data[k] = v
                }
            }
        }
    }
}

class TestWellnessClock(
    var elapsed: Long = 1_000_000L,
    var wall: Long = 1_700_000_000_000L
) : WellnessClock {
    override fun elapsedRealtime(): Long = elapsed
    override fun currentTimeMillis(): Long = wall

    fun advance(durationMs: Long) {
        elapsed += durationMs
        wall += durationMs
    }

    fun reboot(newElapsed: Long = 10_000L, wallDurationMs: Long = 60_000L) {
        elapsed = newElapsed
        wall += wallDurationMs
    }
}

class BreakReminderTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var testClock: TestWellnessClock

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        testClock = TestWellnessClock()
        BreakReminderHelper.preferencesOverride = fakePrefs
        BreakReminderHelper.clock = testClock
        BreakReminderHelper.isBreakAlertEnabledOverride = true
    }

    @After
    fun tearDown() {
        BreakReminderHelper.resetTestOverrides()
    }

    // ==========================================
    // 12 COMPREHENSIVE DETERMINISTIC TEST CASES
    // ==========================================

    @Test
    fun testCase1_ScreenOffFor1Minute_ResumesSession() {
        // Screen turns ON, user uses device for 10 minutes (600s)
        BreakReminderHelper.onScreenOn()
        testClock.advance(10 * 60 * 1000L)

        // Screen turns OFF at 10m
        BreakReminderHelper.onScreenOff()
        assertEquals(600_000L, BreakReminderHelper.getContinuousScreenUsageMillis())

        // Screen stays OFF for 1 minute (60s < 5m grace period)
        testClock.advance(1 * 60 * 1000L)

        // Screen turns back ON
        BreakReminderHelper.onScreenOn()

        // MUST resume with previous 10m preserved; 20m remaining (1200s)
        assertEquals(600_000L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(1200L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())
    }

    @Test
    fun testCase2_ScreenOffFor2Minutes_ResumesSession() {
        // Screen turns ON, user uses device for 20 minutes (1200s)
        BreakReminderHelper.onScreenOn()
        testClock.advance(20 * 60 * 1000L)

        // Screen turns OFF at 20m (remaining: 10m = 600s)
        BreakReminderHelper.onScreenOff()
        assertEquals(1200_000L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(600L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())

        // Screen stays OFF for 2 minutes (120s < 5m grace period)
        testClock.advance(2 * 60 * 1000L)

        // Screen turns back ON
        BreakReminderHelper.onScreenOn()

        // MUST resume: exact 20m preserved, 10m remaining (600s)
        assertEquals(1200_000L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(600L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())
    }

    @Test
    fun testCase3_ScreenOffFor4Minutes59Seconds_ResumesSession() {
        // Screen turns ON, user uses device for 15 minutes (900s)
        BreakReminderHelper.onScreenOn()
        testClock.advance(15 * 60 * 1000L)

        // Screen turns OFF
        BreakReminderHelper.onScreenOff()

        // Screen stays OFF for 4 minutes 59 seconds (299,000ms < 300,000ms boundary)
        testClock.advance((4 * 60 + 59) * 1000L)

        // Screen turns back ON before 5-minute grace period expires
        BreakReminderHelper.onScreenOn()

        // MUST resume: exact 15m preserved, 15m remaining (900s)
        assertEquals(900_000L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(900L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())
    }

    @Test
    fun testCase4_ScreenOffFor5Minutes_StartsNewSession() {
        // Screen turns ON, user uses device for 15 minutes
        BreakReminderHelper.onScreenOn()
        testClock.advance(15 * 60 * 1000L)

        // Screen turns OFF
        BreakReminderHelper.onScreenOff()

        // Screen stays OFF for exactly 5 minutes (300,000ms boundary)
        testClock.advance(5 * 60 * 1000L)

        // Screen turns ON: grace period has expired
        BreakReminderHelper.onScreenOn()

        // MUST start fresh session: 0 usage, full 30 minutes remaining (1800s)
        assertEquals(0L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(1800L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())
    }

    @Test
    fun testCase5_ScreenOffForMoreThan5Minutes_StartsNewSession() {
        // Screen turns ON, user uses device for 25 minutes
        BreakReminderHelper.onScreenOn()
        testClock.advance(25 * 60 * 1000L)

        // Screen turns OFF
        BreakReminderHelper.onScreenOff()

        // Screen stays OFF for 6 minutes (360,000ms > 300,000ms)
        testClock.advance(6 * 60 * 1000L)

        // Screen turns ON
        BreakReminderHelper.onScreenOn()

        // MUST start fresh session: 0 usage, full 30 minutes remaining (1800s)
        assertEquals(0L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(1800L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())
    }

    @Test
    fun testCase6_AppSwitching_TimerContinuesNonStop() {
        BreakReminderHelper.onScreenOn()

        // User on Instagram for 5 minutes
        testClock.advance(5 * 60 * 1000L)
        // User switches to YouTube (screen remains ON, idempotent onScreenOn)
        BreakReminderHelper.onScreenOn()
        testClock.advance(10 * 60 * 1000L)

        // User switches to Chrome (screen remains ON)
        BreakReminderHelper.onScreenOn()
        testClock.advance(5 * 60 * 1000L)

        // 20 minutes continuous screen time accumulated; 10 minutes remaining (600s)
        assertEquals(20 * 60 * 1000L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(600L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())
    }

    @Test
    fun testCase7_ProcessRecreationDuringGracePeriod_PreservesState() {
        // Screen turns ON, user uses phone for 18 minutes
        BreakReminderHelper.onScreenOn()
        testClock.advance(18 * 60 * 1000L)

        // Screen turns OFF at 18m
        BreakReminderHelper.onScreenOff()

        // Simulate process death / recreation:
        // In-memory static fields/jobs cleared, but SharedPreferences data is preserved
        BreakReminderHelper.resetTestOverrides()

        // Advance 2 minutes while process is dead & screen is off
        testClock.advance(2 * 60 * 1000L)

        // Process restarts: re-inject persisted preferences and clock
        BreakReminderHelper.preferencesOverride = fakePrefs
        BreakReminderHelper.clock = testClock
        BreakReminderHelper.isBreakAlertEnabledOverride = true

        // Screen turns back ON
        BreakReminderHelper.onScreenOn()

        // State restored safely from SharedPreferences: 18m accumulated, 12m (720s) remaining
        assertEquals(18 * 60 * 1000L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(720L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())
    }

    @Test
    fun testCase8_ServiceRestartDuringGracePeriod_MaintainsSession() {
        // Screen turns ON, user uses phone for 12 minutes
        BreakReminderHelper.onScreenOn()
        testClock.advance(12 * 60 * 1000L)

        // Screen turns OFF
        BreakReminderHelper.onScreenOff()

        // 1 minute into screen off, service restarts and queries usage
        testClock.advance(1 * 60 * 1000L)
        // During grace period while screen is off, usage is still 12 minutes
        assertEquals(12 * 60 * 1000L, BreakReminderHelper.getContinuousScreenUsageMillis())

        // 2 more minutes pass (total 3 minutes off < 5 minutes)
        testClock.advance(2 * 60 * 1000L)

        // Screen turns ON
        BreakReminderHelper.onScreenOn()

        // Session maintained: 12 minutes accumulated, 18 minutes remaining (1080s)
        assertEquals(12 * 60 * 1000L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(1080L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())
    }

    @Test
    fun testCase9_DeviceRebootDuringGracePeriod_RestoresSafely() {
        // Screen turns ON, user uses phone for 14 minutes
        BreakReminderHelper.onScreenOn()
        testClock.advance(14 * 60 * 1000L)

        // Screen turns OFF
        BreakReminderHelper.onScreenOff()

        // Device reboots during grace period!
        // Monotonic elapsed time resets to 5,000ms; wall clock elapsed is 2 minutes (120,000ms)
        testClock.reboot(newElapsed = 5_000L, wallDurationMs = 2 * 60 * 1000L)

        // Boot completed receiver triggers
        BreakReminderHelper.onDeviceReboot()

        // Screen turns ON after reboot
        BreakReminderHelper.onScreenOn()

        // Grace period restored safely across reboot: 14m preserved, 16m (960s) remaining
        assertEquals(14 * 60 * 1000L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(960L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())

        // Negative subcase: Screen OFF, but reboot completes 8 minutes later (> 5m)
        BreakReminderHelper.onScreenOff()
        testClock.reboot(newElapsed = 5_000L, wallDurationMs = 8 * 60 * 1000L)
        BreakReminderHelper.onDeviceReboot()
        BreakReminderHelper.onScreenOn()

        // Grace period expired across long reboot: starts fresh 30-min session
        assertEquals(0L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(1800L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())
    }

    @Test
    fun testCase10_ReminderTriggering_AccumulatedScreenOnOnly() {
        var alertTriggerCount = 0
        BreakReminderHelper.onAlarmTriggered = { alertTriggerCount++ }

        // Session 1: 15 minutes screen-on
        BreakReminderHelper.onScreenOn()
        testClock.advance(15 * 60 * 1000L)

        // Screen turns OFF for 3 minutes (grace period)
        BreakReminderHelper.onScreenOff()
        testClock.advance(3 * 60 * 1000L)

        // Screen turns ON again
        BreakReminderHelper.onScreenOn()

        // Use phone for 14 more minutes (total screen-on: 29 minutes)
        testClock.advance(14 * 60 * 1000L)

        // MUST NOT trigger at 29 minutes screen-on (even though total wall time = 32 minutes)
        val triggeredAt29 = BreakReminderHelper.checkAndTriggerReminder()
        assertFalse("3 minutes screen-off must NOT count toward 30m usage", triggeredAt29)
        assertEquals(0, alertTriggerCount)
        assertEquals(60L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())

        // 1 more minute screen-on (total screen-on: 30 minutes)
        testClock.advance(1 * 60 * 1000L)

        val triggeredAt30 = BreakReminderHelper.checkAndTriggerReminder()
        assertTrue("Exact 30 minutes of accumulated screen-on time MUST trigger alert", triggeredAt30)
        assertEquals(1, alertTriggerCount)
    }

    @Test
    fun testCase11_FloatingTimerSynchronization() {
        BreakReminderHelper.onScreenOn()

        // 0 min
        val s0 = (BreakReminderHelper.getContinuousScreenUsageMillis() / 1000L).coerceAtLeast(0L)
        val f0 = String.format(Locale.US, "◷ %02d:%02d / 30:00", s0 / 60, s0 % 60)
        assertEquals("◷ 00:00 / 30:00", f0)

        // 20 min
        testClock.advance(20 * 60 * 1000L)
        val s20 = (BreakReminderHelper.getContinuousScreenUsageMillis() / 1000L).coerceAtLeast(0L)
        val f20 = String.format(Locale.US, "◷ %02d:%02d / 30:00", s20 / 60, s20 % 60)
        assertEquals("◷ 20:00 / 30:00", f20)

        // 30 min
        testClock.advance(10 * 60 * 1000L)
        val s30 = (BreakReminderHelper.getContinuousScreenUsageMillis() / 1000L).coerceAtLeast(0L)
        val f30 = String.format(Locale.US, "◷ %02d:%02d / 30:00", s30 / 60, s30 % 60)
        assertEquals("◷ 30:00 / 30:00", f30)
    }

    @Test
    fun testCase12_NoDuplicateRemindersOrScheduledResetTasks() {
        var alertCount = 0
        BreakReminderHelper.onAlarmTriggered = { alertCount++ }

        // When screen turns off, pending reset task is scheduled
        BreakReminderHelper.onScreenOn()
        BreakReminderHelper.onScreenOff()
        assertTrue("Scheduled reset task must be pending while screen is off", BreakReminderHelper.hasPendingResetTask())

        // When screen turns on within grace period, pending reset task is cancelled
        testClock.advance(2 * 60 * 1000L)
        BreakReminderHelper.onScreenOn()
        assertFalse("Pending reset task must be cancelled when screen turns ON", BreakReminderHelper.hasPendingResetTask())

        // Advance to 30 minutes
        testClock.advance(30 * 60 * 1000L)
        val triggeredFirst = BreakReminderHelper.checkAndTriggerReminder()
        assertTrue(triggeredFirst)
        assertEquals(1, alertCount)

        // Immediate subsequent checks must be throttled / not duplicated
        val triggeredSecond = BreakReminderHelper.checkAndTriggerReminder()
        assertFalse("Duplicate alert must be suppressed for same session", triggeredSecond)
        assertEquals(1, alertCount)
    }

    // ==========================================
    // ACTION & UTILITY POLICY TESTS
    // ==========================================

    @Test
    fun testSnoozePolicy_PostponesNextReminder() {
        BreakReminderHelper.onScreenOn()
        testClock.advance(30 * 60 * 1000L)
        assertTrue(BreakReminderHelper.checkAndTriggerReminder())

        // Snooze for 5 minutes
        BreakReminderHelper.snooze(minutes = 5L)

        // 2 minutes later (still snoozed)
        testClock.advance(2 * 60 * 1000L)
        assertFalse("Reminder must remain silenced during snooze", BreakReminderHelper.checkAndTriggerReminder())

        // 6 minutes later (snooze expired)
        testClock.advance(4 * 60 * 1000L)
        assertTrue("Reminder re-alerts after snooze expires", BreakReminderHelper.checkAndTriggerReminder())
    }

    @Test
    fun testFiveMinuteBreakTimer_Calculation() {
        BreakReminderHelper.onScreenOn()
        testClock.advance(30 * 60 * 1000L)
        BreakReminderHelper.startBreak()

        assertTrue(BreakReminderHelper.isBreakActive())
        assertEquals(300L, BreakReminderHelper.getRemainingBreakSeconds())

        // 2.5 minutes into break
        testClock.advance(150 * 1000L)
        assertEquals(150L, BreakReminderHelper.getRemainingBreakSeconds())

        // 5 minutes completed
        testClock.advance(150 * 1000L)
        assertEquals(0L, BreakReminderHelper.getRemainingBreakSeconds())
        assertFalse(BreakReminderHelper.isBreakActive())
    }

    @Test
    fun testBreakContinuesAcrossScreenOff_RemainingTimeAccurateOnScreenOn() {
        // User reaches 30 minutes continuous usage and starts a 5-minute break
        BreakReminderHelper.onScreenOn()
        testClock.advance(30 * 60 * 1000L)
        BreakReminderHelper.startBreak()
        assertTrue(BreakReminderHelper.isBreakActive())
        assertEquals(300L, BreakReminderHelper.getRemainingBreakSeconds())

        // User locks phone / screen turns off after 1 minute (60s)
        testClock.advance(60 * 1000L)
        BreakReminderHelper.onScreenOff()

        // Break MUST NOT be cancelled by screen off!
        assertTrue("Break must remain active while screen is off", BreakReminderHelper.isBreakActive())

        // Screen remains off for 2 minutes (120s)
        testClock.advance(120 * 1000L)

        // User turns screen back on after total 3 minutes (180s) elapsed since break started
        BreakReminderHelper.onScreenOn()

        // MUST be active with ~2 minutes (120s) remaining, NOT reset back to 5 minutes!
        assertTrue(BreakReminderHelper.isBreakActive())
        assertEquals("Remaining time must reflect 120s after 3m of break elapsed", 120L, BreakReminderHelper.getRemainingBreakSeconds())
    }

    @Test
    fun testBreakCompletesWhileScreenOff_MarksCompletedOnScreenOn() {
        // Start 5-minute break
        BreakReminderHelper.onScreenOn()
        testClock.advance(30 * 60 * 1000L)
        BreakReminderHelper.startBreak()

        // Screen turns off at 1m into break
        testClock.advance(60 * 1000L)
        BreakReminderHelper.onScreenOff()

        // Screen remains off for 5 more minutes (total 6m elapsed > 5m break duration)
        testClock.advance(5 * 60 * 1000L)

        // User turns screen on after deadline
        BreakReminderHelper.onScreenOn()

        // Break must be completed and marked completed
        assertFalse("Break must no longer be active", BreakReminderHelper.isBreakActive())
        assertEquals(0L, BreakReminderHelper.getRemainingBreakSeconds())
        assertTrue("Break must be marked completed", BreakReminderHelper.isBreakCompleted())
    }

    @Test
    fun testBreakRecoveryAcrossReboot() {
        // Start 5-minute break
        BreakReminderHelper.onScreenOn()
        testClock.advance(30 * 60 * 1000L)
        BreakReminderHelper.startBreak()

        // 2 minutes into break, device reboots (elapsed resets, wall clock advances 2m)
        testClock.reboot(newElapsed = 5_000L, wallDurationMs = 120_000L)
        BreakReminderHelper.onDeviceReboot()

        // Break should be restored using wall-clock diff
        assertTrue("Active break must be restored across reboot", BreakReminderHelper.isBreakActive())
        assertEquals("Remaining break time must be ~180s (3m) after 2m reboot", 180L, BreakReminderHelper.getRemainingBreakSeconds())

        // Another reboot 4 minutes later (total 6m > 5m)
        testClock.reboot(newElapsed = 5_000L, wallDurationMs = 240_000L)
        BreakReminderHelper.onDeviceReboot()

        // Break must now be completed
        assertFalse("Break past deadline must not remain active after reboot", BreakReminderHelper.isBreakActive())
        assertTrue("Break must be marked completed after deadline reboot", BreakReminderHelper.isBreakCompleted())
    }

    @Test
    fun testBreakCompletion_IsIdempotent_CompletesOnce() {
        BreakReminderHelper.startBreak()
        assertFalse(BreakReminderHelper.isBreakCompleted())

        // First completion
        BreakReminderHelper.completeBreak()
        assertTrue(BreakReminderHelper.isBreakCompleted())
        assertFalse(BreakReminderHelper.isBreakActive())

        // Second completion call must be a safe no-op
        BreakReminderHelper.completeBreak()
        assertTrue(BreakReminderHelper.isBreakCompleted())
    }

    @Test
    fun testSeparateWellnessTimer_UnaffectedByBreak() {
        // Continuous screen session: 25 minutes
        BreakReminderHelper.onScreenOn()
        testClock.advance(25 * 60 * 1000L)
        assertEquals(25 * 60 * 1000L, BreakReminderHelper.getContinuousScreenUsageMillis())

        // Screen turns off for 2 minutes (within 5m grace period)
        BreakReminderHelper.onScreenOff()
        testClock.advance(2 * 60 * 1000L)
        BreakReminderHelper.onScreenOn()

        // Session preserved at 25 minutes
        assertEquals(25 * 60 * 1000L, BreakReminderHelper.getContinuousScreenUsageMillis())
        assertEquals(300L, BreakReminderHelper.getRemainingSecondsToThirtyMinutes())
    }
}
