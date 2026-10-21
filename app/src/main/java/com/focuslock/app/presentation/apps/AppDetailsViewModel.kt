package com.focuslock.app.presentation.apps

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.FocusLockApp
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.entity.ProtectedAppEntity
import com.focuslock.app.data.local.entity.UsageSessionEntity
import com.focuslock.app.domain.engine.AppQuotaEvaluation
import com.focuslock.app.domain.model.AppCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppDetailsUiState(
    val packageName: String = "",
    val displayName: String = "",
    val category: AppCategory = AppCategory.OTHER,
    val isProtected: Boolean = false,
    val dailyLimitSeconds: Long = 1800L,
    val usedSecondsToday: Long = 0L,
    val remainingSeconds: Long = 1800L,
    val isBlocked: Boolean = false,
    val blockedReason: String? = null,
    val recentSessions: List<UsageSessionEntity> = emptyList(),
    val appIcon: Drawable? = null,
    val isReelsOrShortsParent: Boolean = false
)

class AppDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApp
    private val appRepo = app.appRepository
    private val usageRepo = app.usageRepository
    private val quotaEngine = app.quotaEngine

    private val _uiState = MutableStateFlow(AppDetailsUiState())
    val uiState: StateFlow<AppDetailsUiState> = _uiState.asStateFlow()

    fun loadAppDetails(packageName: String) {
        viewModelScope.launch {
            val protectedApp = appRepo.getApp(packageName)
            val evaluation = quotaEngine.evaluateQuota(packageName)
            val icon = appRepo.getAppIcon(packageName)

            val isParentReelsOrShorts = packageName.contains("instagram") || packageName.contains("youtube")

            val appName = protectedApp?.displayName
                ?: try {
                    val info = getApplication<Application>().packageManager.getApplicationInfo(packageName, 0)
                    getApplication<Application>().packageManager.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    packageName
                }

            val category = protectedApp?.let { AppCategory.fromString(it.category) }
                ?: AppCategory.inferCategory(packageName)

            _uiState.value = AppDetailsUiState(
                packageName = packageName,
                displayName = appName,
                category = category,
                isProtected = protectedApp?.enabled == true,
                dailyLimitSeconds = evaluation.dailyLimitSeconds,
                usedSecondsToday = evaluation.usedSeconds,
                remainingSeconds = evaluation.remainingSeconds,
                isBlocked = evaluation.isBlocked,
                blockedReason = evaluation.blockedReason,
                recentSessions = emptyList(),
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
