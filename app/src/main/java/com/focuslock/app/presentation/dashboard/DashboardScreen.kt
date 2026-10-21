package com.focuslock.app.presentation.dashboard

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslock.app.core.designsystem.AmoledBackground
import com.focuslock.app.core.designsystem.CardGlowBorder
import com.focuslock.app.core.designsystem.CyanAccent
import com.focuslock.app.core.designsystem.DangerRed
import com.focuslock.app.core.designsystem.GlassmorphicCard
import com.focuslock.app.core.designsystem.IridescentPebbleBrush
import com.focuslock.app.core.designsystem.PurpleAccent
import com.focuslock.app.core.designsystem.SuccessEmerald
import com.focuslock.app.core.designsystem.SurfaceCard
import com.focuslock.app.core.designsystem.TextMuted
import com.focuslock.app.core.designsystem.TextPrimary
import com.focuslock.app.core.designsystem.TextSecondary
import com.focuslock.app.core.designsystem.WarningAmber
import com.focuslock.app.core.util.TimeUtils

@Composable
fun DashboardScreen(
    onNavigateToApps: () -> Unit,
    onNavigateToFocus: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshHealth()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            HeaderSection(
                onSettingsClick = onNavigateToSettings,
                isHardcoreActive = uiState.isHardcoreActive
            )
        }

        // Hero 3D Card: Today's Wellbeing Progress
        item {
            TodayProgressHeroCard(
                totalUsedSeconds = uiState.totalUsedSeconds,
                blockedCount = uiState.blockedAppsCount,
                activeCount = uiState.activeAppsCount,
                secondsUntilMidnight = uiState.secondsUntilMidnight
            )
        }

        // Protection Health Banner
        item {
            ProtectionHealthCard(
                health = uiState.protectionHealth,
                onFixClick = onNavigateToSettings
            )
        }

        // Quick Focus Session Launcher
        item {
            QuickFocusActionCard(onStartFocus = onNavigateToFocus)
        }

        // Controlled Apps Section Title & Add Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Controlled Distractions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${uiState.appCards.size} apps monitored under daily quotas",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = onNavigateToApps,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E2234),
                        contentColor = CyanAccent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Manage", fontSize = 13.sp)
                }
            }
        }

        // List of Controlled App Cards
        if (uiState.appCards.isEmpty()) {
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No apps selected yet",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select Instagram, YouTube or games to enforce daily limits.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onNavigateToApps,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Text(text = "Select Apps Now", color = AmoledBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(uiState.appCards, key = { it.packageName }) { appCard ->
                AppUsageRowCard(card = appCard)
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun HeaderSection(
    onSettingsClick: () -> Unit,
    isHardcoreActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "FocusLock",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "Digital Wellbeing & Enforcement",
                fontSize = 13.sp,
                color = TextMuted
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isHardcoreActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF381018))
                        .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "HARDCORE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DangerRed
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141724))
                    .border(1.dp, Color(0xFF282E47), CircleShape)
                    .clickable { onSettingsClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security Settings",
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TodayProgressHeroCard(
    totalUsedSeconds: Long,
    blockedCount: Int,
    activeCount: Int,
    secondsUntilMidnight: Long
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TODAY'S SCREEN TIME",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent,
                    letterSpacing = 1.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Resets in ${TimeUtils.formatCountdownSeconds(secondsUntilMidnight)}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = TimeUtils.formatDurationShort(totalUsedSeconds),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "across all protected applications",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Blocked Apps Metric Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (blockedCount > 0) DangerRed.copy(alpha = 0.15f) else Color(0xFF1E2232))
                            .border(1.dp, if (blockedCount > 0) DangerRed.copy(alpha = 0.4f) else Color(0xFF2E354D), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$blockedCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (blockedCount > 0) DangerRed else TextPrimary
                            )
                            Text(
                                text = "Blocked",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    // Active Apps Metric Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E2232))
                            .border(1.dp, Color(0xFF2E354D), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$activeCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessEmerald
                            )
                            Text(
                                text = "Active",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProtectionHealthCard(
    health: com.focuslock.app.domain.model.ProtectionHealth,
    onFixClick: () -> Unit
) {
    val isGood = health.isFullyProtected
    val bgColor = if (isGood) Color(0xFF0F1E19) else Color(0xFF261912)
    val borderColor = if (isGood) SuccessEmerald.copy(alpha = 0.35f) else WarningAmber.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { if (!isGood) onFixClick() }
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
                    imageVector = if (isGood) Icons.Default.Shield else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGood) SuccessEmerald else WarningAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isGood) "Protection Active & Guarded" else "Protection Needs Attention",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isGood) "Accessibility and Usage Tracking fully synchronized" else "Accessibility service or usage access disabled",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            if (!isGood) {
                Text(
                    text = "FIX",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber
                )
            }
        }
    }
}

@Composable
fun QuickFocusActionCard(onStartFocus: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                )
            )
            .border(1.dp, Brush.horizontalGradient(listOf(PurpleAccent.copy(alpha = 0.4f), CyanAccent.copy(alpha = 0.2f))), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ready to Study or Deep Work?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Launch a 25-minute Pomodoro focus block to temporarily silence all selected distractions.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onStartFocus,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Focus", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun AppUsageRowCard(card: AppUsageCardData) {
    val animatedProgress by animateFloatAsState(
        targetValue = card.progressFraction,
        label = "usage_progress"
    )

    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        backgroundColor = Color(0xFF131520)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = card.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = card.category,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                // Status Badge
                if (card.isBlocked) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DangerRed.copy(alpha = 0.2f))
                            .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = DangerRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "BLOCKED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DangerRed
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C2235))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${TimeUtils.formatDurationShort(card.remainingSeconds)} left",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CyanAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (card.isBlocked) DangerRed else CyanAccent,
                trackColor = Color(0xFF1F2436),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${TimeUtils.formatDurationShort(card.usedSeconds)} used",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Text(
                    text = "Daily limit: ${TimeUtils.formatDurationShort(card.quotaSeconds)}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}
