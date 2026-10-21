package com.focuslock.app.domain.usecase

import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.dao.ProtectedAppDao
import com.focuslock.app.data.local.dao.UsageDao
import com.focuslock.app.data.local.entity.DailyUsageEntity
import java.util.TimeZone

class ResetMidnightQuotaUseCase(
    private val protectedAppDao: ProtectedAppDao,
    private val usageDao: UsageDao
) {
    /**
     * Initializes a fresh daily quota record for every enabled app for the new calendar day.
     * Guaranteed to work even if device was asleep or killed at midnight.
     */
    suspend fun resetQuotasForNewDay() {
        val today = TimeUtils.getTodayDateString()
        val tzId = TimeZone.getDefault().id
        val protectedApps = protectedAppDao.getActiveProtectedApps()

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
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
