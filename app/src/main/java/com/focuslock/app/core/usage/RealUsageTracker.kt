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

        val protectedAppsMap = protectedAppDao.getAllProtectedAppsFlow()
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

    private fun getForegroundTimeFromEvents(manager: UsageStatsManager, packageName: String, startDay: Long, now: Long): Long {
        return try {
            val events = manager.queryEvents(startDay, now) ?: return 0L
            var totalMs = 0L
            var lastResumeMs = 0L
            var hasSeenAnyEvent = false
            val event = android.app.usage.UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName == packageName) {
                    when (event.eventType) {
                        android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            hasSeenAnyEvent = true
                            lastResumeMs = event.timeStamp
                        }
                        android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                        android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            if (lastResumeMs > 0L) {
                                val duration = event.timeStamp - lastResumeMs
                                if (duration > 0) {
                                    totalMs += duration
                                }
                                lastResumeMs = 0L
                            } else if (!hasSeenAnyEvent) {
                                // App was already in foreground when local midnight started
                                val duration = event.timeStamp - startDay
                                if (duration > 0) {
                                    totalMs += duration
                                }
                            }
                            hasSeenAnyEvent = true
                        }
                    }
                }
            }
            if (lastResumeMs > 0L && lastResumeMs <= now) {
                val duration = now - lastResumeMs
                if (duration > 0) {
                    totalMs += duration
                }
            }
            totalMs / 1000L
        } catch (e: Exception) {
            0L
        }
    }

    private fun getForegroundTimesFromEventsBatch(manager: UsageStatsManager, startDay: Long, now: Long): Map<String, Long> {
        val resultMap = mutableMapOf<String, Long>()
        val lastResumeMap = mutableMapOf<String, Long>()
        val hasSeenEventMap = mutableMapOf<String, Boolean>()

        try {
            val events = manager.queryEvents(startDay, now) ?: return emptyMap()
            val event = android.app.usage.UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        hasSeenEventMap[pkg] = true
                        lastResumeMap[pkg] = event.timeStamp
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
                        } else if (hasSeenEventMap[pkg] != true) {
                            val duration = event.timeStamp - startDay
                            if (duration > 0) {
                                resultMap[pkg] = (resultMap[pkg] ?: 0L) + duration
                            }
                        }
                        hasSeenEventMap[pkg] = true
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
        val manager = usageStatsManager ?: return 0L
        val startDay = timeProvider.getStartOfDayMillis()
        val now = timeProvider.currentTimeMillis()
        if (now <= startDay) return 0L

        return getForegroundTimeFromEvents(manager, packageName, startDay, now)
    }

    /**
     * Queries actual Android UsageStatsManager statistics for the current cycle.
     * Reconciles usage into Room without carrying over prior days.
     */
    suspend fun reconcileWithAndroidUsageStats(targetPackages: List<String>) = withContext(Dispatchers.IO) {
        val manager = usageStatsManager ?: return@withContext
        val startDay = timeProvider.getStartOfDayMillis()
        val now = timeProvider.currentTimeMillis()
        val todayDate = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val tzId = timeProvider.getZoneId().id

        val batchMeasured = getForegroundTimesFromEventsBatch(manager, startDay, now)

        for (pkg in targetPackages) {
            val measuredSeconds = batchMeasured[pkg] ?: 0L
            val existing = usageDao.getDailyUsageForApp(pkg, todayDate)
            val app = protectedAppDao.getAppByPackageName(pkg)
            val quota = app?.dailyLimitSeconds ?: 1800L

            if (existing != null) {
                val maxSecs = maxOf(measuredSeconds, existing.usedSeconds)
                if (maxSecs > existing.usedSeconds) {
                    usageDao.insertOrUpdateDailyUsage(
                        existing.copy(
                            usedSeconds = maxSecs,
                            isBlocked = maxSecs >= existing.quotaSeconds,
                            updatedAt = now
                        )
                    )
                }
            } else if (measuredSeconds > 0L) {
                usageDao.insertOrUpdateDailyUsage(
                    DailyUsageEntity(
                        packageName = pkg,
                        localDate = todayDate,
                        timezoneId = tzId,
                        usedSeconds = measuredSeconds,
                        quotaSeconds = quota,
                        isBlocked = measuredSeconds >= quota,
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
        val measuredSeconds = queryAccurateUsageSeconds(packageName)
        val todayDate = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val tzId = timeProvider.getZoneId().id
        val now = timeProvider.currentTimeMillis()
        val existing = usageDao.getDailyUsageForApp(packageName, todayDate)
        val app = protectedAppDao.getAppByPackageName(packageName)
        val quota = app?.dailyLimitSeconds ?: 1800L

        if (existing != null) {
            val maxSeconds = maxOf(measuredSeconds, existing.usedSeconds)
            if (maxSeconds > existing.usedSeconds) {
                usageDao.insertOrUpdateDailyUsage(
                    existing.copy(
                        usedSeconds = maxSeconds,
                        isBlocked = maxSeconds >= existing.quotaSeconds,
                        updatedAt = now
                    )
                )
            }
            maxSeconds
        } else if (measuredSeconds > 0L) {
            usageDao.insertOrUpdateDailyUsage(
                DailyUsageEntity(
                    packageName = packageName,
                    localDate = todayDate,
                    timezoneId = tzId,
                    usedSeconds = measuredSeconds,
                    quotaSeconds = quota,
                    isBlocked = measuredSeconds >= quota,
                    updatedAt = now
                )
            )
            measuredSeconds
        } else {
            0L
        }
    }
}
