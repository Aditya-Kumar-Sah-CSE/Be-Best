package com.focuslock.app.service

import com.focuslock.app.domain.engine.FakePermissionChecker
import com.focuslock.app.domain.engine.FakeProtectedAppDao
import com.focuslock.app.domain.engine.FakeTimeProvider
import com.focuslock.app.domain.engine.FakeUsageDao
import com.focuslock.app.domain.engine.EmergencyStartResult
import com.focuslock.app.domain.engine.ProtectionState
import com.focuslock.app.domain.engine.QuotaEngine
import com.focuslock.app.data.local.entity.ProtectedAppEntity
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
}
