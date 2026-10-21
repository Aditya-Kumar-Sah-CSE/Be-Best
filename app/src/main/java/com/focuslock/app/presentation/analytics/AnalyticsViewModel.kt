package com.focuslock.app.presentation.analytics

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.app.FocusLockApp
import com.focuslock.app.core.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class AnalyticsTimeFilter(val title: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

data class AppUsageBreakdownItem(
    val packageName: String,
    val displayName: String,
    val category: String,
    val usedSeconds: Long,
    val percentage: Int,
    val progressFraction: Float,
    val appIcon: Drawable? = null
)

data class AnalyticsUiState(
    val selectedFilter: AnalyticsTimeFilter = AnalyticsTimeFilter.DAILY,
    val totalUsageSeconds: Long = 0L,
    val comparisonPercentage: Int? = null,
    val isIncrease: Boolean = false,
    val dayLabels: List<String> = emptyList(),
    val dayValues: List<Float> = emptyList(),
    val selectedDayIndex: Int = 6,
    val appUsageList: List<AppUsageBreakdownItem> = emptyList(),
    val isLoading: Boolean = false
)

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApp
    private val usageDao = app.database.usageDao()
    private val appRepo = app.appRepository
    private val pm = application.packageManager

    private val _uiState = MutableStateFlow(AnalyticsUiState(isLoading = true))
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics(AnalyticsTimeFilter.DAILY)
    }

    fun setFilter(filter: AnalyticsTimeFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter, isLoading = true)
        loadAnalytics(filter)
    }

    fun selectDay(index: Int) {
        _uiState.value = _uiState.value.copy(selectedDayIndex = index)
    }

    private fun loadAnalytics(filter: AnalyticsTimeFilter) {
        viewModelScope.launch {
            val (totalUsage, dayLabels, dayValues, comparisons, appsList) = withContext(Dispatchers.IO) {
                val cal = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dayFormat = SimpleDateFormat("EEE", Locale.US)

                val daysCount = when (filter) {
                    AnalyticsTimeFilter.DAILY, AnalyticsTimeFilter.WEEKLY -> 7
                    AnalyticsTimeFilter.MONTHLY -> 30
                }

                val labels = mutableListOf<String>()
                val values = mutableListOf<Float>()
                val dates = mutableListOf<String>()

                for (i in (daysCount - 1) downTo 0) {
                    val c = Calendar.getInstance()
                    c.add(Calendar.DAY_OF_YEAR, -i)
                    val dateStr = dateFormat.format(c.time)
                    dates.add(dateStr)
                    labels.add(dayFormat.format(c.time).take(1).uppercase())
                    val dayUsage = usageDao.getTotalUsedSecondsForDate(dateStr) ?: 0L
                    values.add(dayUsage.toFloat())
                }

                // Current period total
                val todayStr = TimeUtils.getTodayDateString()
                val todayUsage = usageDao.getTotalUsedSecondsForDate(todayStr) ?: 0L
                val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                val yesterdayStr = dateFormat.format(yesterdayCal.time)
                val yesterdayUsage = usageDao.getTotalUsedSecondsForDate(yesterdayStr) ?: 0L

                // Trend comparison
                val (compPercent, increase) = if (yesterdayUsage > 0) {
                    val diff = todayUsage - yesterdayUsage
                    val pct = ((diff.toDouble() / yesterdayUsage.toDouble()) * 100).toInt()
                    Pair(kotlin.math.abs(pct), diff >= 0)
                } else {
                    Pair(null, false)
                }

                // Query apps and group usage
                val activeApps = appRepo.getActiveProtectedApps()
                val appItems = mutableListOf<AppUsageBreakdownItem>()

                for (appEntity in activeApps) {
                    var totalAppSeconds = 0L
                    when (filter) {
                        AnalyticsTimeFilter.DAILY -> {
                            val u = usageDao.getDailyUsageForApp(appEntity.packageName, todayStr)
                            totalAppSeconds = u?.usedSeconds ?: 0L
                        }
                        AnalyticsTimeFilter.WEEKLY, AnalyticsTimeFilter.MONTHLY -> {
                            for (date in dates) {
                                val u = usageDao.getDailyUsageForApp(appEntity.packageName, date)
                                totalAppSeconds += (u?.usedSeconds ?: 0L)
                            }
                        }
                    }

                    if (totalAppSeconds > 0) {
                        val icon = try {
                            pm.getApplicationIcon(appEntity.packageName)
                        } catch (e: Exception) {
                            null
                        }

                        val overallTotal = if (filter == AnalyticsTimeFilter.DAILY) todayUsage else values.sum().toLong()
                        val pct = if (overallTotal > 0) {
                            ((totalAppSeconds.toFloat() / overallTotal.toFloat()) * 100).toInt()
                        } else 0

                        appItems.add(
                            AppUsageBreakdownItem(
                                packageName = appEntity.packageName,
                                displayName = appEntity.displayName,
                                category = appEntity.category,
                                usedSeconds = totalAppSeconds,
                                percentage = pct,
                                progressFraction = if (overallTotal > 0) totalAppSeconds.toFloat() / overallTotal.toFloat() else 0f,
                                appIcon = icon
                            )
                        )
                    }
                }

                // Sort apps by usage descending
                appItems.sortByDescending { it.usedSeconds }

                val periodTotal = if (filter == AnalyticsTimeFilter.DAILY) todayUsage else values.sum().toLong()

                return@withContext Tuple5(periodTotal, labels, values, Pair(compPercent, increase), appItems)
            }

            _uiState.value = AnalyticsUiState(
                selectedFilter = filter,
                totalUsageSeconds = totalUsage,
                comparisonPercentage = comparisons.first,
                isIncrease = comparisons.second,
                dayLabels = dayLabels,
                dayValues = dayValues,
                selectedDayIndex = dayLabels.lastIndex.coerceAtLeast(0),
                appUsageList = appsList,
                isLoading = false
            )
        }
    }

    private data class Tuple5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
