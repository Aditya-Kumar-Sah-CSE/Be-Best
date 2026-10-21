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
    private var currentMillis: Long = 1761048000000L // 12:00:00 UTC
) : TimeProvider {

    fun advanceDateByDays(days: Long) {
        currentDate = currentDate.plusDays(days)
        currentMillis += days * 86400000L
    }

    fun setZone(newZone: ZoneId) {
        zoneId = newZone
    }

    override fun currentTimeMillis(): Long = currentMillis

    override fun elapsedRealtime(): Long = currentMillis

    override fun getZoneId(): ZoneId = zoneId

    override fun getTodayLocalDate(): LocalDate = currentDate

    override fun getStartOfDayMillis(): Long =
        currentDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

    override fun getNextMidnightMillis(): Long =
        currentDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
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
    private lateinit var quotaEngine: QuotaEngine

    private val instagramPkg = "com.instagram.android"
    private val youtubePkg = "com.google.android.youtube"

    @Before
    fun setUp() = runBlocking {
        protectedAppDao = FakeProtectedAppDao()
        usageDao = FakeUsageDao()
        fakeTimeProvider = FakeTimeProvider()

        quotaEngine = QuotaEngine(
            protectedAppDao = protectedAppDao,
            usageDao = usageDao,
            focusSessionDao = null,
            timeProvider = fakeTimeProvider
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

    @Test
    fun testUsageBelowLimit_AllowedState() = runBlocking {
        // 10 minutes used of 30 minutes
        quotaEngine.recordUsage(instagramPkg, 600L)

        val evaluation = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(QuotaStatus.ALLOWED, evaluation.status)
        assertFalse(evaluation.isBlocked)
        assertEquals(1800L, evaluation.dailyLimitSeconds)
        assertEquals(600L, evaluation.usedSeconds)
        assertEquals(1200L, evaluation.remainingSeconds)
    }

    @Test
    fun testUsageNearLimit_WarningState() = runBlocking {
        // 25 minutes used of 30 minutes (>80%)
        quotaEngine.recordUsage(instagramPkg, 1500L)

        val evaluation = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(QuotaStatus.WARNING, evaluation.status)
        assertFalse(evaluation.isBlocked)
        assertEquals(300L, evaluation.remainingSeconds)
    }

    @Test
    fun testUsageExactlyAtLimit_ExhaustedAndBlocked() = runBlocking {
        // Exactly 30 minutes used
        quotaEngine.recordUsage(instagramPkg, 1800L)

        val evaluation = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(QuotaStatus.EXHAUSTED, evaluation.status)
        assertTrue(evaluation.isBlocked)
        assertEquals(0L, evaluation.remainingSeconds)
    }

    @Test
    fun testUsageAboveLimit_RemainingNeverNegative() = runBlocking {
        // 35 minutes used of 30 minutes
        quotaEngine.recordUsage(instagramPkg, 2100L)

        val evaluation = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(QuotaStatus.EXHAUSTED, evaluation.status)
        assertTrue(evaluation.isBlocked)
        assertEquals(0L, evaluation.remainingSeconds) // max(0, limit - used)
        assertEquals(2100L, evaluation.usedSeconds)
    }

    @Test
    fun testIndependentQuotas_UsingOneAppDoesNotConsumeAnother() = runBlocking {
        // Exhaust Instagram (30m used)
        quotaEngine.recordUsage(instagramPkg, 1800L)

        val igEval = quotaEngine.evaluateQuota(instagramPkg)
        assertTrue("Instagram must be blocked", igEval.isBlocked)

        val ytEval = quotaEngine.evaluateQuota(youtubePkg)
        assertFalse("YouTube must NOT be blocked", ytEval.isBlocked)
        assertEquals(0L, ytEval.usedSeconds)
        assertEquals(1800L, ytEval.remainingSeconds)
        assertEquals(QuotaStatus.ALLOWED, ytEval.status)
    }

    @Test
    fun testZeroMinuteQuota_ImmediatelyBlocked() = runBlocking {
        val testPkg = "com.gaming.distraction"
        protectedAppDao.insertOrUpdate(
            ProtectedAppEntity(
                packageName = testPkg,
                displayName = "Game",
                dailyLimitSeconds = 0L,
                enabled = true
            )
        )

        val eval = quotaEngine.evaluateQuota(testPkg)
        assertTrue("Zero-quota app must be immediately blocked", eval.isBlocked)
        assertEquals(0L, eval.remainingSeconds)
    }

    @Test
    fun testQuotaChange_IncreasingLimitUnblocksApp() = runBlocking {
        // Exhaust Instagram at 30m
        quotaEngine.recordUsage(instagramPkg, 1800L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // User increases limit to 45m (2700s)
        protectedAppDao.updateLimit(instagramPkg, 2700L)

        val updatedEval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("App must be unblocked after limit increase", updatedEval.isBlocked)
        assertEquals(900L, updatedEval.remainingSeconds)
        assertEquals(QuotaStatus.ALLOWED, updatedEval.status)
    }

    @Test
    fun testCalendarDayMidnightReset_RestoresAllowance() = runBlocking {
        // Exhaust quota on Day 1
        quotaEngine.recordUsage(instagramPkg, 1800L)
        assertTrue(quotaEngine.evaluateQuota(instagramPkg).isBlocked)

        // Advance simulated time by 1 day (midnight crossed)
        fakeTimeProvider.advanceDateByDays(1)

        // QuotaEngine checks for midnight reset
        val resetTriggered = quotaEngine.checkAndPerformMidnightReset()
        assertTrue("Reset must be triggered for new date", resetTriggered)

        val newDayEval = quotaEngine.evaluateQuota(instagramPkg)
        assertFalse("Quota must be unblocked on new day", newDayEval.isBlocked)
        assertEquals(0L, newDayEval.usedSeconds)
        assertEquals(1800L, newDayEval.remainingSeconds)
        assertEquals(QuotaStatus.ALLOWED, newDayEval.status)
    }

    @Test
    fun testNegativeUsage_IgnoredGracefully() = runBlocking {
        quotaEngine.recordUsage(instagramPkg, -500L)
        val eval = quotaEngine.evaluateQuota(instagramPkg)
        assertEquals(0L, eval.usedSeconds)
        assertEquals(1800L, eval.remainingSeconds)
    }
}
