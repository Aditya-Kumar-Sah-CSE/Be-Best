package com.focuslock.app.presentation.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.FocusLockApp
import com.focuslock.app.core.util.BatteryOptimizationHelper
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.FocusSessionEntity
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.domain.engine.QuotaStatus
import com.focuslock.app.domain.model.ProtectionHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DashboardCardItem(
    val packageName: String,
    val displayName: String,
    val category: String,
    val usedSeconds: Long,
    val quotaSeconds: Long,
    val isBlocked: Boolean,
    val isWarning: Boolean
) {
    val remainingSeconds: Long
        get() = (quotaSeconds - usedSeconds).coerceAtLeast(0L)

    val progressFraction: Float
        get() = if (quotaSeconds > 0) (usedSeconds.toFloat() / quotaSeconds.toFloat()).coerceIn(0f, 1f) else 0f
}

data class DashboardUiState(
    val totalUsedSeconds: Long = 0L,
    val totalTimeSavedSeconds: Long? = null, // null if no historical baseline available
    val protectedAppsCount: Int = 0,
    val blockedAppsCount: Int = 0,
    val appsCloseToLimit: List<DashboardCardItem> = emptyList(),
    val appCards: List<DashboardCardItem> = emptyList(),
    val activeFocusSession: FocusSessionEntity? = null,
    val protectionHealth: ProtectionHealth = ProtectionHealth(false, false, false, false),
    val isHardcoreActive: Boolean = false,
    val secondsUntilMidnight: Long = 0L
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApp
    private val appRepo = app.appRepository
    private val usageRepo = app.usageRepository
    private val securityManager = app.pinSecurityManager
    private val focusSessionDao = app.database.focusSessionDao()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    fun refreshHealth() {
        val context = getApplication<Application>()
        val hasUsage = checkUsageAccess()
        val hasAccessibility = checkAccessibilityEnabled()
        val hasBattery = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)

        _uiState.value = _uiState.value.copy(
            protectionHealth = ProtectionHealth(
                isUsageAccessGranted = hasUsage,
                isAccessibilityEnabled = hasAccessibility,
                isBatteryOptimizationIgnored = hasBattery,
                isOverlayPermissionGranted = true
            ),
            isHardcoreActive = securityManager.isHardcoreModeActive(),
            secondsUntilMidnight = TimeUtils.getSecondsUntilNextMidnight()
        )
    }

    private fun loadDashboardData() {
        refreshHealth()
        val today = TimeUtils.getTodayDateString()

        viewModelScope.launch {
            combine(
                appRepo.getProtectedAppsFlow(),
                usageRepo.getDailyUsageFlow(today),
                focusSessionDao.getActiveSessionFlow()
            ) { protectedApps: List<ProtectedAppEntity>, dailyUsages: List<DailyUsageEntity>, activeFocus: FocusSessionEntity? ->
                val usageMap = dailyUsages.associateBy { it.packageName }
                val activeProtected = protectedApps.filter { it.enabled }

                val cards = activeProtected.map { appEntity ->
                    val usage = usageMap[appEntity.packageName]
                    val usedSecs = usage?.usedSeconds ?: 0L
                    val quotaSecs = appEntity.dailyLimitSeconds
                    val blocked = usage?.isBlocked ?: (usedSecs >= quotaSecs && quotaSecs > 0)
                    val warning = !blocked && quotaSecs > 0 && usedSecs >= (quotaSecs * 0.8)

                    DashboardCardItem(
                        packageName = appEntity.packageName,
                        displayName = appEntity.displayName,
                        category = appEntity.category,
                        usedSeconds = usedSecs,
                        quotaSeconds = quotaSecs,
                        isBlocked = blocked,
                        isWarning = warning
                    )
                }

                val totalUsed = cards.sumOf { it.usedSeconds }
                val blockedCount = cards.count { it.isBlocked }
                val closeToLimit = cards.filter { it.isWarning || it.isBlocked }

                // Time saved: calculated if user stayed under their assigned quotas
                val totalQuotas = cards.sumOf { it.quotaSeconds }
                val timeSaved = if (totalQuotas > 0 && totalUsed < totalQuotas) {
                    (totalQuotas - totalUsed).coerceAtLeast(0L)
                } else null

                DashboardUiState(
                    totalUsedSeconds = totalUsed,
                    totalTimeSavedSeconds = timeSaved,
                    protectedAppsCount = activeProtected.size,
                    blockedAppsCount = blockedCount,
                    appsCloseToLimit = closeToLimit,
                    appCards = cards,
                    activeFocusSession = activeFocus,
                    protectionHealth = _uiState.value.protectionHealth,
                    isHardcoreActive = securityManager.isHardcoreModeActive(),
                    secondsUntilMidnight = TimeUtils.getSecondsUntilNextMidnight()
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun checkUsageAccess(): Boolean {
        val appOps = getApplication<Application>().getSystemService(Application.APP_OPS_SERVICE) as? android.app.AppOpsManager
            ?: return false
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getApplication<Application>().packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getApplication<Application>().packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun checkAccessibilityEnabled(): Boolean {
        val context = getApplication<Application>()
        val expectedService = "${context.packageName}/${com.focuslock.app.service.FocusLockAccessibilityService::class.java.canonicalName}"
        val enabledServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedService)
    }
}
