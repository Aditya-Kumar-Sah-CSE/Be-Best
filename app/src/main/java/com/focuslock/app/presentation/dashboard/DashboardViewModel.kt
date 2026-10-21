package com.focuslock.app.presentation.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.FocusLockApp
import com.focuslock.app.core.util.BatteryOptimizationHelper
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.domain.model.ProtectionHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class AppUsageCardData(
    val packageName: String,
    val displayName: String,
    val category: String,
    val usedSeconds: Long,
    val quotaSeconds: Long,
    val isBlocked: Boolean
) {
    val remainingSeconds: Long
        get() = (quotaSeconds - usedSeconds).coerceAtLeast(0L)

    val progressFraction: Float
        get() = if (quotaSeconds > 0) (usedSeconds.toFloat() / quotaSeconds.toFloat()).coerceIn(0f, 1f) else 0f
}

data class DashboardUiState(
    val totalUsedSeconds: Long = 0L,
    val blockedAppsCount: Int = 0,
    val activeAppsCount: Int = 0,
    val appCards: List<AppUsageCardData> = emptyList(),
    val protectionHealth: ProtectionHealth = ProtectionHealth(false, false, false, false),
    val isHardcoreActive: Boolean = false,
    val secondsUntilMidnight: Long = 0L
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApp
    private val appRepo = app.appRepository
    private val usageRepo = app.usageRepository
    private val securityManager = app.pinSecurityManager

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
                usageRepo.getDailyUsageFlow(today)
            ) { protectedApps: List<ProtectedAppEntity>, dailyUsages: List<DailyUsageEntity> ->
                val usageMap = dailyUsages.associateBy { it.packageName }

                val cards = protectedApps.filter { it.enabled }.map { appEntity ->
                    val usage = usageMap[appEntity.packageName]
                    val usedSecs = usage?.usedSeconds ?: 0L
                    val quotaSecs = appEntity.dailyLimitSeconds
                    val blocked = usage?.isBlocked ?: (usedSecs >= quotaSecs)

                    AppUsageCardData(
                        packageName = appEntity.packageName,
                        displayName = appEntity.displayName,
                        category = appEntity.category,
                        usedSeconds = usedSecs,
                        quotaSeconds = quotaSecs,
                        isBlocked = blocked
                    )
                }

                val totalUsed = cards.sumOf { it.usedSeconds }
                val blockedCount = cards.count { it.isBlocked }
                val activeCount = cards.count { !it.isBlocked }

                DashboardUiState(
                    totalUsedSeconds = totalUsed,
                    blockedAppsCount = blockedCount,
                    activeAppsCount = activeCount,
                    appCards = cards,
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
