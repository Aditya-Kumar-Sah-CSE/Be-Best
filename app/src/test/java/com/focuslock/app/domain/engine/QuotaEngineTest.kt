package com.focuslock.app.domain.engine

import com.focuslock.app.core.util.TimeProvider
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.dao.ProtectedAppDao
import com.focuslock.app.data.local.dao.UsageDao
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.data.local.entity.ProtectionEventEntity
import com.focuslock.app.data.local.entity.UsageSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Deterministic Fake TimeProvider allowing controlled time travel in tests.
 */
class FakeTimeProvider(
    private var currentDate: LocalDate = LocalDate.of(2026, 10, 21),
    private var zoneId: ZoneId = ZoneId.of("UTC"),
    private var currentMillis: Long = 1761048000000L, // 12:00:00 UTC
    private var currentElapsedMillis: Long = 100_000L
) : TimeProvider {

    fun advanceSeconds(seconds: Long) {
        currentMillis += seconds * 1000L
        currentElapsedMillis += seconds * 1000L
    }

    fun advanceMillis(millis: Long) {
        currentMillis += millis
        currentElapsedMillis += millis
    }

    fun advanceDateByDays(days: Long) {
        currentDate = currentDate.plusDays(days)
        currentMillis += days * 86400000L
        currentElapsedMillis += days * 86400000L
    }

    fun simulateReboot(postBootElapsedMillis: Long = 5000L) {
        currentMillis += 10000L
        currentElapsedMillis = postBootElapsedMillis
    }

    fun setZone(newZone: ZoneId) {
        zoneId = newZone
    }

    override fun currentTimeMillis(): Long = currentMillis

    override fun elapsedRealtime(): Long = currentElapsedMillis

    override fun getZoneId(): ZoneId = zoneId

    override fun getTodayLocalDate(): LocalDate = currentDate

    override fun getStartOfDayMillis(): Long =
        currentDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

    override fun getNextMidnightMillis(): Long =
        currentDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
}

/**
 * Fake PermissionChecker for deterministic permission state tests.
 */
class FakePermissionChecker(
    var active: Boolean = true
) : PermissionChecker {
    override fun areEnforcementPermissionsActive(): Boolean = active
}

/**
 * In-memory Fake DAOs for deterministic unit testing.
 */
class FakeProtectedAppDao : ProtectedAppDao {
    private val apps = mutableMapOf<String, ProtectedAppEntity>()

    override fun getAllProtectedAppsFlow(): Flow<List<ProtectedAppEntity>> = flowOf(apps.values.toList())

    override suspend fun getActiveProtectedApps(): List<ProtectedAppEntity> =
        apps.values.filter { it.enabled }

    override suspend fun getAppByPackageName(packageName: String): ProtectedAppEntity? = apps[packageName]

    override suspend fun insertOrUpdate(app: ProtectedAppEntity) {
        apps[app.packageName] = app
    }

    override suspend fun insertAll(appsList: List<ProtectedAppEntity>) {
        appsList.forEach { apps[it.packageName] = it }
    }

    override suspend fun update(app: ProtectedAppEntity) {
        apps[app.packageName] = app
    }

    override suspend fun delete(app: ProtectedAppEntity) {
        apps.remove(app.packageName)
    }

    override suspend fun updateLimit(packageName: String, limitSeconds: Long) {
        apps[packageName]?.let {
            apps[packageName] = it.copy(dailyLimitSeconds = limitSeconds)
        }
    }
}

class FakeUsageDao : UsageDao {
    private val usageMap = mutableMapOf<String, DailyUsageEntity>()

    private fun key(pkg: String, date: String) = "$pkg#$date"

    override fun getDailyUsageForDateFlow(date: String): Flow<List<DailyUsageEntity>> =
        flowOf(usageMap.values.filter { it.localDate == date })

    override suspend fun getDailyUsageForApp(packageName: String, date: String): DailyUsageEntity? =
        usageMap[key(packageName, date)]

    override suspend fun insertOrUpdateDailyUsage(dailyUsage: DailyUsageEntity) {
        usageMap[key(dailyUsage.packageName, dailyUsage.localDate)] = dailyUsage
    }

    override suspend fun incrementUsage(packageName: String, date: String, additionalSeconds: Long, timestamp: Long) {
        val existing = getDailyUsageForApp(packageName, date)
        if (existing != null) {
            val newUsed = existing.usedSeconds + additionalSeconds
            usageMap[key(packageName, date)] = existing.copy(
                usedSeconds = newUsed,
                isBlocked = newUsed >= existing.quotaSeconds,
                updatedAt = timestamp
            )
        }
    }

    override suspend fun recordEmergencySession(
        packageName: String,
        date: String,
        startedAt: Long,
        expiresAt: Long,
        elapsedStart: Long,
        now: Long
    ) {
        val existing = getDailyUsageForApp(packageName, date)
        if (existing != null) {
            usageMap[key(packageName, date)] = existing.copy(
                emergencyUsed = true,
                emergencyStartedAt = startedAt,
                emergencyExpiresAt = expiresAt,
                emergencyElapsedRealtimeAtStart = elapsedStart,
                updatedAt = now
            )
        }
    }

    override suspend fun insertUsageSession(session: UsageSessionEntity) {}

    override suspend fun insertProtectionEvent(event: ProtectionEventEntity) {}

    override fun getRecentProtectionEventsFlow(): Flow<List<ProtectionEventEntity>> = flowOf(emptyList())

    override suspend fun getTotalUsedSecondsForDate(date: String): Long =
        usageMap.values.filter { it.localDate == date }.sumOf { it.usedSeconds }

    override suspend fun getBlockedAppsCount(date: String): Int =
        usageMap.values.count { it.localDate == date && it.isBlocked }
}

class QuotaEngineTest {

    private lateinit var protectedAppDao: FakeProtectedAppDao
    private lateinit var usageDao: FakeUsageDao
    private lateinit var fakeTimeProvider: FakeTimeProvider
    private lateinit var permissionChecker: FakePermissionChecker
    private lateinit var quotaEngine: QuotaEngine

    private val instagramPkg = "com.instagram.android"
    private val youtubePkg = "com.google.android.youtube"

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

        // Set up Instagram with 30m limit (1800s) and YouTube with 30m limit (1800s)
        protectedAppDao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = instagramPkg,
                displayName = "Instagram",
                dailyLimitSeconds = 1800L,
                enabled = true,
                category = "SOCIAL"
            )
        )
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

    // 1. App remains allowed below its limit.
    @Test
    fun test1_appRemainsAllowedBelowLimit() = runBlocking {
        // 10 minutes used of 30 minutes
        quotaEngine.recordUsage(instagramPkg, 600L)

        val evaluation = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(ProtectionState.ALLOWED, evaluation.status)
        assertFalse(evaluation.isBlocked)
        assertEquals(1800L, evaluation.dailyLimitSeconds)
        assertEquals(600L, evaluation.usedSeconds)
        assertEquals(1200L, evaluation.remainingSeconds)
    }

    // 2. App becomes blocked at the exact limit.
    @Test
    fun test2_appBecomesBlockedAtExactLimit() = runBlocking {
        // Exactly 30 minutes used (1800s)
        quotaEngine.recordUsage(instagramPkg, 1800L)

        val evaluation = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(ProtectionState.EXHAUSTED, evaluation.status)
        assertTrue(evaluation.isBlocked)
        assertEquals(0L, evaluation.remainingSeconds)
        assertTrue(evaluation.canUseEmergency)
        assertFalse(evaluation.emergencyUsedToday)
    }

    // 3. Separate apps have independent quotas.
    @Test
    fun test3_separateAppsHaveIndependentQuotas() = runBlocking {
        // Exhaust Instagram
        quotaEngine.recordUsage(instagramPkg, 1800L)

        val igEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Instagram must be blocked", igEval.isBlocked)

        val ytEval = quotaEngine.evaluateQuota(youtubePkg)
        assertFalse("YouTube must NOT be blocked", ytEval.isBlocked)
        assertEquals(0L, ytEval.usedSeconds)
        assertEquals(1800L, ytEval.remainingSeconds)
        assertEquals(ProtectionState.ALLOWED, ytEval.status)
    }

    // 4. Emergency access starts only after an explicit action.
    @Test
    fun test4_emergencyAccessStartsOnlyAfterExplicitAction() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)

        // Before explicit action: EXHAUSTED and blocked
        val before = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue(before.isBlocked)
        assertEquals(ProtectionState.EXHAUSTED, before.status)
        assertNotEquals(ProtectionState.EMERGENCY_ACTIVE, before.status)

        // User explicitly triggers emergency session
        val result = quotaEngine.startEmergencySession(instagramPkg)
        assertTrue(result is EmergencyStartResult.Success)

        // Now emergency access is active
        val after = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(ProtectionState.EMERGENCY_ACTIVE, after.status)
        assertFalse(after.isBlocked)
        assertEquals(30L, after.remainingEmergencySeconds)
    }

    // 5. Emergency access lasts no more than 30 seconds.
    @Test
    fun test5_emergencyAccessLastsNoMoreThan30Seconds() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        val startResult = quotaEngine.startEmergencySession(instagramPkg)
        assertTrue(startResult is EmergencyStartResult.Success)

        // At 15 seconds: still allowed
        fakeTimeProvider.advanceSeconds(15)
        val midEval = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(ProtectionState.EMERGENCY_ACTIVE, midEval.status)
        assertFalse(midEval.isBlocked)
        assertEquals(15L, midEval.remainingEmergencySeconds)

        // At 30 seconds (full elapsed): emergency expires
        fakeTimeProvider.advanceSeconds(15)
        val expiredEval = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(ProtectionState.BLOCKED_UNTIL_RESET, expiredEval.status)
        assertTrue(expiredEval.isBlocked)
        assertEquals(0L, expiredEval.remainingEmergencySeconds)
    }

    // 6. Emergency access cannot be restarted after process recreation.
    @Test
    fun test6_emergencyAccessCannotBeRestartedAfterProcessRecreation() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        quotaEngine.startEmergencySession(instagramPkg)

        // Advance past 30s
        fakeTimeProvider.advanceSeconds(35)

        // Simulate app process death and recreation with a fresh QuotaEngine instance
        val recreatedEngine = QuotaEngine(
            protectedAppDao = protectedAppDao,
            usageDao = usageDao,
            focusSessionDao = null,
            timeProvider = fakeTimeProvider
        )

        val eval = recreatedEngine.evaluateQuota(instagramPkg)
        assertTrue("Recreated engine must reflect blocked state", eval.isBlocked)
        assertEquals(ProtectionState.BLOCKED_UNTIL_RESET, eval.status)

        // Attempting to restart emergency fails
        val restartResult = recreatedEngine.startEmergencySession(instagramPkg)
        assertEquals(EmergencyStartResult.AlreadyUsedToday, restartResult)
    }

    // 7. Emergency access cannot be reused for the same app on the same day.
    @Test
    fun test7_emergencyAccessCannotBeReusedSameDay() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        val firstStart = quotaEngine.startEmergencySession(instagramPkg)
        assertTrue(firstStart is EmergencyStartResult.Success)

        // Advance 30 seconds so it ends
        fakeTimeProvider.advanceSeconds(30)

        // Second attempt on the same day must be rejected
        val secondStart = quotaEngine.startEmergencySession(instagramPkg)
        assertEquals(EmergencyStartResult.AlreadyUsedToday, secondStart)

        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(ProtectionState.BLOCKED_UNTIL_RESET, eval.status)
        assertTrue(eval.isBlocked)
        assertFalse(eval.canUseEmergency)
    }

    // 8. Leaving and reopening the protected app does not reset the timer.
    @Test
    fun test8_leavingAndReopeningDoesNotResetTimer() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        quotaEngine.startEmergencySession(instagramPkg)

        // User spends 10s in app, leaves, returns 5s later (15s total elapsed)
        fakeTimeProvider.advanceSeconds(15)

        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(ProtectionState.EMERGENCY_ACTIVE, eval.status)
        // Must reflect 15 seconds remaining, NOT reset to 30s
        assertEquals(15L, eval.remainingEmergencySeconds)
    }

    // 9. Expired emergency access returns the app to BLOCKED state.
    @Test
    fun test9_expiredEmergencyReturnsAppToBlockedState() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        quotaEngine.startEmergencySession(instagramPkg)

        // Advance 31 seconds
        fakeTimeProvider.advanceSeconds(31)

        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Expired emergency must return to blocked state", eval.isBlocked)
        assertEquals(ProtectionState.BLOCKED_UNTIL_RESET, eval.status)
    }

    // 10. Changing the daily limit does not reset emergency eligibility.
    @Test
    fun test10_changingDailyLimitDoesNotResetEmergencyEligibility() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 2400L) // 40m used
        quotaEngine.startEmergencySession(instagramPkg)
        fakeTimeProvider.advanceSeconds(30)

        // User changes limit from 30m (1800s) to 35m (2100s) - still exhausted!
        protectedAppDao.updateLimit(instagramPkg, 2100L)

        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue(eval.isBlocked)
        assertEquals(ProtectionState.BLOCKED_UNTIL_RESET, eval.status)
        assertTrue(eval.emergencyUsedToday)
        assertFalse(eval.canUseEmergency)

        val retryResult = quotaEngine.startEmergencySession(instagramPkg)
        assertEquals(EmergencyStartResult.AlreadyUsedToday, retryResult)
    }

    // 11. Daily reset restores quota and emergency eligibility.
    @Test
    fun test11_dailyResetRestoresQuotaAndEmergencyEligibility() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        quotaEngine.startEmergencySession(instagramPkg)
        fakeTimeProvider.advanceSeconds(30)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // Advance to next local date
        fakeTimeProvider.advanceDateByDays(1)
        val resetPerformed = quotaEngine.checkAndPerformMidnightReset()
        assertTrue(resetPerformed)

        val nextDayEval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("New day must restore quota", nextDayEval.isBlocked)
        assertEquals(0L, nextDayEval.usedSeconds)
        assertEquals(1800L, nextDayEval.remainingSeconds)
        assertEquals(ProtectionState.ALLOWED, nextDayEval.status)
        assertFalse("Emergency eligibility must be restored for new day", nextDayEval.emergencyUsedToday)
    }

    // 12. Restarting the app does not incorrectly reset usage.
    @Test
    fun test12_restartingAppDoesNotIncorrectlyResetUsage() = runBlocking {
        // User used 1000s
        quotaEngine.recordUsage(instagramPkg, 1000L)

        // Simulate app process kill & restart
        val newEngine = QuotaEngine(
            protectedAppDao = protectedAppDao,
            usageDao = usageDao,
            focusSessionDao = null,
            timeProvider = fakeTimeProvider
        )

        val eval = newEngine.evaluateQuota(instagramPkg)
        assertEquals("Usage must survive app restart", 1000L, eval.usedSeconds)
        assertEquals(800L, eval.remainingSeconds)
        assertFalse(eval.isBlocked)
    }

    // 13. Missing permissions are reported accurately.
    @Test
    fun test13_missingPermissionsReportedAccurately() = runBlocking {
        permissionChecker.active = false

        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("Evaluation must flag inactive permissions", eval.permissionsActive)

        permissionChecker.active = true
        val activeEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Evaluation must report active permissions", activeEval.permissionsActive)
    }

    // 14. Accessibility events do not cause an infinite blocking loop.
    @Test
    fun test14_accessibilityEventsDoNotCauseInfiniteBlockingLoop() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue(eval.isBlocked)

        // When BlockingActivity is visible for this package, the service loop guard prevents duplicate launches
        var launchCount = 0
        fun simulateAccessibilityEvent(pkg: String, isActivityVisible: Boolean, activePkg: String?) {
            if (isActivityVisible && activePkg == pkg) {
                // Loop guard triggered: event ignored
                return
            }
            launchCount++
        }

        // First event launches block screen
        simulateAccessibilityEvent(instagramPkg, isActivityVisible = false, activePkg = null)
        assertEquals(1, launchCount)

        // Subsequent rapid events while block screen is visible are suppressed
        simulateAccessibilityEvent(instagramPkg, isActivityVisible = true, activePkg = instagramPkg)
        simulateAccessibilityEvent(instagramPkg, isActivityVisible = true, activePkg = instagramPkg)
        assertEquals("Subsequent events while screen is visible must be suppressed", 1, launchCount)
    }

    // Reboot during emergency access safe expiry test
    @Test
    fun testRebootDuringEmergencyAccess_ImmediatelyExpiresEmergency() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)
        quotaEngine.startEmergencySession(instagramPkg)

        // Phone reboots after 5 seconds: elapsedRealtime resets to 2000ms (< elapsedStart 100000ms)
        fakeTimeProvider.simulateReboot(postBootElapsedMillis = 2000L)

        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Reboot must immediately invalidate emergency session", eval.isBlocked)
        assertEquals(ProtectionState.BLOCKED_UNTIL_RESET, eval.status)
    }

    @Test
    fun testMidnightReset_HistoricalUsagePreservedInRoom() = runBlocking {
        // Day 1: 2026-10-21
        val day1Date = TimeUtils.formatDate(fakeTimeProvider.getTodayLocalDate())
        quotaEngine.recordUsage(instagramPkg, 1800L)

        val day1Eval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Day 1 quota must be blocked after 1800s", day1Eval.isBlocked)
        assertEquals(1800L, day1Eval.usedSeconds)

        // Advance to Day 2: 2026-10-22 (crossing 12:00 AM midnight)
        fakeTimeProvider.advanceDateByDays(1)
        val day2Date = TimeUtils.formatDate(fakeTimeProvider.getTodayLocalDate())

        // Trigger midnight reset for new day
        quotaEngine.checkAndPerformMidnightReset()

        // Verify Day 2 starts fresh
        val day2Eval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("Day 2 must start UNBLOCKED", day2Eval.isBlocked)
        assertEquals("Day 2 usage must start at 0s", 0L, day2Eval.usedSeconds)
        assertEquals("Day 2 remaining allowance must be full limit", 1800L, day2Eval.remainingSeconds)

        // Verify Day 1 historical record is strictly preserved
        val day1Historical = usageDao.getDailyUsageForApp(instagramPkg, day1Date)
        assertNotNull("Day 1 historical record must be preserved", day1Historical)
        assertEquals("Day 1 historical usage must remain 1800s", 1800L, day1Historical!!.usedSeconds)
        assertTrue("Day 1 historical blocked flag must remain true", day1Historical.isBlocked)
    }

    @Test
    fun testProcessRestartAcrossMidnight_RestoresFreshQuota() = runBlocking {
        // App records usage before midnight
        quotaEngine.recordUsage(youtubePkg, 1200L)
        val beforeMidnight = quotaEngine.evaluateQuota(youtubePkg)
        assertEquals(1200L, beforeMidnight.usedSeconds)

        // Process killed and phone transitions past midnight
        fakeTimeProvider.advanceDateByDays(1)

        // Recreate QuotaEngine as happens during process restart
        val newQuotaEngineInstance = QuotaEngine(
            protectedAppDao = protectedAppDao,
            usageDao = usageDao,
            timeProvider = fakeTimeProvider,
            permissionChecker = permissionChecker
        )

        val afterRestartEval = newQuotaEngineInstance.evaluateQuota(youtubePkg)
        assertFalse("After process restart on new day, app must be unblocked", afterRestartEval.isBlocked)
        assertEquals("After restart on new day, usage must be 0s", 0L, afterRestartEval.usedSeconds)
        assertEquals(1800L, afterRestartEval.remainingSeconds)
    }

    @Test
    fun testTimezoneChange_ReCalculatesDailyResetCorrectly() = runBlocking {
        // App in UTC
        val initialSeconds = quotaEngine.getSecondsUntilNextDailyReset()
        assertTrue("Seconds until next midnight must be positive", initialSeconds > 0)

        // User switches to Asia/Kolkata (IST = UTC+5:30)
        fakeTimeProvider.setZone(ZoneId.of("Asia/Kolkata"))
        val istSeconds = quotaEngine.getSecondsUntilNextDailyReset()
        assertTrue("Seconds until next midnight in new timezone must be positive", istSeconds > 0)
    }

    @Test
    fun testReEnteringBlockedApp_DoesNotBypassEnforcement() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, 1800L)

        // First attempt to open Instagram
        val eval1 = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("First attempt must be blocked", eval1.isBlocked)

        // User exits and tries to reopen Instagram 10 seconds later
        fakeTimeProvider.advanceSeconds(10)
        val eval2 = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Re-entry attempt MUST remain blocked", eval2.isBlocked)
        assertEquals("Blocked reason must be populated", "Daily allowance of 30m exhausted", eval2.blockedReason)
    }

    @Test
    fun testOwnAppUI_ExcludedFromProtection() = runBlocking {
        val ownAppPkg = "com.focuslock.app"
        val eval = quotaEngine.evaluateQuota(ownAppPkg)

        assertFalse("FocusLock itself must NEVER be blocked", eval.isBlocked)
        assertEquals(ProtectionState.ALLOWED, eval.status)
    }
}
