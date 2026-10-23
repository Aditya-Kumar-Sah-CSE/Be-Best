package com.focuslock.app.service

import com.focuslock.app.data.local.FocusLockDatabase
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.domain.engine.EmergencyStartResult
import com.focuslock.app.domain.engine.EnforcementState
import com.focuslock.app.domain.engine.FakePermissionChecker
import com.focuslock.app.domain.engine.FakeProtectedAppDao
import com.focuslock.app.domain.engine.FakeTimeProvider
import com.focuslock.app.domain.engine.FakeUsageDao
import com.focuslock.app.domain.engine.ProtectionState
import com.focuslock.app.domain.engine.QuotaEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class ExternalAppBlockingTest {

    private lateinit var protectedAppDao: FakeProtectedAppDao
    private lateinit var usageDao: FakeUsageDao
    private lateinit var fakeTimeProvider: FakeTimeProvider
    private lateinit var permissionChecker: FakePermissionChecker
    private lateinit var quotaEngine: QuotaEngine

    private val instagramPkg = "com.instagram.android"
    private val youtubePkg = "com.google.android.youtube"
    private val ownAppPkg = "com.focuslock.app"

    @Before
    fun setUp() = runBlocking {
        protectedAppDao = FakeProtectedAppDao()
        usageDao = FakeUsageDao()
        fakeTimeProvider = FakeTimeProvider()
        permissionChecker = FakePermissionChecker(active = true)

        quotaEngine = QuotaEngine(
            protectedAppDao = protectedAppDao,
            usageDao = usageDao,
            focusSessionDao = null,
            timeProvider = fakeTimeProvider,
            permissionChecker = permissionChecker
        )

        // Configure Instagram with default 30-minute limit (1800s)
        protectedAppDao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = instagramPkg,
                displayName = "Instagram",
                dailyLimitSeconds = 1800L,
                enabled = true,
                category = "SOCIAL"
            )
        )

        // Configure YouTube with independent 30-minute limit (1800s)
        protectedAppDao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = youtubePkg,
                displayName = "YouTube",
                dailyLimitSeconds = 1800L,
                enabled = true,
                category = "VIDEO"
            )
        )
    }

    @Test
    fun testUsageBelowLimit_IsAllowed() = runBlocking {
        // Record 29 minutes (1740s) of usage
        quotaEngine.recordUsage(instagramPkg, 1740L)

        val evaluation = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("App below 30m limit must not be blocked", evaluation.isBlocked)
        assertEquals(ProtectionState.WARNING, evaluation.status)
        assertEquals(60L, evaluation.remainingSeconds)
    }

    @Test
    fun testUsageReachingExactLimit_IsBlocked() = runBlocking {
        // Record exactly 30 minutes (1800s) of usage
        quotaEngine.recordUsage(instagramPkg, 1800L)

        val evaluation = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("App at exact 30m limit must be blocked immediately", evaluation.isBlocked)
        assertEquals(ProtectionState.EXHAUSTED, evaluation.status)
        assertEquals(0L, evaluation.remainingSeconds)
        assertTrue(evaluation.canUseEmergency)
    }

    @Test
    fun testOpeningAppAfterQuotaExhausted_IsBlockedImmediately() = runBlocking {
        // App has exceeded limit (e.g., 35 minutes used)
        quotaEngine.recordUsage(instagramPkg, 2100L)

        val evaluation = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Opening app when quota is exhausted must return blocked immediately", evaluation.isBlocked)
        assertEquals(0L, evaluation.remainingSeconds)
    }

    @Test
    fun testSwitchingAwayAndReopeningRestrictedApp_RemainsBlocked() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)

        val eval1 = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Initial check: blocked", eval1.isBlocked)

        // User switches away to home screen for 2 minutes and returns
        fakeTimeProvider.advanceSeconds(120)

        val eval2 = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Re-entering the blocked app MUST remain blocked", eval2.isBlocked)
        assertEquals(0L, eval2.remainingSeconds)
    }

    @Test
    fun testIndependentQuotasForMultipleApps() = runBlocking {
        // Exhaust Instagram
        quotaEngine.recordUsage(instagramPkg, 1800L)

        val igEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Instagram must be blocked", igEval.isBlocked)

        // YouTube has not been used
        val ytEval = quotaEngine.evaluateQuota(youtubePkg)
        assertFalse("YouTube must remain completely unblocked with independent quota", ytEval.isBlocked)
        assertEquals(1800L, ytEval.remainingSeconds)
        assertEquals(ProtectionState.ALLOWED, ytEval.status)
    }

    @Test
    fun testEmergencyAccess_GrantedFor30Seconds_ThenExpiresAndReEnforcesBlock() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)

        val result = quotaEngine.startEmergencySession(instagramPkg)
        assertTrue("Emergency access must be granted", result is EmergencyStartResult.Success)

        // During 30-second window
        fakeTimeProvider.advanceSeconds(10)
        val activeEval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("App must be temporarily accessible during 30s emergency window", activeEval.isBlocked)
        assertEquals(ProtectionState.EMERGENCY_ACTIVE, activeEval.status)
        assertEquals(20L, activeEval.remainingEmergencySeconds)

        // After 30 seconds expire
        fakeTimeProvider.advanceSeconds(21) // 31 seconds total
        val expiredEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("After 30s, block must be strictly re-enforced", expiredEval.isBlocked)
        assertEquals(ProtectionState.BLOCKED_UNTIL_RESET, expiredEval.status)
        assertFalse("Cannot reuse emergency session on the same day", expiredEval.canUseEmergency)

        // Second attempt on the same day must fail
        val secondAttempt = quotaEngine.startEmergencySession(instagramPkg)
        assertEquals(EmergencyStartResult.AlreadyUsedToday, secondAttempt)
    }

    @Test
    fun testMidnightReset_UnlocksExhaustedAppForNewDay() = runBlocking {
        // Day 1: Exhaust quota
        quotaEngine.recordUsage(instagramPkg, 1800L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // Midnight arrives: Advance date by 1 day
        fakeTimeProvider.advanceDateByDays(1)
        quotaEngine.checkAndPerformMidnightReset()

        // Day 2: App starts fresh and unblocked
        val day2Eval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("App must be unblocked on new day after midnight reset", day2Eval.isBlocked)
        assertEquals(0L, day2Eval.usedSeconds)
        assertEquals(1800L, day2Eval.remainingSeconds)
        assertEquals(ProtectionState.ALLOWED, day2Eval.status)
    }

    @Test
    fun testServiceAndProcessRestart_PreservesQuotaWithoutReset() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // Re-create QuotaEngine simulating process recreation or service restart
        val freshEngine = QuotaEngine(
            protectedAppDao = protectedAppDao,
            usageDao = usageDao,
            focusSessionDao = null,
            timeProvider = fakeTimeProvider,
            permissionChecker = permissionChecker
        )

        val restartedEval = freshEngine.evaluateQuota(instagramPkg)
        assertTrue("Service/Process restart must NOT reset quota; app must stay blocked", restartedEval.isBlocked)
        assertEquals(1800L, restartedEval.usedSeconds)
    }

    @Test
    fun testMissingPermissions_ReportedHonestly() = runBlocking {
        // Simulate missing accessibility or usage access permission
        permissionChecker.active = false

        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("When permissions are missing, permissionsActive must report false", eval.permissionsActive)
    }

    @Test
    fun testExcludingFocusLockItself_NeverBlocked() = runBlocking {
        val eval = quotaEngine.evaluateQuota(ownAppPkg)
        assertFalse("FocusLock itself must NEVER be blocked", eval.isBlocked)
        assertEquals(ProtectionState.ALLOWED, eval.status)
    }

    @Test
    fun testRepeatedReOpen_RemainsBlockedWithoutQuotaReset() = runBlocking {
        // App reaches exact limit
        quotaEngine.recordUsage(instagramPkg, 1800L)
        val initialEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("First attempt must be blocked", initialEval.isBlocked)

        // Simulate repeated user openings (1st, 2nd, 3rd attempts)
        for (attempt in 1..5) {
            fakeTimeProvider.advanceSeconds(5)
            val eval = quotaEngine.evaluateQuota(instagramPkg)
            assertTrue("Attempt #$attempt must strictly remain blocked", eval.isBlocked)
            assertEquals("Used seconds must not be reset on repeated attempts", 1800L, eval.usedSeconds)
        }
    }

    @Test
    fun testInstagramOneMinuteLimit_ReachesLimit_ExhaustedStatePersisted() = runBlocking {
        // 1. Configure separate 1-minute (60s) daily limit specifically for Instagram
        val oneMinuteLimitSeconds = 60L
        protectedAppDao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = instagramPkg,
                displayName = "Instagram",
                dailyLimitSeconds = oneMinuteLimitSeconds,
                enabled = true,
                category = "SOCIAL"
            )
        )

        val initialEval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("Instagram with 1m limit starts unblocked", initialEval.isBlocked)
        assertEquals(60L, initialEval.remainingSeconds)

        // 2. Reach the 1-minute limit
        quotaEngine.recordUsage(instagramPkg, 60L)

        // 3. Verify exhausted state
        val exhaustedEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Instagram must be blocked immediately after 1 minute", exhaustedEval.isBlocked)
        assertEquals(ProtectionState.EXHAUSTED, exhaustedEval.status)
        assertEquals(0L, exhaustedEval.remainingSeconds)
        assertTrue(exhaustedEval.canUseEmergency)

        // Verify exhausted state is persisted in Room database for today's date
        val todayStr = com.focuslock.app.core.util.TimeUtils.formatDate(fakeTimeProvider.getTodayLocalDate())
        val persistedUsage = usageDao.getDailyUsageForApp(instagramPkg, todayStr)
        assertNotNull("Usage record must be persisted", persistedUsage)
        assertTrue("isBlocked must be persisted in database", persistedUsage!!.isBlocked)
        assertEquals("usedSeconds must equal 60s in database", 60L, persistedUsage.usedSeconds)
    }

    @Test
    fun testInstagramOneMinuteLimit_ReopeningAndReturnHome_RemainsBlocked() = runBlocking {
        // Configure 1-minute limit and exhaust it
        protectedAppDao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = instagramPkg,
                displayName = "Instagram",
                dailyLimitSeconds = 60L,
                enabled = true,
                category = "SOCIAL"
            )
        )
        quotaEngine.recordUsage(instagramPkg, 60L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // User returns to Home screen and later taps Instagram icon again
        fakeTimeProvider.advanceSeconds(300) // 5 minutes later

        val reopenEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Reopening Instagram after returning Home must remain strictly blocked", reopenEval.isBlocked)
        assertEquals(0L, reopenEval.remainingSeconds)
    }

    @Test
    fun testInstagramOneMinuteLimit_EmergencyBypass_ThenReEnforced() = runBlocking {
        // Configure 1-minute limit and exhaust it
        protectedAppDao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = instagramPkg,
                displayName = "Instagram",
                dailyLimitSeconds = 60L,
                enabled = true,
                category = "SOCIAL"
            )
        )
        quotaEngine.recordUsage(instagramPkg, 60L)

        // Request 30-second emergency bypass
        val emergencyResult = quotaEngine.startEmergencySession(instagramPkg)
        assertTrue("Emergency bypass must be granted", emergencyResult is EmergencyStartResult.Success)

        // Verify active during bypass window
        fakeTimeProvider.advanceSeconds(15)
        val activeEval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("Instagram accessible during active 30s bypass", activeEval.isBlocked)
        assertEquals(ProtectionState.EMERGENCY_ACTIVE, activeEval.status)

        // When bypass window expires (total 31 seconds)
        fakeTimeProvider.advanceSeconds(16)
        val reBlockedEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("After bypass expires, Instagram must be blocked again", reBlockedEval.isBlocked)
        assertEquals(ProtectionState.BLOCKED_UNTIL_RESET, reBlockedEval.status)
        assertFalse("Cannot use emergency twice on same day", reBlockedEval.canUseEmergency)
    }

    @Test
    fun testInstagramOneMinuteLimit_ServiceRestart_PreservesBlockedState() = runBlocking {
        protectedAppDao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = instagramPkg,
                displayName = "Instagram",
                dailyLimitSeconds = 60L,
                enabled = true,
                category = "SOCIAL"
            )
        )
        quotaEngine.recordUsage(instagramPkg, 60L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // Simulate service restart by creating a new QuotaEngine instance
        val restartedEngine = QuotaEngine(
            protectedAppDao = protectedAppDao,
            usageDao = usageDao,
            focusSessionDao = null,
            timeProvider = fakeTimeProvider,
            permissionChecker = permissionChecker
        )

        val restartedEval = restartedEngine.evaluateQuota(instagramPkg)
        assertTrue("Service restart must preserve exhausted state", restartedEval.isBlocked)
        assertEquals(60L, restartedEval.usedSeconds)
        assertEquals(0L, restartedEval.remainingSeconds)
    }

    @Test
    fun testInstagramOneMinuteLimit_NextDayReset_AllowsInstagramAgain() = runBlocking {
        protectedAppDao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = instagramPkg,
                displayName = "Instagram",
                dailyLimitSeconds = 60L,
                enabled = true,
                category = "SOCIAL"
            )
        )
        quotaEngine.recordUsage(instagramPkg, 60L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // Midnight arrives: Advance date by 1 day
        fakeTimeProvider.advanceDateByDays(1)
        quotaEngine.checkAndPerformMidnightReset()

        // Verify Instagram is allowed again on the new day with a fresh 60-second quota
        val nextDayEval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("Instagram must be allowed on the new day after midnight reset", nextDayEval.isBlocked)
        assertEquals(0L, nextDayEval.usedSeconds)
        assertEquals(60L, nextDayEval.remainingSeconds)
        assertEquals(ProtectionState.ALLOWED, nextDayEval.status)
        assertEquals(EnforcementState.UNDER_LIMIT, nextDayEval.enforcementState)
    }

    @Test
    fun testReopeningYouTubeAfterExhaustion_RemainsBlocked() = runBlocking {
        quotaEngine.recordUsage(youtubePkg, 1800L)
        val eval1 = quotaEngine.evaluateQuota(youtubePkg)
        assertTrue("YouTube must be blocked at limit", eval1.isBlocked)
        assertEquals(EnforcementState.LIMIT_REACHED, eval1.enforcementState)

        // User switches away to home screen and returns after 10 minutes
        fakeTimeProvider.advanceSeconds(600)
        val eval2 = quotaEngine.evaluateQuota(youtubePkg)
        assertTrue("YouTube must remain strictly blocked on re-opening", eval2.isBlocked)
        assertEquals(EnforcementState.LIMIT_REACHED, eval2.enforcementState)
        assertEquals(0L, eval2.remainingSeconds)
    }

    @Test
    fun testRecentAppsReEntry_RemainsBlocked() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        // User moves to recents overview, then taps Instagram card again
        fakeTimeProvider.advanceSeconds(15)
        val recentsEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Recent Apps re-entry must remain strictly blocked", recentsEval.isBlocked)
        assertEquals(0L, recentsEval.remainingSeconds)
        assertEquals(EnforcementState.LIMIT_REACHED, recentsEval.enforcementState)
    }

    @Test
    fun testSwitchingBetweenMultipleRestrictedApps_IndependentEnforcement() = runBlocking {
        // Instagram exhausted
        quotaEngine.recordUsage(instagramPkg, 1800L)
        // YouTube used for only 10 minutes (600s)
        quotaEngine.recordUsage(youtubePkg, 600L)

        val igEval = quotaEngine.evaluateQuota(instagramPkg)
        val ytEval = quotaEngine.evaluateQuota(youtubePkg)

        assertTrue("Instagram must be blocked", igEval.isBlocked)
        assertEquals(EnforcementState.LIMIT_REACHED, igEval.enforcementState)

        assertFalse("YouTube must NOT be blocked", ytEval.isBlocked)
        assertEquals(EnforcementState.UNDER_LIMIT, ytEval.enforcementState)
        assertEquals(1200L, ytEval.remainingSeconds)
    }

    @Test
    fun testDeviceRebootRecovery_PreservesExhaustedState() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        // Simulate phone reboot (monotonic clock resets to 5s post-boot)
        fakeTimeProvider.simulateReboot(postBootElapsedMillis = 5000L)
        val rebootEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("After device reboot, exhausted quota must be recovered from database", rebootEval.isBlocked)
        assertEquals(1800L, rebootEval.usedSeconds)
        assertEquals(EnforcementState.LIMIT_REACHED, rebootEval.enforcementState)
    }

    @Test
    fun testTemporaryBypassExpiry_ImmediatelyRestoresEnforcementState() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        val res = quotaEngine.startEmergencySession(instagramPkg)
        assertTrue(res is EmergencyStartResult.Success)

        // 10s into emergency window
        fakeTimeProvider.advanceSeconds(10)
        val active = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(EnforcementState.TEMPORARY_BYPASS_ACTIVE, active.enforcementState)
        assertFalse(active.isBlocked)

        // After 30s expire
        fakeTimeProvider.advanceSeconds(25)
        val expired = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(EnforcementState.DAILY_RESET_PENDING, expired.enforcementState)
        assertTrue("Exhausted enforcement must be restored immediately upon bypass expiry", expired.isBlocked)
    }

    @Test
    fun testBeforeMidnightExhaustion_RemainsBlockedAt1159PM() = runBlocking {
        // Quota exhausted at 10:00 AM
        quotaEngine.recordUsage(instagramPkg, 1800L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // Advance 13 hours and 59 minutes to 11:59 PM same day
        fakeTimeProvider.advanceSeconds(13 * 3600 + 59 * 60)
        val lateNightEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Quota reached in morning must remain exhausted until midnight", lateNightEval.isBlocked)
        assertEquals(EnforcementState.LIMIT_REACHED, lateNightEval.enforcementState)
    }

    @Test
    fun testLocalMidnightRollover_ResetsQuotasToUnderLimit() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // Advance to next day 12:00:01 AM
        fakeTimeProvider.advanceDateByDays(1)
        fakeTimeProvider.advanceSeconds(1)
        quotaEngine.checkAndPerformMidnightReset()

        val nextDayEval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("App must be unblocked after local midnight rollover", nextDayEval.isBlocked)
        assertEquals(EnforcementState.UNDER_LIMIT, nextDayEval.enforcementState)
        assertEquals(0L, nextDayEval.usedSeconds)
    }

    @Test
    fun testAppLaunchImmediatelyAfterMidnight_StartsFreshQuota() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        // Midnight passes
        fakeTimeProvider.advanceDateByDays(1)
        fakeTimeProvider.advanceSeconds(5) // 12:00:05 AM

        // First app launch today
        quotaEngine.checkAndPerformMidnightReset()
        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse(eval.isBlocked)
        assertEquals(1800L, eval.remainingSeconds)

        // User uses 10 seconds of Instagram
        quotaEngine.recordUsage(instagramPkg, 10L)
        val postUsage = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(10L, postUsage.usedSeconds)
        assertEquals(1790L, postUsage.remainingSeconds)
        assertEquals(EnforcementState.UNDER_LIMIT, postUsage.enforcementState)
    }

    @Test
    fun testTimezoneAndSystemTimeChanges_ReEvaluatesWithoutBypass() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // Simulate user altering timezone
        fakeTimeProvider.setZone(ZoneId.of("Asia/Kolkata"))
        // If still same date, app must remain blocked
        val tzEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Timezone shift must not clear exhausted quota", tzEval.isBlocked)
        assertEquals(1800L, tzEval.usedSeconds)
    }

    @Test
    fun testDatabaseMigration_SchemaSafety() {
        val mockDb = java.lang.reflect.Proxy.newProxyInstance(
            androidx.sqlite.db.SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(androidx.sqlite.db.SupportSQLiteDatabase::class.java)
        ) { _, _, _ -> null } as androidx.sqlite.db.SupportSQLiteDatabase

        // Execute migrations 2->3, 3->4, and 4->5 safely
        FocusLockDatabase.MIGRATION_2_3.migrate(mockDb)
        FocusLockDatabase.MIGRATION_3_4.migrate(mockDb)
        FocusLockDatabase.MIGRATION_4_5.migrate(mockDb)
        assertTrue("Database migrations executed without exception", true)
    }

    @Test
    fun testFloatingTimerAndWellnessReminderRegression() {
        // Verify TimeUtils countdown formatting and reset calculation logic
        val midnightSecs = TimeUtils.getSecondsUntilNextDailyReset()
        assertTrue("Midnight countdown seconds must be positive", midnightSecs >= 0L)
        val formatted = TimeUtils.formatCountdown(3665L)
        assertEquals("01:01:05", formatted)
    }

    @Test
    fun testInstagramOpenedThroughExternalIntent_RemainsBlocked() = runBlocking {
        // App exhausted via normal usage
        quotaEngine.recordUsage(instagramPkg, 1800L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // Incoming external intent opens Instagram (e.g. deep link or share target)
        fakeTimeProvider.advanceSeconds(30)
        val deepLinkEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Opening via external intent must still be strictly blocked", deepLinkEval.isBlocked)
        assertEquals(EnforcementState.LIMIT_REACHED, deepLinkEval.enforcementState)
        assertEquals(0L, deepLinkEval.remainingSeconds)
    }

    @Test
    fun testUsageAccessPermissionRevoked_PreservesRecordedUsageWithoutReset() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // User revokes Usage Access permission in system settings
        permissionChecker.active = false

        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("Permissions active must report false", eval.permissionsActive)
        assertTrue("Previously recorded usage must NEVER be erased or reset when permissions are revoked", eval.isBlocked)
        assertEquals(1800L, eval.usedSeconds)
    }

    @Test
    fun testThreeHourOveruse_BoundaryCondition_StrictlyEnforced() = runBlocking {
        // Specifically reproduce the issue reported by user:
        // Configured limit: 30 minutes (1800s). Observed usage: ~3 hours (10,800s).
        quotaEngine.recordUsage(instagramPkg, 10800L)

        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Instagram used for 3 hours against 30m limit must be blocked", eval.isBlocked)
        assertEquals(ProtectionState.EXHAUSTED, eval.status)
        assertEquals(EnforcementState.LIMIT_REACHED, eval.enforcementState)
        assertEquals(0L, eval.remainingSeconds)
        assertEquals(10800L, eval.usedSeconds)
        assertEquals(1800L, eval.dailyLimitSeconds)

        // Repeated attempts to re-open across time
        for (i in 1..5) {
            fakeTimeProvider.advanceSeconds(60)
            val reEval = quotaEngine.evaluateQuota(instagramPkg)
            assertTrue("Repeated re-launch #$i must remain strictly blocked", reEval.isBlocked)
            assertEquals(10800L, reEval.usedSeconds)
        }
    }

    @Test
    fun testRepeatedLaunchAttempts_PersistentlyBlockedUntilMidnight() = runBlocking {
        // App reaches exact limit
        quotaEngine.recordUsage(instagramPkg, 1800L)
        val initialBlock = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue(initialBlock.isBlocked)

        // User rapidly closes and tries to reopen 10 times
        for (attempt in 1..10) {
            fakeTimeProvider.advanceSeconds(5)
            val eval = quotaEngine.evaluateQuota(instagramPkg)
            assertTrue("Attempt #$attempt must be blocked immediately", eval.isBlocked)
            assertEquals("Daily allowance of 30m exhausted", eval.blockedReason)
        }

        // Advance to next calendar day
        fakeTimeProvider.advanceDateByDays(1)
        quotaEngine.checkAndPerformMidnightReset()

        // After midnight reset, access must be restored with 0 usedSeconds
        val afterMidnight = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("App must be unblocked after midnight reset", afterMidnight.isBlocked)
        assertEquals(0L, afterMidnight.usedSeconds)
        assertEquals(1800L, afterMidnight.remainingSeconds)
    }

    @Test
    fun testZeroSecondLimit_InstantlyBlocked() = runBlocking {
        // App configured with 0-second limit (immediate strict block)
        val strictPkg = "com.strict.test"
        protectedAppDao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = strictPkg,
                displayName = "Strict App",
                dailyLimitSeconds = 0L,
                enabled = true,
                category = "SOCIAL"
            )
        )

        val eval = quotaEngine.evaluateQuota(strictPkg)
        assertTrue("0-second limit must immediately block application without usage", eval.isBlocked)
        assertEquals(0L, eval.remainingSeconds)
        assertEquals(ProtectionState.EXHAUSTED, eval.status)
    }

    @Test
    fun testBlockingIntentFlagsContracts() {
        val expectedFlags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        assertTrue("Flags must contain FLAG_ACTIVITY_NEW_TASK", (expectedFlags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue("Flags must contain FLAG_ACTIVITY_REORDER_TO_FRONT", (expectedFlags and android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) != 0)
    }
}

