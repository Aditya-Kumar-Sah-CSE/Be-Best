package com.focuslock.app.presentation.dashboard

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.FocusLockApp
import com.focuslock.app.core.util.BatteryOptimizationHelper
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.local.entity.FocusSessionEntity
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.domain.model.ProtectionHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardCardItem(
    val packageName: String,
    val displayName: String,
    val category: String,
    val usedSeconds: Long,
    val quotaSeconds: Long,
    val isBlocked: Boolean,
    val isWarning: Boolean,
    val appIcon: Drawable? = null
) {
    val remainingSeconds: Long
        get() = (quotaSeconds - usedSeconds).coerceAtLeast(0L)

    val progressFraction: Float
        get() = if (quotaSeconds > 0) (usedSeconds.toFloat() / quotaSeconds.toFloat()).coerceIn(0f, 1f) else 0f
}

data class DashboardUiState(
    val totalUsedSeconds: Long = 0L,
    val totalDailyQuotaSeconds: Long = 0L,
    val totalRemainingSeconds: Long = 0L,
    val totalTimeSavedSeconds: Long? = null,
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
    private val pm = application.packageManager

    private val currentDateFlow = MutableStateFlow(TimeUtils.getTodayDateString())

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
        startMidnightCountdownTicker()
    }

    fun refreshHealth() {
        val context = getApplication<Application>()
        val hasUsage = checkUsageAccess()
        val hasAccessibility = checkAccessibilityEnabled()
        val hasBattery = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)

        val todayDate = TimeUtils.getTodayDateString()
        if (currentDateFlow.value != todayDate) {
            currentDateFlow.value = todayDate
            viewModelScope.launch(Dispatchers.IO) {
                app.quotaEngine.checkAndPerformMidnightReset()
            }
        }

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

    private fun startMidnightCountdownTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val todayDate = TimeUtils.getTodayDateString()
                if (currentDateFlow.value != todayDate) {
                    currentDateFlow.value = todayDate
                    withContext(Dispatchers.IO) {
                        app.quotaEngine.checkAndPerformMidnightReset()
                    }
                }
                _uiState.value = _uiState.value.copy(
                    secondsUntilMidnight = TimeUtils.getSecondsUntilNextMidnight()
                )
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadDashboardData() {
        refreshHealth()

        viewModelScope.launch {
            currentDateFlow.flatMapLatest { today ->
                combine(
                    appRepo.getProtectedAppsFlow(),
                    usageRepo.getDailyUsageFlow(today),
                    focusSessionDao.getActiveSessionFlow()
                ) { protectedApps: List<ProtectedAppEntity>, dailyUsages: List<DailyUsageEntity>, activeFocus: FocusSessionEntity? ->
                    val usageMap = dailyUsages.associateBy { it.packageName }
                    val activeProtected = protectedApps.filter { it.enabled }

                    val cards = withContext(Dispatchers.IO) {
                        activeProtected.map { appEntity ->
                            val usage = usageMap[appEntity.packageName]
                            val usedSecs = usage?.usedSeconds ?: 0L
                            val quotaSecs = appEntity.dailyLimitSeconds
                            val blocked = usage?.isBlocked ?: (usedSecs >= quotaSecs && quotaSecs > 0)
                            val warning = !blocked && quotaSecs > 0 && usedSecs >= (quotaSecs * 0.75)

                            val icon = try {
                                pm.getApplicationIcon(appEntity.packageName)
                            } catch (e: Exception) {
                                null
                            }

                            DashboardCardItem(
                                packageName = appEntity.packageName,
                                displayName = appEntity.displayName,
                                category = appEntity.category,
                                usedSeconds = usedSecs,
                                quotaSeconds = quotaSecs,
                                isBlocked = blocked,
                                isWarning = warning,
                                appIcon = icon
                            )
                        }
                    }

                    val totalUsed = cards.sumOf { it.usedSeconds }
                    val totalQuotas = cards.sumOf { it.quotaSeconds }
                    val totalRemaining = (totalQuotas - totalUsed).coerceAtLeast(0L)
                    val blockedCount = cards.count { it.isBlocked }

                    // Sort: first blocked, then nearing limit, then highest usage
                    val closeToLimit = cards
                        .filter { it.isWarning || it.isBlocked || it.progressFraction > 0.5f }
                        .sortedByDescending { it.progressFraction }

                    // Time saved: calculated if user stayed under their assigned quotas
                    val timeSaved = if (totalQuotas > 0 && totalUsed < totalQuotas) {
                        (totalQuotas - totalUsed).coerceAtLeast(0L)
                    } else null

                    DashboardUiState(
                        totalUsedSeconds = totalUsed,
                        totalDailyQuotaSeconds = totalQuotas,
                        totalRemainingSeconds = totalRemaining,
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
                }
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
