package com.focuslock.app.presentation.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.FocusLockApp
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.entity.DailyUsageEntity
import com.focuslock.app.data.repository.InstalledAppItem
import com.focuslock.app.domain.model.AppCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

enum class AppSortOrder(val title: String) {
    USAGE_DESC("Highest Usage"),
    NAME_ASC("Name (A-Z)"),
    REMAINING_ASC("Lowest Remaining")
}

data class AppItemUiModel(
    val item: InstalledAppItem,
    val usedSecondsToday: Long,
    val remainingSeconds: Long,
    val isBlocked: Boolean
) {
    val progressFraction: Float
        get() = if (item.dailyLimitSeconds > 0) (usedSecondsToday.toFloat() / item.dailyLimitSeconds.toFloat()).coerceIn(0f, 1f) else 0f
}

data class AppSelectionUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedCategory: AppCategory? = null,
    val sortOrder: AppSortOrder = AppSortOrder.USAGE_DESC,
    val allApps: List<AppItemUiModel> = emptyList(),
    val filteredApps: List<AppItemUiModel> = emptyList(),
    val suggestedCount: Int = 0
)

class AppSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApp
    private val appRepo = app.appRepository
    private val usageRepo = app.usageRepository
    private val realUsageTracker = app.realUsageTracker

    private val _uiState = MutableStateFlow(AppSelectionUiState())
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val installedApps = appRepo.getInstalledLaunchableApps()
            val targetPackages = installedApps.map { it.packageName }

            // Reconcile with Android UsageStatsManager
            try {
                realUsageTracker.reconcileWithAndroidUsageStats(targetPackages)
            } catch (e: Exception) {
                // Ignore if usage access permission is denied
            }

            val todayDate = TimeUtils.getTodayDateString()
            val dailyUsages = usageRepo.getDailyUsageFlow(todayDate).firstOrNull() ?: emptyList()
            val usageMap = dailyUsages.associateBy { it.packageName }

            val uiModels = installedApps.map { appItem ->
                val usage = usageMap[appItem.packageName]
                val used = usage?.usedSeconds ?: 0L
                val remaining = (appItem.dailyLimitSeconds - used).coerceAtLeast(0L)
                val isBlocked = usage?.isBlocked ?: (appItem.dailyLimitSeconds > 0 && used >= appItem.dailyLimitSeconds)

                AppItemUiModel(
                    item = appItem,
                    usedSecondsToday = used,
                    remainingSeconds = remaining,
                    isBlocked = isBlocked
                )
            }

            val suggestedDistractionsCount = uiModels.count { it.item.category.isSuggestedDistraction }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                allApps = uiModels,
                filteredApps = filterAndSort(uiModels, _uiState.value.searchQuery, _uiState.value.selectedCategory, _uiState.value.sortOrder),
                suggestedCount = suggestedDistractionsCount
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredApps = filterAndSort(_uiState.value.allApps, query, _uiState.value.selectedCategory, _uiState.value.sortOrder)
        )
    }

    fun onCategorySelected(category: AppCategory?) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredApps = filterAndSort(_uiState.value.allApps, _uiState.value.searchQuery, category, _uiState.value.sortOrder)
        )
    }

    fun onSortOrderSelected(order: AppSortOrder) {
        _uiState.value = _uiState.value.copy(
            sortOrder = order,
            filteredApps = filterAndSort(_uiState.value.allApps, _uiState.value.searchQuery, _uiState.value.selectedCategory, order)
        )
    }

    fun toggleAppProtection(appItem: InstalledAppItem) {
        viewModelScope.launch {
            appRepo.setAppProtection(
                packageName = appItem.packageName,
                appName = appItem.appName,
                enabled = !appItem.isProtected,
                limitSeconds = appItem.dailyLimitSeconds,
                category = appItem.category
            )
            loadApps()
        }
    }

    fun updateAppLimit(packageName: String, limitSeconds: Long) {
        viewModelScope.launch {
            appRepo.updateAppLimit(packageName, limitSeconds)
            loadApps()
        }
    }

    fun selectAllSuggestedDistractions() {
        viewModelScope.launch {
            appRepo.selectAllSuggestedDistractions(1800L)
            loadApps()
        }
    }

    fun deselectAll() {
        viewModelScope.launch {
            appRepo.deselectAllApps()
            loadApps()
        }
    }

    private fun filterAndSort(
        list: List<AppItemUiModel>,
        query: String,
        category: AppCategory?,
        sortOrder: AppSortOrder
    ): List<AppItemUiModel> {
        val filtered = list.filter { model ->
            val matchesQuery = query.isBlank() ||
                model.item.appName.contains(query, ignoreCase = true) ||
                model.item.packageName.contains(query, ignoreCase = true)
            val matchesCategory = category == null || model.item.category == category
            matchesQuery && matchesCategory
        }

        return when (sortOrder) {
            AppSortOrder.USAGE_DESC -> filtered.sortedByDescending { it.usedSecondsToday }
            AppSortOrder.NAME_ASC -> filtered.sortedBy { it.item.appName.lowercase() }
            AppSortOrder.REMAINING_ASC -> filtered.sortedWith(
                compareBy<AppItemUiModel> { !it.item.isProtected }.thenBy { it.remainingSeconds }
            )
        }
    }
}
