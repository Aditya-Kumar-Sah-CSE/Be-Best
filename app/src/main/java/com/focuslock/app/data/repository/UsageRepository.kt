package com.focuslock.app.data.repository

import android.app.usage.UsageStatsManager
import android.content.Context
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.dao.UsageDao
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.ProtectionEventEntity
import com.focuslock.app.data.local.entity.UsageSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.TimeZone

data class DailyUsageSummary(
    val totalControlledUsageSeconds: Long,
    val blockedAppsCount: Int,
    val activeAppsCount: Int
)

interface UsageRepository {
    fun getDailyUsageFlow(date: String): Flow<List<DailyUsageEntity>>
    suspend fun recordForegroundSession(packageName: String, durationSeconds: Long, source: String)
    suspend fun isAppBlocked(packageName: String): Boolean
    suspend fun getDailySummary(date: String): DailyUsageSummary
    suspend fun logProtectionEvent(eventType: String, details: String)
    suspend fun reconcileWithUsageStats(packageNames: List<String>)
}

class UsageRepositoryImpl(
    private val dao: UsageDao,
    private val context: Context
) : UsageRepository {

    override fun getDailyUsageFlow(date: String): Flow<List<DailyUsageEntity>> {
        return dao.getDailyUsageForDateFlow(date)
    }

    override suspend fun recordForegroundSession(
        packageName: String,
        durationSeconds: Long,
        source: String
    ) = withContext(Dispatchers.IO) {
        if (durationSeconds <= 0) return@withContext

        val today = TimeUtils.getTodayDateString()
        val tzId = TimeZone.getDefault().id
        val now = System.currentTimeMillis()

        // Insert session record
        dao.insertUsageSession(
            UsageSessionEntity(
                packageName = packageName,
                startedAt = now - (durationSeconds * 1000),
                endedAt = now,
                durationSeconds = durationSeconds,
                source = source,
                sessionDate = today
            )
        )

        // Increment or initialize daily usage record
        val existing = dao.getDailyUsageForApp(packageName, today)
        if (existing == null) {
            dao.insertOrUpdateDailyUsage(
                DailyUsageEntity(
                    packageName = packageName,
                    localDate = today,
                    timezoneId = tzId,
                    usedSeconds = durationSeconds,
                    quotaSeconds = 1800L,
                    isBlocked = durationSeconds >= 1800L,
                    updatedAt = now
                )
            )
        } else {
            dao.incrementUsage(packageName, today, durationSeconds, now)
        }
    }

    override suspend fun isAppBlocked(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val today = TimeUtils.getTodayDateString()
        val usage = dao.getDailyUsageForApp(packageName, today)
        return@withContext usage?.isBlocked ?: false
    }

    override suspend fun getDailySummary(date: String): DailyUsageSummary = withContext(Dispatchers.IO) {
        val total = dao.getTotalUsedSecondsForDate(date) ?: 0L
        val blocked = dao.getBlockedAppsCount(date)
        DailyUsageSummary(
            totalControlledUsageSeconds = total,
            blockedAppsCount = blocked,
            activeAppsCount = 0
        )
    }

    override suspend fun logProtectionEvent(eventType: String, details: String) = withContext(Dispatchers.IO) {
        dao.insertProtectionEvent(
            ProtectionEventEntity(
                eventType = eventType,
                timestamp = System.currentTimeMillis(),
                detailsCode = details
            )
        )
    }

    override suspend fun reconcileWithUsageStats(packageNames: List<String>) = withContext(Dispatchers.IO) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext

        val startDay = TimeUtils.getDayStartMillis()
        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startDay,
            now
        ) ?: return@withContext

        val today = TimeUtils.getTodayDateString()
        val tzId = TimeZone.getDefault().id

        for (stat in stats) {
            if (packageNames.contains(stat.packageName)) {
                val statsUsageSeconds = stat.totalTimeInForeground / 1000
                val existing = dao.getDailyUsageForApp(stat.packageName, today)

                if (existing != null) {
                    // Reconcile if UsageStats recorded more than foreground sessions
                    if (statsUsageSeconds > existing.usedSeconds) {
                        dao.insertOrUpdateDailyUsage(
                            existing.copy(
                                usedSeconds = statsUsageSeconds,
                                isBlocked = statsUsageSeconds >= existing.quotaSeconds,
                                updatedAt = now
                            )
                        )
                    }
                } else if (statsUsageSeconds > 0) {
                    dao.insertOrUpdateDailyUsage(
                        DailyUsageEntity(
                            packageName = stat.packageName,
                            localDate = today,
                            timezoneId = tzId,
                            usedSeconds = statsUsageSeconds,
                            quotaSeconds = 1800L,
                            isBlocked = statsUsageSeconds >= 1800L,
                            updatedAt = now
                        )
                    )
                }
            }
        }
    }
}
