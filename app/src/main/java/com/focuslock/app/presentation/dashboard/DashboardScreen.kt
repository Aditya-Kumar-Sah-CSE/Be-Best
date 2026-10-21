package com.focuslock.app.presentation.dashboard

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
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslock.app.R
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
import com.focuslock.app.core.designsystem.BrandTeal
import com.focuslock.app.core.designsystem.BrandViolet
import com.focuslock.app.core.designsystem.DangerRed
import com.focuslock.app.core.designsystem.DarkBackground
import com.focuslock.app.core.designsystem.DarkBorder
import com.focuslock.app.core.designsystem.DarkBorderSubtle
import com.focuslock.app.core.designsystem.DarkSurface
import com.focuslock.app.core.designsystem.DarkSurfaceElevated
import com.focuslock.app.core.designsystem.FocusCard
import com.focuslock.app.core.designsystem.FocusLockBottomBar
import com.focuslock.app.core.designsystem.FocusLockTopBar
import com.focuslock.app.core.designsystem.Neutral200
import com.focuslock.app.core.designsystem.Neutral400
import com.focuslock.app.core.designsystem.Neutral600
import com.focuslock.app.core.designsystem.SuccessGreen
import com.focuslock.app.core.designsystem.UsageProgressRing
import com.focuslock.app.core.designsystem.WarningAmber
import com.focuslock.app.core.util.TimeUtils

@Composable
fun DashboardScreen(
    onNavigateToApps: () -> Unit,
    onNavigateToAppDetails: (String) -> Unit,
    onNavigateToFocus: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshHealth()
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            FocusLockBottomBar(
                currentRoute = "dashboard",
                onNavigateToHome = { /* Already on dashboard */ },
                onNavigateToApps = onNavigateToApps,
                onNavigateToFocus = onNavigateToFocus,
                onNavigateToAnalytics = onNavigateToAnalytics,
                onNavigateToSettings = onNavigateToSettings
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Screen 4 Top Header: FocusLock logo + Title + Settings gear
            item {
                Spacer(modifier = Modifier.height(8.dp))
                FocusLockTopBar(
                    title = "FocusLock",
                    showLogo = true,
                    actionIcon = Icons.Default.Settings,
                    onActionClick = onNavigateToSettings
                )
            }

            // Health Status banner if monitoring is incomplete
            if (!uiState.protectionHealth.isFullyProtected) {
                item {
                    MonitoringIncompleteBanner(onFixClick = onNavigateToSettings)
                }
            }

            // Today's Usage Hero Card (Matching Screen 4)
            item {
                TodayUsageCard(
                    totalUsedSeconds = uiState.totalUsedSeconds,
                    totalRemainingSeconds = uiState.totalRemainingSeconds,
                    dailyLimitSeconds = uiState.totalDailyQuotaSeconds
                )
            }

            // Two Metric Cards side by side: Apps Protected & Time Saved
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 1: Apps Protected
                    MetricSummaryCard(
                        title = "Apps Protected",
                        value = "${uiState.protectedAppsCount}",
                        icon = Icons.Default.Shield,
                        iconTint = BrandCyan,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToApps
                    )

                    // Card 2: Time Saved
                    val savedSecs = uiState.totalTimeSavedSeconds
                    val savedFormatted = if (savedSecs != null && savedSecs > 0) {
                        TimeUtils.formatDurationShort(savedSecs)
                    } else "0m"

                    MetricSummaryCard(
                        title = "Time Saved",
                        value = savedFormatted,
                        icon = Icons.Default.Timer,
                        iconTint = SuccessGreen,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAnalytics
                    )
                }
            }

            // Active Focus Session Banner (if running)
            if (uiState.activeFocusSession != null) {
                item {
                    ActiveFocusBanner(onFocusClick = onNavigateToFocus)
                }
            }

            // Section: Apps Nearing Limit (Screen 4)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Apps Nearing Limit",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "View All",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandCyan,
                        modifier = Modifier
                            .clickable { onNavigateToApps() }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            val warningOrActiveApps = uiState.appsCloseToLimit.ifEmpty { uiState.appCards.take(4) }

            if (warningOrActiveApps.isEmpty()) {
                item {
                    FocusCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        backgroundColor = DarkSurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No applications protected yet",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add distracting social media and games to track & limit.",
                                fontSize = 12.sp,
                                color = Neutral400
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onNavigateToApps,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "Protect Apps", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(warningOrActiveApps, key = { it.packageName }) { appItem ->
                    NearingLimitAppRow(
                        item = appItem,
                        onClick = { onNavigateToAppDetails(appItem.packageName) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Today's Usage Card matching Screen 4 of reference mockup
 */
@Composable
private fun TodayUsageCard(
    totalUsedSeconds: Long,
    totalRemainingSeconds: Long,
    dailyLimitSeconds: Long
) {
    val effectiveLimit = if (dailyLimitSeconds > 0) dailyLimitSeconds else 1800L
    val progress = (totalUsedSeconds.toFloat() / effectiveLimit.toFloat()).coerceIn(0f, 1f)

    FocusCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        backgroundColor = DarkSurface,
        borderColor = DarkBorder,
        contentPadding = 18.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Today's Usage",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circular Progress Ring on Left
                UsageProgressRing(
                    progress = progress,
                    size = 114.dp,
                    strokeWidth = 10.dp,
                    progressColor = BrandCyan
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = TimeUtils.formatDurationShort(totalUsedSeconds),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Total",
                            fontSize = 11.sp,
                            color = Neutral400
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Stats on Right
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text(
                            text = TimeUtils.formatDurationShort(totalRemainingSeconds),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Remaining",
                            fontSize = 12.sp,
                            color = Neutral400
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BrandCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Daily Limit: ${TimeUtils.formatDurationShort(effectiveLimit)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Neutral200
                        )
                    }
                }
            }
        }
    }
}

/**
 * Metric Summary Card (Screen 4)
 */
@Composable
private fun MetricSummaryCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusCard(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        cornerRadius = 18.dp,
        backgroundColor = DarkSurface,
        borderColor = DarkBorder,
        contentPadding = 14.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                color = Neutral400
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

/**
 * App Row in "Apps Nearing Limit" section (Screen 4)
 */
@Composable
private fun NearingLimitAppRow(
    item: DashboardCardItem,
    onClick: () -> Unit
) {
    FocusCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 16.dp,
        backgroundColor = DarkSurface,
        borderColor = if (item.isBlocked) DangerRed.copy(alpha = 0.35f) else DarkBorder,
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
                                fontWeight = FontWeight.Bold,
                                color = BrandCyan,
                                fontSize = 14.sp
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

                Text(
                    text = "${TimeUtils.formatDurationShort(item.usedSeconds)} / ${TimeUtils.formatDurationShort(item.quotaSeconds)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isBlocked) DangerRed else Neutral200
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { item.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (item.isBlocked) DangerRed else if (item.progressFraction > 0.75f) WarningAmber else BrandCyan,
                trackColor = DarkBorderSubtle,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun MonitoringIncompleteBanner(onFixClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarningAmber.copy(alpha = 0.12f))
            .border(1.dp, WarningAmber.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable { onFixClick() }
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Monitoring Setup Required",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Enable Usage Access & Accessibility to enforce quotas.",
                        fontSize = 11.sp,
                        color = Neutral400
                    )
                }
            }
            Text(
                text = "FIX",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WarningAmber
            )
        }
    }
}

@Composable
private fun ActiveFocusBanner(onFocusClick: () -> Unit) {
    FocusCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFocusClick() },
        backgroundColor = BrandIndigo.copy(alpha = 0.25f),
        borderColor = BrandIndigo.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Focus Session in Progress",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = "Protected distractions are actively restricted.",
                    fontSize = 12.sp,
                    color = BrandCyan
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}
