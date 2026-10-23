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

enum class QuotaWarningType {
    NONE,
    TEN_MINUTES,
    FIVE_MINUTES
}

enum class EnforcementState {
    UNDER_LIMIT,
    LIMIT_REACHED,
    TEMPORARY_BYPASS_ACTIVE,
    DAILY_RESET_PENDING
}

typealias QuotaStatus = ProtectionState

data class WarningCheckResult(
    val warningToDeliver: QuotaWarningType,
    val remainingSeconds: Long,
    val limitSeconds: Long,
    val usedSeconds: Long,
    val appName: String
)

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
    val warning10Triggered: Boolean = false,
    val warning5Triggered: Boolean = false,
    val activeWarning: QuotaWarningType = QuotaWarningType.NONE,
    val blockedReason: String? = null,
    val permissionsActive: Boolean = true,
    val enforcementState: EnforcementState = when (status) {
        ProtectionState.ALLOWED, ProtectionState.WARNING -> EnforcementState.UNDER_LIMIT
        ProtectionState.EMERGENCY_ACTIVE -> EnforcementState.TEMPORARY_BYPASS_ACTIVE
        ProtectionState.EXHAUSTED -> EnforcementState.LIMIT_REACHED
        ProtectionState.BLOCKED_UNTIL_RESET -> EnforcementState.DAILY_RESET_PENDING
    }
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
        val isExhausted = usageEntity?.isBlocked == true || (usedSeconds >= limitSeconds && limitSeconds > 0L) || (limitSeconds == 0L)

        if (!isExhausted) {
            val w10 = usageEntity?.warning10Triggered == true
            val w5 = usageEntity?.warning5Triggered == true
            val activeWarning = when {
                limitSeconds > 300L && remainingSeconds <= 300L && usedSeconds > 0L -> QuotaWarningType.FIVE_MINUTES
                limitSeconds > 600L && remainingSeconds <= 600L && usedSeconds > 0L -> QuotaWarningType.TEN_MINUTES
                else -> QuotaWarningType.NONE
            }
            val status = if (activeWarning != QuotaWarningType.NONE || (limitSeconds > 0L && usedSeconds >= (limitSeconds * 0.8))) {
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
                warning10Triggered = w10,
                warning5Triggered = w5,
                activeWarning = activeWarning,
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
    /**
     * Accumulates real usage for an application.
     * Returns true if the app is now blocked (quota exhausted) after recording.
     * Prevents negative additions and duplicate inflations.
     */
    suspend fun recordUsage(packageName: String, additionalSeconds: Long, source: String = "REAL_TRACKER"): Boolean {
        if (additionalSeconds <= 0L) return false

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
            return isBlocked
        } else {
            val newTotal = existing.usedSeconds + additionalSeconds
            val isBlocked = existing.isBlocked || (newTotal >= quotaSeconds)
            usageDao.insertOrUpdateDailyUsage(
                existing.copy(
                    usedSeconds = newTotal,
                    quotaSeconds = quotaSeconds,
                    isBlocked = isBlocked,
                    updatedAt = now
                )
            )
            return isBlocked
        }
    }

    /**
     * Checks if a 10-minute or 5-minute warning should be dispatched for this app.
     * If a warning threshold is crossed and has not yet been delivered today,
     * updates the database delivery flag and returns a WarningCheckResult.
     * Guaranteed at most once per app per local calendar day.
     */
    suspend fun checkAndRecordWarningDelivery(packageName: String): WarningCheckResult? {
        val app = protectedAppDao.getAppByPackageName(packageName) ?: return null
        if (!app.enabled) return null

        val limitSeconds = app.dailyLimitSeconds.coerceAtLeast(0L)
        if (limitSeconds == 0L) return null

        val todayDate = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val tzId = timeProvider.getZoneId().id
        val now = timeProvider.currentTimeMillis()

        var usage = usageDao.getDailyUsageForApp(packageName, todayDate)
        if (usage == null) {
            usage = DailyUsageEntity(
                packageName = packageName,
                localDate = todayDate,
                timezoneId = tzId,
                usedSeconds = 0L,
                quotaSeconds = limitSeconds,
                isBlocked = false,
                updatedAt = now
            )
            usageDao.insertOrUpdateDailyUsage(usage)
        }

        val usedSeconds = usage.usedSeconds.coerceAtLeast(0L)
        val remainingSeconds = (limitSeconds - usedSeconds).coerceAtLeast(0L)

        // If quota is exhausted (<= 0), limit enforcement handles it, no warning
        if (remainingSeconds <= 0L) return null

        // 5-Minute Warning: remaining <= 5 min (300s), limit > 5 min (300s), used > 0
        if (limitSeconds > 300L && remainingSeconds <= 300L && usedSeconds > 0L) {
            if (!usage.warning5Triggered) {
                usageDao.markWarning5Triggered(packageName, todayDate, now)
                if (!usage.warning10Triggered && limitSeconds > 600L) {
                    usageDao.markWarning10Triggered(packageName, todayDate, now)
                }
                return WarningCheckResult(
                    warningToDeliver = QuotaWarningType.FIVE_MINUTES,
                    remainingSeconds = remainingSeconds,
                    limitSeconds = limitSeconds,
                    usedSeconds = usedSeconds,
                    appName = app.displayName
                )
            }
        } else if (limitSeconds > 600L && remainingSeconds <= 600L && usedSeconds > 0L) {
            // 10-Minute Warning: remaining <= 10 min (600s), limit > 10 min (600s), used > 0
            if (!usage.warning10Triggered) {
                usageDao.markWarning10Triggered(packageName, todayDate, now)
                return WarningCheckResult(
                    warningToDeliver = QuotaWarningType.TEN_MINUTES,
                    remainingSeconds = remainingSeconds,
                    limitSeconds = limitSeconds,
                    usedSeconds = usedSeconds,
                    appName = app.displayName
                )
            }
        }

        return null
    }

    /**
     * Checks whether calendar day has transitioned and resets quotas for all protected apps.
     * Restores fresh emergency eligibility and warning state for the new local calendar date.
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
                        warning10Triggered = false,
                        warning5Triggered = false,
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

    suspend fun isAppExhausted(packageName: String): Boolean {
        val today = TimeUtils.formatDate(timeProvider.getTodayLocalDate())
        val usage = usageDao.getDailyUsageForApp(packageName, today) ?: return false
        val app = protectedAppDao.getAppByPackageName(packageName) ?: return false
        if (!app.enabled) return false
        val isEmergency = usage.emergencyUsed &&
            timeProvider.currentTimeMillis() < usage.emergencyExpiresAt &&
            (timeProvider.elapsedRealtime() - usage.emergencyElapsedRealtimeAtStart) < EMERGENCY_DURATION_MILLIS
        if (isEmergency) return false
        return usage.isBlocked || (app.dailyLimitSeconds in 0L..usage.usedSeconds)
    }

    fun getSecondsUntilNextDailyReset(): Long {
        val diff = timeProvider.getNextMidnightMillis() - timeProvider.currentTimeMillis()
        return (diff / 1000).coerceAtLeast(0L)
    }
}
