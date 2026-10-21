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

    /**
     * Queries actual Android UsageStatsManager statistics for the current local day.
     * Reconciles usage into Room without double counting.
     */
    suspend fun reconcileWithAndroidUsageStats(targetPackages: List<String>) = withContext(Dispatchers.IO) {
        val manager = usageStatsManager ?: return@withContext

        val startDay = timeProvider.getStartOfDayMillis()
        val now = timeProvider.currentTimeMillis()

        val stats = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startDay,
            now
        ) ?: return@withContext

        val todayDate = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val tzId = timeProvider.getZoneId().id

        for (stat in stats) {
            if (targetPackages.contains(stat.packageName)) {
                val measuredSeconds = stat.totalTimeInForeground / 1000
                val existing = usageDao.getDailyUsageForApp(stat.packageName, todayDate)

                if (existing != null) {
                    // Reconcile: use maximum verified foreground time to prevent under-reporting
                    if (measuredSeconds > existing.usedSeconds) {
                        usageDao.insertOrUpdateDailyUsage(
                            existing.copy(
                                usedSeconds = measuredSeconds,
                                isBlocked = measuredSeconds >= existing.quotaSeconds,
                                updatedAt = now
                            )
                        )
                    }
                } else if (measuredSeconds > 0) {
                    val app = protectedAppDao.getAppByPackageName(stat.packageName)
                    val quota = app?.dailyLimitSeconds ?: 1800L
                    usageDao.insertOrUpdateDailyUsage(
                        DailyUsageEntity(
                            packageName = stat.packageName,
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
    }
}
