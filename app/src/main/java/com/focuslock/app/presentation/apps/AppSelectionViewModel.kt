package com.focuslock.app.presentation.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.FocusLockApp
import com.focuslock.app.data.repository.InstalledAppInfo
import com.focuslock.app.domain.model.AppCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppSelectionUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedCategory: AppCategory? = null,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val filteredApps: List<InstalledAppInfo> = emptyList()
)

class AppSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepo = (application as FocusLockApp).appRepository

    private val _uiState = MutableStateFlow(AppSelectionUiState())
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val apps = appRepo.getInstalledApps()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                installedApps = apps,
                filteredApps = applyFilters(apps, _uiState.value.searchQuery, _uiState.value.selectedCategory)
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredApps = applyFilters(_uiState.value.installedApps, query, _uiState.value.selectedCategory)
        )
    }

    fun onCategorySelected(category: AppCategory?) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredApps = applyFilters(_uiState.value.installedApps, _uiState.value.searchQuery, category)
        )
    }

    fun toggleAppProtection(app: InstalledAppInfo) {
        viewModelScope.launch {
            val updatedStatus = !app.isProtected
            appRepo.setAppProtection(
                packageName = app.packageName,
                appName = app.appName,
                enabled = updatedStatus,
                limitSeconds = app.dailyLimitSeconds,
                category = app.category
            )
            loadInstalledApps()
        }
    }

    fun updateAppLimit(app: InstalledAppInfo, limitSeconds: Long) {
        viewModelScope.launch {
            appRepo.updateAppLimit(app.packageName, limitSeconds)
            loadInstalledApps()
        }
    }

    private fun applyFilters(
        list: List<InstalledAppInfo>,
        query: String,
        category: AppCategory?
    ): List<InstalledAppInfo> {
        return list.filter { app ->
            val matchesQuery = query.isBlank() || app.appName.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
            val matchesCategory = category == null || app.category == category
            matchesQuery && matchesCategory
        }
    }
}
