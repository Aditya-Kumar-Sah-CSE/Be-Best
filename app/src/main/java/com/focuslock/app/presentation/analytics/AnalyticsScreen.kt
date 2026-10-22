package com.focuslock.app.presentation.analytics

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
import com.focuslock.app.core.designsystem.BrandTeal
import com.focuslock.app.core.designsystem.DangerRed
import com.focuslock.app.core.designsystem.DarkBackground
import com.focuslock.app.core.designsystem.DarkBorder
import com.focuslock.app.core.designsystem.DarkBorderSubtle
import com.focuslock.app.core.designsystem.DarkSurface
import com.focuslock.app.core.designsystem.DarkSurfaceElevated
import com.focuslock.app.core.designsystem.EmptyState
import com.focuslock.app.core.designsystem.FocusCard
import com.focuslock.app.core.designsystem.FocusLockBottomBar
import com.focuslock.app.core.designsystem.FocusLockTopBar
import com.focuslock.app.core.designsystem.Neutral200
import com.focuslock.app.core.designsystem.Neutral400
import com.focuslock.app.core.designsystem.SuccessGreen
import com.focuslock.app.core.designsystem.UsageBarChart
import com.focuslock.app.core.util.TimeUtils

@Composable
fun AnalyticsScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToFocus: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAppDetails: (String) -> Unit,
    viewModel: AnalyticsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = com.focuslock.app.core.designsystem.AppTheme.colors.background,
        bottomBar = {
            FocusLockBottomBar(
                currentRoute = "analytics",
                onNavigateToHome = onNavigateToHome,
                onNavigateToApps = onNavigateToApps,
                onNavigateToFocus = onNavigateToFocus,
                onNavigateToAnalytics = { /* Already on analytics */ },
                onNavigateToSettings = onNavigateToSettings
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Screen 10 Top Bar: Star icon + "Analytics"
            FocusLockTopBar(
                title = "Analytics",
                navigationIcon = Icons.Default.Insights
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Tabs: Daily | Weekly | Monthly (Screen 10)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AnalyticsTimeFilter.entries.forEach { filter ->
                    val isSelected = uiState.selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) BrandIndigo else Color.Transparent)
                            .clickable { viewModel.setFilter(filter) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Neutral400
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandCyan, strokeWidth = 3.dp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Screen 10: Total Usage Card with Dynamic Bar Chart
                    item {
                        FocusCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            backgroundColor = DarkSurface,
                            borderColor = DarkBorder,
                            contentPadding = 18.dp
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Total Screen Time on Left
                                    Column {
                                        Text(
                                            text = "Total Usage",
                                            fontSize = 12.sp,
                                            color = Neutral400
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = TimeUtils.formatDurationShort(uiState.totalUsageSeconds),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Trend Chip
                                        if (uiState.comparisonPercentage != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (uiState.isIncrease) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                                    contentDescription = null,
                                                    tint = if (uiState.isIncrease) DangerRed else SuccessGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${if (uiState.isIncrease) "+" else "-"}${uiState.comparisonPercentage}% vs yesterday",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (uiState.isIncrease) DangerRed else SuccessGreen
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Baseline measuring today",
                                                fontSize = 11.sp,
                                                color = Neutral400
                                            )
                                        }
                                    }

                                    // Bar Chart on Right (Screen 10)
                                    if (uiState.dayValues.isNotEmpty()) {
                                        Box(modifier = Modifier.width(160.dp)) {
                                            UsageBarChart(
                                                dayLabels = uiState.dayLabels,
                                                values = uiState.dayValues,
                                                selectedIndex = uiState.selectedDayIndex,
                                                onSelectDay = { viewModel.selectDay(it) },
                                                barColor = BrandCyan,
                                                selectedBarColor = BrandIndigo
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkSurfaceElevated)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Continuous Screen-On",
                                            fontSize = 11.5.sp,
                                            color = Neutral400
                                        )
                                        Text(
                                            text = TimeUtils.formatDurationShort(uiState.continuousScreenTimeTodaySeconds),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandTeal
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Source",
                                            fontSize = 11.5.sp,
                                            color = Neutral400
                                        )
                                        Text(
                                            text = "UsageEvents Ground Truth",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SuccessGreen
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section: App Usage (Screen 10)
                    item {
                        Text(
                            text = "App Usage",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (uiState.appUsageList.isEmpty()) {
                        item {
                            EmptyState(
                                message = "No measured app usage in this period",
                                subMessage = "Foreground screen time will automatically populate here as monitored apps are used."
                            )
                        }
                    } else {
                        items(uiState.appUsageList, key = { it.packageName }) { appItem ->
                            AnalyticsAppUsageRow(
                                item = appItem,
                                onClick = { onNavigateToAppDetails(appItem.packageName) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

/**
 * Screen 10 App Usage Breakdown Item
 */
@Composable
private fun AnalyticsAppUsageRow(
    item: AppUsageBreakdownItem,
    onClick: () -> Unit
) {
    FocusCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 16.dp,
        backgroundColor = DarkSurface,
        borderColor = DarkBorder,
        contentPadding = 14.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (item.appIcon != null) {
                        val bitmap = item.appIcon.toBitmap(64, 64, Bitmap.Config.ARGB_8888)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = item.displayName,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                        ) {
                            Text(
                                text = item.displayName.take(1).uppercase(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = item.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TimeUtils.formatDurationShort(item.usedSeconds),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "${item.percentage}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Neutral400
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { item.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = BrandIndigo,
                trackColor = DarkBorderSubtle,
                strokeCap = StrokeCap.Round
            )
        }
    }
}
