package com.focuslock.app.core.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.focuslock.app.core.util.SystemTimeProvider
import com.focuslock.app.core.util.TimeProvider
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.dao.ProtectedAppDao
import com.focuslock.app.data.local.dao.UsageDao
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.UsageSessionEntity
import com.focuslock.app.domain.model.AppCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LaunchableAppInfo(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val isProtected: Boolean,
    val dailyLimitSeconds: Long,
    val icon: Drawable? = null
)

data class AppSessionSummary(
    val packageName: String,
    val totalForegroundSeconds: Long = 0L,
    val sessionCount: Int = 0,
    val averageSessionSeconds: Long = 0L,
    val longestSessionSeconds: Long = 0L,
    val firstActivityAt: Long = 0L,
    val lastActivityAt: Long = 0L,
    val sessions: List<UsageSessionEntity> = emptyList()
)

class RealUsageTracker(
    private val context: Context,
    private val protectedAppDao: ProtectedAppDao,
    private val usageDao: UsageDao,
    private val timeProvider: TimeProvider = SystemTimeProvider()
) {

    private val packageManager: PackageManager = context.packageManager
    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Discovers genuine, user-launchable applications installed on the device.
     * Excludes system-internal daemons and FocusLock itself.
     */
    suspend fun getLaunchableApplications(): List<LaunchableAppInfo> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        val existingProtected = protectedAppDao.getActiveProtectedApps().associateBy { it.packageName }
        val ownPackage = context.packageName

        resolveInfos
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ownPackage }
            .map { appInfo ->
                val pkg = appInfo.packageName
                val name = appInfo.loadLabel(packageManager).toString()
                val icon = try {
                    appInfo.loadIcon(packageManager)
                } catch (e: Exception) {
                    null
                }

                val existing = existingProtected[pkg]
                val category = existing?.let { AppCategory.fromString(it.category) }
                    ?: AppCategory.inferCategory(pkg)

                LaunchableAppInfo(
                    packageName = pkg,
                    appName = name,
                    category = category,
                    isProtected = existing?.enabled == true,
                    dailyLimitSeconds = existing?.dailyLimitSeconds ?: 1800L,
                    icon = icon
                )
            }
            .sortedWith(compareByDescending<LaunchableAppInfo> { it.isProtected }.thenBy { it.appName.lowercase() })
    }

    /**
     * Reconstructs discrete user foreground sessions from Android UsageEvents.
     * Prevents overlapping double-counting and merges sub-activity bounces (<2s).
     */
    fun extractSessionsFromEvents(
        packageName: String,
        startDay: Long,
        now: Long,
        dateStr: String
    ): AppSessionSummary {
        val manager = usageStatsManager ?: return AppSessionSummary(packageName)
        val rawSessions = mutableListOf<UsageSessionEntity>()

        try {
            val events = manager.queryEvents(startDay, now) ?: return AppSessionSummary(packageName)
            val event = android.app.usage.UsageEvents.Event()
            var sessionStartMs = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName == packageName) {
                    when (event.eventType) {
                        android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            if (sessionStartMs == 0L) {
                                sessionStartMs = event.timeStamp
                            }
                        }
                        android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                        android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            if (sessionStartMs > 0L) {
                                val durationMs = event.timeStamp - sessionStartMs
                                if (durationMs >= 1000L) {
                                    rawSessions.add(
                                        UsageSessionEntity(
                                            packageName = packageName,
                                            startedAt = sessionStartMs,
                                            endedAt = event.timeStamp,
                                            durationSeconds = durationMs / 1000L,
                                            source = "USAGE_EVENTS",
                                            sessionDate = dateStr
                                        )
                                    )
                                }
                                sessionStartMs = 0L
                            }
                        }
                    }
                }
            }

            // Handle active in-progress session if app is currently in foreground
            if (sessionStartMs > 0L && sessionStartMs <= now) {
                val durationMs = now - sessionStartMs
                if (durationMs >= 1000L) {
                    rawSessions.add(
                        UsageSessionEntity(
                            packageName = packageName,
                            startedAt = sessionStartMs,
                            endedAt = now,
                            durationSeconds = durationMs / 1000L,
                            source = "USAGE_EVENTS",
                            sessionDate = dateStr
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return AppSessionSummary(packageName)
        }

        if (rawSessions.isEmpty()) {
            return AppSessionSummary(packageName)
        }

        // Merge contiguous or bouncing sub-activity sessions within 2 seconds
        val mergedSessions = mutableListOf<UsageSessionEntity>()
        var current = rawSessions[0]

        for (i in 1 until rawSessions.size) {
            val next = rawSessions[i]
            if (next.startedAt - current.endedAt <= 2000L) {
                current = current.copy(
                    endedAt = maxOf(current.endedAt, next.endedAt),
                    durationSeconds = current.durationSeconds + next.durationSeconds
                )
            } else {
                mergedSessions.add(current)
                current = next
            }
        }
        mergedSessions.add(current)

        val totalSeconds = mergedSessions.sumOf { it.durationSeconds }
        val sessionCount = mergedSessions.size
        val avgSeconds = if (sessionCount > 0) totalSeconds / sessionCount else 0L
        val longestSeconds = mergedSessions.maxOfOrNull { it.durationSeconds } ?: 0L
        val firstAt = mergedSessions.firstOrNull()?.startedAt ?: 0L
        val lastAt = mergedSessions.lastOrNull()?.endedAt ?: 0L

        return AppSessionSummary(
            packageName = packageName,
            totalForegroundSeconds = totalSeconds,
            sessionCount = sessionCount,
            averageSessionSeconds = avgSeconds,
            longestSessionSeconds = longestSeconds,
            firstActivityAt = firstAt,
            lastActivityAt = lastAt,
            sessions = mergedSessions
        )
    }

    private fun getForegroundTimesFromEventsBatch(manager: UsageStatsManager, startDay: Long, now: Long): Map<String, Long> {
        val resultMap = mutableMapOf<String, Long>()
        val lastResumeMap = mutableMapOf<String, Long>()

        try {
            val events = manager.queryEvents(startDay, now) ?: return emptyMap()
            val event = android.app.usage.UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if ((lastResumeMap[pkg] ?: 0L) == 0L) {
                            lastResumeMap[pkg] = event.timeStamp
                        }
                    }
                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                    android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED,
                    android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val lastResume = lastResumeMap[pkg] ?: 0L
                        if (lastResume > 0L) {
                            val duration = event.timeStamp - lastResume
                            if (duration > 0) {
                                resultMap[pkg] = (resultMap[pkg] ?: 0L) + duration
                            }
                            lastResumeMap[pkg] = 0L
                        }
                    }
                }
            }

            for ((pkg, lastResume) in lastResumeMap) {
                if (lastResume > 0L && lastResume <= now) {
                    val duration = now - lastResume
                    if (duration > 0) {
                        resultMap[pkg] = (resultMap[pkg] ?: 0L) + duration
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return resultMap.mapValues { it.value / 1000L }
    }

    /**
     * Resolves authoritative foreground usage in seconds strictly for today's local cycle.
     * Guaranteed never to leak yesterday's pre-bucketed UsageStats into today.
     */
    fun queryAccurateUsageSeconds(packageName: String): Long {
        val startDay = timeProvider.getStartOfDayMillis()
        val now = timeProvider.currentTimeMillis()
        if (now <= startDay) return 0L
        val dateStr = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        return extractSessionsFromEvents(packageName, startDay, now, dateStr).totalForegroundSeconds
    }

    /**
     * Queries detailed session breakdown and stats for a specific package.
     */
    suspend fun queryDetailedAppSessions(packageName: String): AppSessionSummary = withContext(Dispatchers.IO) {
        val startDay = timeProvider.getStartOfDayMillis()
        val now = timeProvider.currentTimeMillis()
        val dateStr = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        extractSessionsFromEvents(packageName, startDay, now, dateStr)
    }

    /**
     * Queries actual Android UsageStatsManager statistics for the current cycle.
     * Reconciles usage into Room without carrying over prior days.
     */
    suspend fun reconcileWithAndroidUsageStats(targetPackages: List<String>) = withContext(Dispatchers.IO) {
        val startDay = timeProvider.getStartOfDayMillis()
        val now = timeProvider.currentTimeMillis()
        val todayDate = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val tzId = timeProvider.getZoneId().id

        for (pkg in targetPackages) {
            val sessionSummary = extractSessionsFromEvents(pkg, startDay, now, todayDate)
            val measuredSeconds = sessionSummary.totalForegroundSeconds
            val existing = usageDao.getDailyUsageForApp(pkg, todayDate)
            val app = protectedAppDao.getAppByPackageName(pkg)
            val quota = app?.dailyLimitSeconds ?: 1800L

            // Store parsed sessions in Room
            if (sessionSummary.sessions.isNotEmpty()) {
                usageDao.replaceSessionsForAppAndDate(pkg, todayDate, sessionSummary.sessions)
            }

            if (existing != null) {
                val isLegacyGhostAnomaly = existing.usedSeconds >= 14400L && measuredSeconds in 1L..3600L
                val reconciledSeconds = if (isLegacyGhostAnomaly) {
                    measuredSeconds
                } else {
                    maxOf(measuredSeconds, existing.usedSeconds)
                }

                usageDao.insertOrUpdateDailyUsage(
                    existing.copy(
                        usedSeconds = reconciledSeconds,
                        isBlocked = reconciledSeconds >= existing.quotaSeconds,
                        sessionCount = maxOf(existing.sessionCount, sessionSummary.sessionCount),
                        longestSessionSeconds = maxOf(existing.longestSessionSeconds, sessionSummary.longestSessionSeconds),
                        firstActivityAt = if (existing.firstActivityAt > 0L) existing.firstActivityAt else sessionSummary.firstActivityAt,
                        lastActivityAt = maxOf(existing.lastActivityAt, sessionSummary.lastActivityAt),
                        activitySource = "USAGE_EVENTS",
                        activityConfidence = "EXACT",
                        updatedAt = now
                    )
                )
            } else if (measuredSeconds > 0L) {
                usageDao.insertOrUpdateDailyUsage(
                    DailyUsageEntity(
                        packageName = pkg,
                        localDate = todayDate,
                        timezoneId = tzId,
                        usedSeconds = measuredSeconds,
                        quotaSeconds = quota,
                        isBlocked = measuredSeconds >= quota,
                        sessionCount = sessionSummary.sessionCount,
                        longestSessionSeconds = sessionSummary.longestSessionSeconds,
                        firstActivityAt = sessionSummary.firstActivityAt,
                        lastActivityAt = sessionSummary.lastActivityAt,
                        activitySource = "USAGE_EVENTS",
                        activityConfidence = "EXACT",
                        updatedAt = now
                    )
                )
            }
        }
    }

    /**
     * Synchronizes and returns genuine foreground usage for a specific protected application.
     * Guaranteed to update Room immediately so quota checks are authoritative.
     */
    suspend fun syncPackageUsage(packageName: String): Long = withContext(Dispatchers.IO) {
        val startDay = timeProvider.getStartOfDayMillis()
        val now = timeProvider.currentTimeMillis()
        val todayDate = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val tzId = timeProvider.getZoneId().id

        val sessionSummary = extractSessionsFromEvents(packageName, startDay, now, todayDate)
        val measuredSeconds = sessionSummary.totalForegroundSeconds
        val existing = usageDao.getDailyUsageForApp(packageName, todayDate)
        val app = protectedAppDao.getAppByPackageName(packageName)
        val quota = app?.dailyLimitSeconds ?: 1800L

        if (sessionSummary.sessions.isNotEmpty()) {
            usageDao.replaceSessionsForAppAndDate(packageName, todayDate, sessionSummary.sessions)
        }

        if (existing != null) {
            val isLegacyGhostAnomaly = existing.usedSeconds >= 14400L && measuredSeconds in 1L..3600L
            val reconciledSeconds = if (isLegacyGhostAnomaly) {
                measuredSeconds
            } else {
                maxOf(measuredSeconds, existing.usedSeconds)
            }

            usageDao.insertOrUpdateDailyUsage(
                existing.copy(
                    usedSeconds = reconciledSeconds,
                    isBlocked = reconciledSeconds >= existing.quotaSeconds,
                    sessionCount = maxOf(existing.sessionCount, sessionSummary.sessionCount),
                    longestSessionSeconds = maxOf(existing.longestSessionSeconds, sessionSummary.longestSessionSeconds),
                    firstActivityAt = if (existing.firstActivityAt > 0L) existing.firstActivityAt else sessionSummary.firstActivityAt,
                    lastActivityAt = maxOf(existing.lastActivityAt, sessionSummary.lastActivityAt),
                    activitySource = "USAGE_EVENTS",
                    activityConfidence = "EXACT",
                    updatedAt = now
                )
            )
            reconciledSeconds
        } else if (measuredSeconds > 0L) {
            usageDao.insertOrUpdateDailyUsage(
                DailyUsageEntity(
                    packageName = packageName,
                    localDate = todayDate,
                    timezoneId = tzId,
                    usedSeconds = measuredSeconds,
                    quotaSeconds = quota,
                    isBlocked = measuredSeconds >= quota,
                    sessionCount = sessionSummary.sessionCount,
                    longestSessionSeconds = sessionSummary.longestSessionSeconds,
                    firstActivityAt = sessionSummary.firstActivityAt,
                    lastActivityAt = sessionSummary.lastActivityAt,
                    activitySource = "USAGE_EVENTS",
                    activityConfidence = "EXACT",
                    updatedAt = now
                )
            )
            measuredSeconds
        } else {
            0L
        }
    }
}
