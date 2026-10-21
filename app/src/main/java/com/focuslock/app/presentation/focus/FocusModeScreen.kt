package com.focuslock.app.presentation.focus

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.focuslock.app.FocusLockApp
import com.focuslock.app.R
import com.focuslock.app.core.designsystem.AnimatedPrimaryButton
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
import com.focuslock.app.core.designsystem.FocusLockBottomBar
import com.focuslock.app.core.designsystem.FocusLockTopBar
import com.focuslock.app.core.designsystem.Neutral200
import com.focuslock.app.core.designsystem.Neutral400
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.data.local.entity.FocusSessionEntity
import kotlinx.coroutines.delay

@Composable
fun FocusModeScreen(
    onBack: () -> Unit,
    showBottomBar: Boolean = false,
    onNavigateToHome: () -> Unit = {},
    onNavigateToApps: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val app = FocusLockApp.instance
    val appRepo = app.appRepository
    val focusSessionDao = app.database.focusSessionDao()

    val protectedApps by appRepo.getProtectedAppsFlow().collectAsState(initial = emptyList())
    val activeApps = protectedApps.filter { it.enabled }

    var selectedDurationMinutes by remember { mutableIntStateOf(25) }
    var isRunning by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(25 * 60) }

    LaunchedEffect(isRunning, secondsRemaining) {
        if (isRunning && secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining -= 1
        } else if (secondsRemaining == 0 && isRunning) {
            isRunning = false
            // Record completed session in Room
            val now = System.currentTimeMillis()
            focusSessionDao.insertSession(
                FocusSessionEntity(
                    startedAt = now - (selectedDurationMinutes * 60 * 1000L),
                    plannedEndAt = now,
                    actualEndAt = now,
                    status = "COMPLETED"
                )
            )
        }
    }

    val totalDurationSeconds = selectedDurationMinutes * 60
    val progress = if (totalDurationSeconds > 0) {
        (totalDurationSeconds - secondsRemaining).toFloat() / totalDurationSeconds.toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500),
        label = "focus_progress"
    )

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            if (showBottomBar) {
                FocusLockBottomBar(
                    currentRoute = "focus",
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToApps = onNavigateToApps,
                    onNavigateToFocus = { /* Already on focus */ },
                    onNavigateToAnalytics = onNavigateToAnalytics,
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))
                FocusLockTopBar(
                    title = "Focus Mode",
                    navigationIcon = if (!showBottomBar) Icons.AutoMirrored.Filled.ArrowBack else null,
                    onNavigationClick = onBack
                )
            }

            // Center Content
            if (!isRunning) {
                // Setup Mode (Screen 8)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Illustration: Person at laptop with glowing plants (Screen 8)
                    Image(
                        painter = painterResource(id = R.drawable.ic_focus_illustration),
                        contentDescription = "Focus Illustration",
                        modifier = Modifier
                            .size(190.dp, 130.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Stay Focused. Get More Done.",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Block selected apps during your focus session and build better habits.",
                        fontSize = 13.sp,
                        color = Neutral400,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    // Session Duration Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "Session Duration",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Duration Preset Cards: 25m Pomodoro, 50m Deep Work, Custom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DurationPresetCard(
                            title = "25m",
                            subtitle = "Pomodoro",
                            isSelected = selectedDurationMinutes == 25,
                            onClick = {
                                selectedDurationMinutes = 25
                                secondsRemaining = 25 * 60
                            },
                            modifier = Modifier.weight(1f)
                        )

                        DurationPresetCard(
                            title = "50m",
                            subtitle = "Deep Work",
                            isSelected = selectedDurationMinutes == 50,
                            onClick = {
                                selectedDurationMinutes = 50
                                secondsRemaining = 50 * 60
                            },
                            modifier = Modifier.weight(1f)
                        )

                        DurationPresetCard(
                            title = "Custom",
                            subtitle = "Set Time",
                            icon = Icons.Default.AccessTime,
                            isSelected = selectedDurationMinutes != 25 && selectedDurationMinutes != 50,
                            onClick = {
                                selectedDurationMinutes = 35
                                secondsRemaining = 35 * 60
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Blocked Apps Section (Screen 8)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "Blocked Apps (${activeApps.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row of blocked app avatar icons with +N badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayCount = 4
                        val displayedApps = activeApps.take(displayCount)
                        val remainingCount = (activeApps.size - displayCount).coerceAtLeast(0)

                        displayedApps.forEach { appItem ->
                            val icon = appRepo.getAppIcon(appItem.packageName)
                            if (icon != null) {
                                val bitmap = icon.toBitmap(56, 56, Bitmap.Config.ARGB_8888)
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = appItem.displayName,
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
                                        text = appItem.displayName.take(1).uppercase(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandCyan
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        if (remainingCount > 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, DarkBorderSubtle, RoundedCornerShape(10.dp))
                            ) {
                                Text(
                                    text = "+$remainingCount",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Neutral200
                                )
                            }
                        }
                    }
                }
            } else {
                // Running Active Session
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(230.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.size(210.dp),
                            color = DarkSurfaceElevated,
                            strokeWidth = 10.dp
                        )

                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(210.dp),
                            color = BrandCyan,
                            strokeWidth = 10.dp,
                            strokeCap = StrokeCap.Round
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = TimeUtils.formatCountdown(secondsRemaining.toLong()),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Deep Focus Active",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandTeal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Notifications silenced and ${activeApps.size} distraction apps restricted.",
                        fontSize = 12.sp,
                        color = Neutral400,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom Action Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                if (!isRunning) {
                    AnimatedPrimaryButton(
                        text = "Start Focus Session",
                        onClick = { isRunning = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { isRunning = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated)
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Pause", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                isRunning = false
                                secondsRemaining = selectedDurationMinutes * 60
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "End", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationPresetCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    FocusCard(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        cornerRadius = 16.dp,
        backgroundColor = if (isSelected) BrandIndigo else DarkSurface,
        borderColor = if (isSelected) BrandCyan else DarkBorder,
        contentPadding = 12.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else BrandCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
            } else {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (isSelected) Color.White.copy(alpha = 0.85f) else Neutral400
            )
        }
    }
}
