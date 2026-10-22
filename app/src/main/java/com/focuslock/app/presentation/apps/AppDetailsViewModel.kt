package com.focuslock.app.presentation.apps

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.FocusLockApp
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.domain.engine.AppQuotaEvaluation
import com.focuslock.app.domain.model.AppCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

data class AppDetailsUiState(
    val packageName: String = "",
    val displayName: String = "",
    val category: AppCategory = AppCategory.OTHER,
    val isProtected: Boolean = false,
    val dailyLimitSeconds: Long = 1800L,
    val usedSecondsToday: Long = 0L,
    val remainingSeconds: Long = 1800L,
    val usedSecondsYesterday: Long = 0L,
    val usedSecondsLast7Days: Long = 0L,
    val isBlocked: Boolean = false,
    val blockedReason: String? = null,
    val appIcon: Drawable? = null,
    val isReelsOrShortsParent: Boolean = false
) {
    val consumedPercentage: Int
        get() = if (dailyLimitSeconds > 0) {
            ((usedSecondsToday.toFloat() / dailyLimitSeconds.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else 0
}

class AppDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApp
    private val appRepo = app.appRepository
    private val usageDao = app.database.usageDao()
    private val quotaEngine = app.quotaEngine

    private val _uiState = MutableStateFlow(AppDetailsUiState())
    val uiState: StateFlow<AppDetailsUiState> = _uiState.asStateFlow()

    fun loadAppDetails(packageName: String) {
        viewModelScope.launch {
            try {
                app.realUsageTracker.syncPackageUsage(packageName)
            } catch (e: Exception) {
                // Ignore if usage access not granted
            }
            val protectedApp = appRepo.getApp(packageName)
            val evaluation = quotaEngine.evaluateQuota(packageName)
            val icon = appRepo.getAppIcon(packageName)

            val isParentReelsOrShorts = packageName.contains("instagram", ignoreCase = true) ||
                    packageName.contains("youtube", ignoreCase = true)

            val appName = protectedApp?.displayName
                ?: try {
                    val info = getApplication<Application>().packageManager.getApplicationInfo(packageName, 0)
                    getApplication<Application>().packageManager.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    packageName
                }

            val category = protectedApp?.let { AppCategory.fromString(it.category) }
                ?: AppCategory.inferCategory(packageName)

            // Query genuine Room historical usage
            val (yesterdayUsage, last7DaysUsage) = withContext(Dispatchers.IO) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val yesterdayStr = String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                val yUsage = usageDao.getDailyUsageForApp(packageName, yesterdayStr)?.usedSeconds ?: 0L

                var sevenDaysTotal = evaluation.usedSeconds + yUsage
                for (i in 2..6) {
                    val pastCal = Calendar.getInstance()
                    pastCal.add(Calendar.DAY_OF_YEAR, -i)
                    val pastDateStr = String.format(Locale.US, "%04d-%02d-%02d", pastCal.get(Calendar.YEAR), pastCal.get(Calendar.MONTH) + 1, pastCal.get(Calendar.DAY_OF_MONTH))
                    val pastUsage = usageDao.getDailyUsageForApp(packageName, pastDateStr)?.usedSeconds ?: 0L
                    sevenDaysTotal += pastUsage
                }
                Pair(yUsage, sevenDaysTotal)
            }

            _uiState.value = AppDetailsUiState(
                packageName = packageName,
                displayName = appName,
                category = category,
                isProtected = protectedApp?.enabled == true,
                dailyLimitSeconds = evaluation.dailyLimitSeconds,
                usedSecondsToday = evaluation.usedSeconds,
                remainingSeconds = evaluation.remainingSeconds,
                usedSecondsYesterday = yesterdayUsage,
                usedSecondsLast7Days = last7DaysUsage,
                isBlocked = evaluation.isBlocked,
                blockedReason = evaluation.blockedReason,
                appIcon = icon,
                isReelsOrShortsParent = isParentReelsOrShorts
            )
        }
    }

    fun updateLimit(limitSeconds: Long) {
        val pkg = _uiState.value.packageName
        if (pkg.isBlank()) return
        viewModelScope.launch {
            appRepo.updateAppLimit(pkg, limitSeconds)
            loadAppDetails(pkg)
        }
    }

    fun toggleProtection() {
        val state = _uiState.value
        if (state.packageName.isBlank()) return
        viewModelScope.launch {
            val newStatus = !state.isProtected
            appRepo.setAppProtection(
                packageName = state.packageName,
                appName = state.displayName,
                enabled = newStatus,
                limitSeconds = state.dailyLimitSeconds,
                category = state.category
            )
            loadAppDetails(state.packageName)
        }
    }
}
