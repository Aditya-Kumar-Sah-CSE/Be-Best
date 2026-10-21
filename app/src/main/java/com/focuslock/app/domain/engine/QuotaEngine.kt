package com.focuslock.app.domain.engine

import com.focuslock.app.core.util.SystemTimeProvider
import com.focuslock.app.core.util.TimeProvider
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.dao.FocusSessionDao
import com.focuslock.app.data.local.dao.ProtectedAppDao
import com.focuslock.app.data.local.dao.UsageDao
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.ProtectedAppEntity

enum class QuotaStatus {
    ALLOWED,
    WARNING, // Usage >= 80% of quota
    EXHAUSTED
}

data class AppQuotaEvaluation(
    val packageName: String,
    val dailyLimitSeconds: Long,
    val usedSeconds: Long,
    val remainingSeconds: Long,
    val status: QuotaStatus,
    val isBlocked: Boolean,
    val blockedReason: String? = null
)

class QuotaEngine(
    private val protectedAppDao: ProtectedAppDao,
    private val usageDao: UsageDao,
    private val focusSessionDao: FocusSessionDao? = null,
    private val timeProvider: TimeProvider = SystemTimeProvider()
) {

    /**
     * Authoritative calculation of an application's quota status.
     * Guaranteed independent per package.
     */
    suspend fun evaluateQuota(packageName: String): AppQuotaEvaluation {
        val app = protectedAppDao.getAppByPackageName(packageName)
            ?: return AppQuotaEvaluation(
                packageName = packageName,
                dailyLimitSeconds = 1800L,
                usedSeconds = 0L,
                remainingSeconds = 1800L,
                status = QuotaStatus.ALLOWED,
                isBlocked = false
            )

        if (!app.enabled) {
            return AppQuotaEvaluation(
                packageName = packageName,
                dailyLimitSeconds = app.dailyLimitSeconds,
                usedSeconds = 0L,
                remainingSeconds = app.dailyLimitSeconds,
                status = QuotaStatus.ALLOWED,
                isBlocked = false
            )
        }

        // Check if an active Focus Session is restricting distractions
        val activeFocus = focusSessionDao?.getActiveSession()
        if (activeFocus != null) {
            val now = timeProvider.currentTimeMillis()
            if (now < activeFocus.plannedEndAt) {
                return AppQuotaEvaluation(
                    packageName = packageName,
                    dailyLimitSeconds = app.dailyLimitSeconds,
                    usedSeconds = 0L,
                    remainingSeconds = 0L,
                    status = QuotaStatus.EXHAUSTED,
                    isBlocked = true,
                    blockedReason = "Active Focus Session in progress"
                )
            }
        }

        val todayDate = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val usageEntity = usageDao.getDailyUsageForApp(packageName, todayDate)
        val usedSeconds = usageEntity?.usedSeconds?.coerceAtLeast(0L) ?: 0L
        val limitSeconds = app.dailyLimitSeconds.coerceAtLeast(0L)

        // Remaining allowance = max(0, dailyLimit - usedToday)
        val remainingSeconds = (limitSeconds - usedSeconds).coerceAtLeast(0L)
        val isExhausted = (usedSeconds >= limitSeconds) && (limitSeconds > 0L)
        val isZeroQuota = (limitSeconds == 0L)

        val isBlocked = isExhausted || isZeroQuota

        val status = when {
            isBlocked -> QuotaStatus.EXHAUSTED
            limitSeconds > 0L && usedSeconds >= (limitSeconds * 0.8) -> QuotaStatus.WARNING
            else -> QuotaStatus.ALLOWED
        }

        val reason = if (isBlocked) "Daily allowance of ${TimeUtils.formatDurationShort(limitSeconds)} exhausted" else null

        return AppQuotaEvaluation(
            packageName = packageName,
            dailyLimitSeconds = limitSeconds,
            usedSeconds = usedSeconds,
            remainingSeconds = remainingSeconds,
            status = status,
            isBlocked = isBlocked,
            blockedReason = reason
        )
    }

    /**
     * Accumulates real usage for an application.
     * Prevents negative additions and duplicate inflations.
     */
    suspend fun recordUsage(packageName: String, additionalSeconds: Long, source: String = "REAL_TRACKER") {
        if (additionalSeconds <= 0L) return

        val todayDate = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val tzId = timeProvider.getZoneId().id
        val now = timeProvider.currentTimeMillis()

        val app = protectedAppDao.getAppByPackageName(packageName)
        val quotaSeconds = app?.dailyLimitSeconds ?: 1800L

        val existing = usageDao.getDailyUsageForApp(packageName, todayDate)
        if (existing == null) {
            val isBlocked = additionalSeconds >= quotaSeconds
            usageDao.insertOrUpdateDailyUsage(
                DailyUsageEntity(
                    packageName = packageName,
                    localDate = todayDate,
                    timezoneId = tzId,
                    usedSeconds = additionalSeconds,
                    quotaSeconds = quotaSeconds,
                    isBlocked = isBlocked,
                    updatedAt = now
                )
            )
        } else {
            val newTotal = existing.usedSeconds + additionalSeconds
            val isBlocked = newTotal >= existing.quotaSeconds
            usageDao.insertOrUpdateDailyUsage(
                existing.copy(
                    usedSeconds = newTotal,
                    isBlocked = isBlocked,
                    updatedAt = now
                )
            )
        }
    }

    /**
     * Checks whether calendar day has transitioned and resets quotas if so.
     * A countdown reaching zero alone does not reset quotas; only date progression resets.
     */
    suspend fun checkAndPerformMidnightReset(): Boolean {
        val today = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val tzId = timeProvider.getZoneId().id
        val protectedApps = protectedAppDao.getActiveProtectedApps()

        var resetPerformed = false
        for (app in protectedApps) {
            val existing = usageDao.getDailyUsageForApp(app.packageName, today)
            if (existing == null) {
                usageDao.insertOrUpdateDailyUsage(
                    DailyUsageEntity(
                        packageName = app.packageName,
                        localDate = today,
                        timezoneId = tzId,
                        usedSeconds = 0L,
                        quotaSeconds = app.dailyLimitSeconds,
                        isBlocked = false,
                        updatedAt = timeProvider.currentTimeMillis()
                    )
                )
                resetPerformed = true
            }
        }
        return resetPerformed
    }

    fun getSecondsUntilNextDailyReset(): Long {
        val diff = timeProvider.getNextMidnightMillis() - timeProvider.currentTimeMillis()
        return (diff / 1000).coerceAtLeast(0L)
    }
}
