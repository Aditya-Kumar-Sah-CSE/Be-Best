package com.focuslock.app.presentation.apps

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.focuslock.app.core.designsystem.FocusCard
import com.focuslock.app.core.designsystem.FocusLockTopBar
import com.focuslock.app.core.designsystem.Neutral200
import com.focuslock.app.core.designsystem.Neutral400
import com.focuslock.app.core.designsystem.UsageProgressRing
import com.focuslock.app.core.designsystem.WarningAmber
import com.focuslock.app.core.designsystem.SuccessGreen
import com.focuslock.app.core.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppDetailsScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AppDetailsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLimitPicker by remember { mutableStateOf(false) }

    LaunchedEffect(packageName) {
        viewModel.loadAppDetails(packageName)
    }

    val limits = listOf(15 * 60L, 30 * 60L, 45 * 60L, 60 * 60L, 90 * 60L, 120 * 60L)

    Scaffold(
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar
            item {
                Spacer(modifier = Modifier.height(8.dp))
                FocusLockTopBar(
                    title = if (uiState.isInstagram) "Instagram Activity" else "App Details",
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    onNavigationClick = onBack
                )
            }

            // Screen 6 Header: App Icon, Name, Category • package name
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.appIcon != null) {
                        val bitmap = uiState.appIcon!!.toBitmap(96, 96, Bitmap.Config.ARGB_8888)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = uiState.displayName,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(DarkSurfaceElevated)
                        ) {
                            Text(
                                text = uiState.displayName.take(1).uppercase(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (uiState.isInstagram && uiState.instagramVariant.isNotBlank()) {
                            uiState.instagramVariant
                        } else {
                            uiState.displayName
                        },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${uiState.category.title} • ${uiState.packageName}",
                        fontSize = 12.sp,
                        color = Neutral400
                    )
                }
            }

            // Screen 6: Two Circular Stat Cards Side-by-Side
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 1: Used Today Ring
                    val usedProgress = if (uiState.dailyLimitSeconds > 0) {
                        (uiState.usedSecondsToday.toFloat() / uiState.dailyLimitSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    FocusCard(
                        modifier = Modifier.weight(1f),
                        cornerRadius = 18.dp,
                        backgroundColor = DarkSurface,
                        borderColor = DarkBorder,
                        contentPadding = 14.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UsageProgressRing(
                                progress = usedProgress,
                                size = 80.dp,
                                strokeWidth = 8.dp,
                                progressColor = if (uiState.isBlocked) DangerRed else BrandCyan
                            ) {
                                Text(
                                    text = TimeUtils.formatDurationShort(uiState.usedSecondsToday),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Used today",
                                fontSize = 12.sp,
                                color = Neutral400
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "${TimeUtils.formatDurationShort(uiState.dailyLimitSeconds)} Daily limit",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Neutral200
                            )
                        }
                    }

                    // Card 2: Remaining Ring
                    val remainingProgress = if (uiState.dailyLimitSeconds > 0) {
                        (uiState.remainingSeconds.toFloat() / uiState.dailyLimitSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    FocusCard(
                        modifier = Modifier.weight(1f),
                        cornerRadius = 18.dp,
                        backgroundColor = DarkSurface,
                        borderColor = DarkBorder,
                        contentPadding = 14.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UsageProgressRing(
                                progress = remainingProgress,
                                size = 80.dp,
                                strokeWidth = 8.dp,
                                progressColor = BrandTeal
                            ) {
                                Text(
                                    text = TimeUtils.formatDurationShort(uiState.remainingSeconds),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Remaining",
                                fontSize = 12.sp,
                                color = Neutral400
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "${uiState.consumedPercentage}% Consumed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Neutral200
                            )
                        }
                    }
                }
            }

            // Sessions & Engagement Card
            item {
                FocusCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    backgroundColor = DarkSurface,
                    borderColor = DarkBorder,
                    contentPadding = 16.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sessions & Engagement",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Ground Truth",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        StatDetailRow(
                            label = "Detected Sessions",
                            value = "${uiState.sessionCount} sessions",
                            source = "UsageEvents"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StatDetailRow(
                            label = "Average Session",
                            value = TimeUtils.formatDurationShort(uiState.averageSessionSeconds),
                            source = "Calculated"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StatDetailRow(
                            label = "Longest Session",
                            value = TimeUtils.formatDurationShort(uiState.longestSessionSeconds),
                            source = "UsageEvents"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StatDetailRow(
                            label = "First Activity Today",
                            value = formatTimeOfDay(uiState.firstActivityAt),
                            source = "System Event"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StatDetailRow(
                            label = "Last Activity Today",
                            value = formatTimeOfDay(uiState.lastActivityAt),
                            source = "System Event"
                        )
                    }
                }
            }

            // Activity Estimation & Privacy Guard Card
            item {
                FocusCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    backgroundColor = DarkSurface,
                    borderColor = DarkBorder,
                    contentPadding = 16.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Activity Estimation",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Privacy Protected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        StatDetailRow(
                            label = "Scroll Interactions",
                            value = "~${uiState.approximateScrollCount} observed",
                            source = "Accessibility (Approximate)"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StatDetailRow(
                            label = "Reels Viewed",
                            value = "Unavailable",
                            source = "Not exposed by OS API"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StatDetailRow(
                            label = "Likes / Comments / DMs",
                            value = "Unsupported",
                            source = "Zero text/screen logging"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Strict Privacy Guard: Screen content, messages, and credentials are never captured, read, or transmitted.",
                                    fontSize = 11.sp,
                                    color = Neutral400,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // Official Instagram API Status Card (For Instagram packages)
            if (uiState.isInstagram) {
                item {
                    FocusCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        backgroundColor = DarkSurface,
                        borderColor = DarkBorder,
                        contentPadding = 16.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = BrandCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Official Meta Graph API",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Text(
                                    text = "Independent",
                                    fontSize = 11.sp,
                                    color = Neutral400
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Status: ${uiState.instagramApiStatus.title}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandTeal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Meta's official Graph API is restricted to creator/business accounts and does not expose personal browsing history, reels watched, or private usage. FocusLock provides 100% autonomous local tracking.",
                                fontSize = 11.5.sp,
                                color = Neutral400,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Screen 6: "Usage History" Card
            item {
                FocusCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    backgroundColor = DarkSurface,
                    borderColor = DarkBorder,
                    contentPadding = 16.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Usage History",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val maxHistory = maxOf(
                            uiState.usedSecondsToday,
                            uiState.usedSecondsYesterday,
                            (uiState.usedSecondsLast7Days / 7),
                            uiState.dailyLimitSeconds,
                            1L
                        ).toFloat()

                        // Row 1: Today
                        UsageHistoryRow(
                            label = "Today",
                            durationSeconds = uiState.usedSecondsToday,
                            fraction = (uiState.usedSecondsToday / maxHistory).coerceIn(0.05f, 1f),
                            barColor = BrandCyan
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Row 2: Yesterday
                        UsageHistoryRow(
                            label = "Yesterday",
                            durationSeconds = uiState.usedSecondsYesterday,
                            fraction = (uiState.usedSecondsYesterday / maxHistory).coerceIn(0.05f, 1f),
                            barColor = BrandIndigo
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Row 3: Last 7 Days
                        UsageHistoryRow(
                            label = "Last 7 Days",
                            durationSeconds = uiState.usedSecondsLast7Days,
                            fraction = (uiState.usedSecondsLast7Days / (maxHistory * 7)).coerceIn(0.05f, 1f),
                            barColor = BrandTeal
                        )
                    }
                }
            }

            // Screen 6: "Limit Settings" Card
            item {
                FocusCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    backgroundColor = DarkSurface,
                    borderColor = DarkBorder,
                    contentPadding = 16.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLimitPicker = !showLimitPicker },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Limit Settings",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Daily Limit: ${TimeUtils.formatDurationShort(uiState.dailyLimitSeconds)}",
                                    fontSize = 13.sp,
                                    color = BrandCyan
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Neutral400
                            )
                        }

                        if (showLimitPicker) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Select quota per calendar day:",
                                fontSize = 12.sp,
                                color = Neutral400
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                limits.forEach { sec ->
                                    val isSelected = uiState.dailyLimitSeconds == sec
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) BrandIndigo else DarkSurfaceElevated)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) BrandCyan else DarkBorderSubtle,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                viewModel.updateLimit(sec)
                                                showLimitPicker = false
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${sec / 60}m",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Scope clarification for Shorts / Reels
            if (uiState.isReelsOrShortsParent) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, DarkBorderSubtle, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = BrandCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Reels and Shorts are built-in sections of their parent application, not separate Android apps. FocusLock accurately tracks and restricts overall foreground screen time.",
                                fontSize = 12.sp,
                                color = Neutral400,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Screen 6 Bottom Action: Red "Disable Protection" Button
            item {
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = { viewModel.toggleProtection() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (uiState.isProtected) DangerRed.copy(alpha = 0.6f) else BrandIndigo
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (uiState.isProtected) DangerRed else BrandCyan
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.isProtected) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isProtected) "Disable Protection" else "Enable Protection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatDetailRow(
    label: String,
    value: String,
    source: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color.White
            )
            Text(
                text = source,
                fontSize = 10.5.sp,
                color = Neutral400
            )
        }
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = BrandCyan
        )
    }
}

@Composable
private fun UsageHistoryRow(
    label: String,
    durationSeconds: Long,
    fraction: Float,
    barColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color.White
            )
            Text(
                text = TimeUtils.formatDurationShort(durationSeconds),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Neutral200
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = DarkBorderSubtle,
            strokeCap = StrokeCap.Round
        )
    }
}

private fun formatTimeOfDay(millis: Long): String {
    if (millis <= 0L) return "None detected"
    val sdf = SimpleDateFormat("hh:mm a", Locale.US)
    return sdf.format(Date(millis))
}
