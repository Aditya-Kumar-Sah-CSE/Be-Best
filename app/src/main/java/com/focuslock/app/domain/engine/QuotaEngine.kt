package com.focuslock.app.domain.engine

import com.focuslock.app.core.util.SystemTimeProvider
import com.focuslock.app.core.util.TimeProvider
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.dao.FocusSessionDao
import com.focuslock.app.data.local.dao.ProtectedAppDao
import com.focuslock.app.data.local.dao.UsageDao
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.ProtectedAppEntity

enum class ProtectionState {
    ALLOWED,
    WARNING,
    EXHAUSTED,
    EMERGENCY_ACTIVE,
    BLOCKED_UNTIL_RESET
}

typealias QuotaStatus = ProtectionState

data class AppQuotaEvaluation(
    val packageName: String,
    val dailyLimitSeconds: Long,
    val usedSeconds: Long,
    val remainingSeconds: Long,
    val status: ProtectionState,
    val isBlocked: Boolean,
    val canUseEmergency: Boolean = false,
    val remainingEmergencySeconds: Long = 0L,
    val emergencyUsedToday: Boolean = false,
    val emergencyExpiresAtMillis: Long = 0L,
    val blockedReason: String? = null,
    val permissionsActive: Boolean = true
)

sealed class EmergencyStartResult {
    data class Success(val remainingSeconds: Long, val expiresAtMillis: Long) : EmergencyStartResult()
    object AlreadyUsedToday : EmergencyStartResult()
    object NotProtected : EmergencyStartResult()
    object NotExhausted : EmergencyStartResult()
}

interface PermissionChecker {
    fun areEnforcementPermissionsActive(): Boolean
}

class QuotaEngine(
    private val protectedAppDao: ProtectedAppDao,
    private val usageDao: UsageDao,
    private val focusSessionDao: FocusSessionDao? = null,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val permissionChecker: PermissionChecker? = null
) {

    companion object {
        const val EMERGENCY_DURATION_MILLIS = 30_000L
        const val EMERGENCY_DURATION_SECONDS = 30L
    }

    /**
     * Authoritative calculation of an application's quota status and protection state.
     * Guaranteed independent per package.
     */
    suspend fun evaluateQuota(packageName: String): AppQuotaEvaluation {
        val permsActive = permissionChecker?.areEnforcementPermissionsActive() ?: true

        val app = protectedAppDao.getAppByPackageName(packageName)
            ?: return AppQuotaEvaluation(
                packageName = packageName,
                dailyLimitSeconds = 1800L,
                usedSeconds = 0L,
                remainingSeconds = 1800L,
                status = ProtectionState.ALLOWED,
                isBlocked = false,
                permissionsActive = permsActive
            )

        if (!app.enabled) {
            return AppQuotaEvaluation(
                packageName = packageName,
                dailyLimitSeconds = app.dailyLimitSeconds,
                usedSeconds = 0L,
                remainingSeconds = app.dailyLimitSeconds,
                status = ProtectionState.ALLOWED,
                isBlocked = false,
                permissionsActive = permsActive
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
                    status = ProtectionState.EXHAUSTED,
                    isBlocked = true,
                    canUseEmergency = false,
                    blockedReason = "Active Focus Session in progress",
                    permissionsActive = permsActive
                )
            }
        }

        val todayDate = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val usageEntity = usageDao.getDailyUsageForApp(packageName, todayDate)
        val usedSeconds = usageEntity?.usedSeconds?.coerceAtLeast(0L) ?: 0L
        val limitSeconds = app.dailyLimitSeconds.coerceAtLeast(0L)

        // Remaining daily allowance = max(0, dailyLimit - usedToday)
        val remainingSeconds = (limitSeconds - usedSeconds).coerceAtLeast(0L)
        val isExhausted = (usedSeconds >= limitSeconds && limitSeconds > 0L) || (limitSeconds == 0L)

        if (!isExhausted) {
            val status = if (limitSeconds > 0L && usedSeconds >= (limitSeconds * 0.8)) {
                ProtectionState.WARNING
            } else {
                ProtectionState.ALLOWED
            }
            return AppQuotaEvaluation(
                packageName = packageName,
                dailyLimitSeconds = limitSeconds,
                usedSeconds = usedSeconds,
                remainingSeconds = remainingSeconds,
                status = status,
                isBlocked = false,
                canUseEmergency = false,
                emergencyUsedToday = usageEntity?.emergencyUsed == true,
                blockedReason = null,
                permissionsActive = permsActive
            )
        }

        // Quota is EXHAUSTED: Evaluate 30-second Emergency Access state
        val emergencyUsed = usageEntity?.emergencyUsed == true
        val emergencyStartedAt = usageEntity?.emergencyStartedAt ?: 0L
        val emergencyExpiresAt = usageEntity?.emergencyExpiresAt ?: 0L
        val emergencyElapsedStart = usageEntity?.emergencyElapsedRealtimeAtStart ?: 0L

        if (emergencyStartedAt > 0L && emergencyExpiresAt > 0L) {
            val nowWall = timeProvider.currentTimeMillis()
            val nowElapsed = timeProvider.elapsedRealtime()

            val wallExpired = nowWall >= emergencyExpiresAt
            val elapsedExpired = (nowElapsed - emergencyElapsedStart) >= EMERGENCY_DURATION_MILLIS
            val rebootDetected = nowElapsed < emergencyElapsedStart

            val isExpired = wallExpired || elapsedExpired || rebootDetected

            if (!isExpired) {
                val remainingMillis = minOf(
                    (emergencyExpiresAt - nowWall),
                    (EMERGENCY_DURATION_MILLIS - (nowElapsed - emergencyElapsedStart))
                ).coerceAtLeast(0L)
                val remainingSecs = ((remainingMillis + 999L) / 1000L).coerceIn(1L, EMERGENCY_DURATION_SECONDS)

                return AppQuotaEvaluation(
                    packageName = packageName,
                    dailyLimitSeconds = limitSeconds,
                    usedSeconds = usedSeconds,
                    remainingSeconds = 0L,
                    status = ProtectionState.EMERGENCY_ACTIVE,
                    isBlocked = false, // Temporarily accessible during 30s emergency window
                    canUseEmergency = false,
                    remainingEmergencySeconds = remainingSecs,
                    emergencyUsedToday = true,
                    emergencyExpiresAtMillis = emergencyExpiresAt,
                    blockedReason = null,
                    permissionsActive = permsActive
                )
            } else {
                // Emergency session was started but has elapsed
                return AppQuotaEvaluation(
                    packageName = packageName,
                    dailyLimitSeconds = limitSeconds,
                    usedSeconds = usedSeconds,
                    remainingSeconds = 0L,
                    status = ProtectionState.BLOCKED_UNTIL_RESET,
                    isBlocked = true,
                    canUseEmergency = false,
                    remainingEmergencySeconds = 0L,
                    emergencyUsedToday = true,
                    emergencyExpiresAtMillis = emergencyExpiresAt,
                    blockedReason = "Emergency access ended. Locked until tomorrow.",
                    permissionsActive = permsActive
                )
            }
        }

        // Emergency has not yet been started
        if (!emergencyUsed) {
            return AppQuotaEvaluation(
                packageName = packageName,
                dailyLimitSeconds = limitSeconds,
                usedSeconds = usedSeconds,
                remainingSeconds = 0L,
                status = ProtectionState.EXHAUSTED,
                isBlocked = true,
                canUseEmergency = true,
                remainingEmergencySeconds = 0L,
                emergencyUsedToday = false,
                blockedReason = "Daily allowance of ${TimeUtils.formatDurationShort(limitSeconds)} exhausted",
                permissionsActive = permsActive
            )
        } else {
            return AppQuotaEvaluation(
                packageName = packageName,
                dailyLimitSeconds = limitSeconds,
                usedSeconds = usedSeconds,
                remainingSeconds = 0L,
                status = ProtectionState.BLOCKED_UNTIL_RESET,
                isBlocked = true,
                canUseEmergency = false,
                remainingEmergencySeconds = 0L,
                emergencyUsedToday = true,
                blockedReason = "Emergency access already used today.",
                permissionsActive = permsActive
            )
        }
    }

    /**
     * Starts a one-time 30-second emergency unlock session for an exhausted application.
     * Prevents re-use on the same local calendar day.
     */
    suspend fun startEmergencySession(packageName: String): EmergencyStartResult {
        val evaluation = evaluateQuota(packageName)
        val app = protectedAppDao.getAppByPackageName(packageName)
            ?: return EmergencyStartResult.NotProtected

        if (!evaluation.isBlocked && evaluation.status != ProtectionState.EXHAUSTED) {
            return EmergencyStartResult.NotExhausted
        }

        if (evaluation.emergencyUsedToday || !evaluation.canUseEmergency) {
            return EmergencyStartResult.AlreadyUsedToday
        }

        val todayDate = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val tzId = timeProvider.getZoneId().id
        val nowWall = timeProvider.currentTimeMillis()
        val nowElapsed = timeProvider.elapsedRealtime()
        val expiresAt = nowWall + EMERGENCY_DURATION_MILLIS

        val existing = usageDao.getDailyUsageForApp(packageName, todayDate)
        if (existing == null) {
            usageDao.insertOrUpdateDailyUsage(
                DailyUsageEntity(
                    packageName = packageName,
                    localDate = todayDate,
                    timezoneId = tzId,
                    usedSeconds = evaluation.usedSeconds,
                    quotaSeconds = app.dailyLimitSeconds,
                    isBlocked = false,
                    emergencyUsed = true,
                    emergencyStartedAt = nowWall,
                    emergencyExpiresAt = expiresAt,
                    emergencyElapsedRealtimeAtStart = nowElapsed,
                    updatedAt = nowWall
                )
            )
        } else {
            usageDao.recordEmergencySession(
                packageName = packageName,
                date = todayDate,
                startedAt = nowWall,
                expiresAt = expiresAt,
                elapsedStart = nowElapsed,
                now = nowWall
            )
        }

        return EmergencyStartResult.Success(
            remainingSeconds = EMERGENCY_DURATION_SECONDS,
            expiresAtMillis = expiresAt
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
     * Checks whether calendar day has transitioned and resets quotas for all protected apps.
     * Restores fresh emergency eligibility for the new local calendar date.
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
                        emergencyUsed = false,
                        emergencyStartedAt = 0L,
                        emergencyExpiresAt = 0L,
                        emergencyElapsedRealtimeAtStart = 0L,
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
