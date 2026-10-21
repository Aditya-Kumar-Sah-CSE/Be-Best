package com.focuslock.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.focuslock.app.R
import com.focuslock.app.core.util.TimeUtils

/**
 * Top App Bar with FocusLock brand identity or back navigation
 */
@Composable
fun FocusLockTopBar(
    title: String = "FocusLock",
    subtitle: String? = null,
    showLogo: Boolean = false,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    onActionClick: (() -> Unit)? = null,
    actionContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (navigationIcon != null && onNavigationClick != null) {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else if (showLogo) {
                Image(
                    painter = painterResource(id = R.drawable.ic_focuslock_logo),
                    contentDescription = "FocusLock Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (actionContent != null) {
            actionContent()
        } else if (actionIcon != null && onActionClick != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated)
                    .clickable { onActionClick() }
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = "Action",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Animated Primary Action Button with Gradient styling
 */
@Composable
fun AnimatedPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    gradient: Brush = PrimaryGradient
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(54.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), spotColor = BrandIndigo.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = DarkSurfaceElevated
        ),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (enabled) gradient else Brush.linearGradient(listOf(DarkSurfaceElevated, DarkSurfaceElevated)))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (icon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Circular Usage Progress Ring matching reference Hero card
 */
@Composable
fun UsageProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 130.dp,
    strokeWidth: Dp = 10.dp,
    progressColor: Color = BrandCyan,
    trackColor: Color = DarkBorderSubtle,
    centerContent: @Composable () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "progress_ring"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        // Background Track
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(size),
            color = trackColor,
            strokeWidth = strokeWidth,
            trackColor = Color.Transparent
        )

        // Animated Foreground Progress
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(size),
            color = progressColor,
            strokeWidth = strokeWidth,
            strokeCap = StrokeCap.Round,
            trackColor = Color.Transparent
        )

        // Centered Content
        centerContent()
    }
}

/**
 * Today's Usage Hero Card (Screen 4 Layout)
 */
@Composable
fun DailyUsageCard(
    totalUsedSeconds: Long,
    remainingAllowanceSeconds: Long,
    dailyLimitSeconds: Long,
    modifier: Modifier = Modifier
) {
    val progress = if (dailyLimitSeconds > 0) {
        (totalUsedSeconds.toFloat() / dailyLimitSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f

    FocusCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        backgroundColor = DarkSurface,
        borderColor = DarkBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Usage",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circular Ring on Left
                UsageProgressRing(
                    progress = progress,
                    size = 110.dp,
                    strokeWidth = 9.dp,
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
                            text = TimeUtils.formatDurationShort(remainingAllowanceSeconds),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
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
                            text = "Daily Limit: ${TimeUtils.formatDurationShort(dailyLimitSeconds)}",
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
 * Single Protected App Row in Dashboard or Apps screen
 */
@Composable
fun ProtectedAppRow(
    appName: String,
    category: String,
    usedSeconds: Long,
    limitSeconds: Long,
    isProtected: Boolean,
    isBlocked: Boolean,
    appIcon: Drawable? = null,
    onToggle: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (limitSeconds > 0) {
        (usedSeconds.toFloat() / limitSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f

    FocusCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 16.dp,
        backgroundColor = DarkSurface,
        borderColor = if (isBlocked) DangerRed.copy(alpha = 0.4f) else DarkBorder
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
                    if (appIcon != null) {
                        val bitmap = appIcon.toBitmap(64, 64, Bitmap.Config.ARGB_8888)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = appName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                        ) {
                            Text(
                                text = appName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = BrandCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = category,
                            fontSize = 12.sp,
                            color = Neutral400
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${TimeUtils.formatDurationShort(usedSeconds)} / ${TimeUtils.formatDurationShort(limitSeconds)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBlocked) DangerRed else Neutral200
                    )

                    if (onToggle != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isProtected,
                            onCheckedChange = { onToggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandIndigo
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isBlocked) DangerRed else if (progress > 0.8f) WarningAmber else BrandCyan,
                trackColor = DarkBorderSubtle,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

/**
 * Remaining time badge
 */
@Composable
fun RemainingTimeBadge(
    remainingSeconds: Long,
    isBlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isBlocked) DangerRed.copy(alpha = 0.15f) else BrandTeal.copy(alpha = 0.15f)
    val textColor = if (isBlocked) DangerRed else BrandTeal

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (isBlocked) "Exhausted" else "${TimeUtils.formatDurationShort(remainingSeconds)} left",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * Diagnostic card for permissions (Screen 11)
 */
@Composable
fun PermissionStatusCard(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 16.dp,
        backgroundColor = DarkSurface,
        borderColor = if (isEnabled) DarkBorder else WarningAmber.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isEnabled) SuccessGreen.copy(alpha = 0.15f) else DarkSurfaceElevated)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isEnabled) SuccessGreen else Neutral400,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = if (isEnabled) SuccessGreen else WarningAmber
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isEnabled) SuccessGreen.copy(alpha = 0.2f) else DarkSurfaceElevated)
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isEnabled) SuccessGreen else Neutral600,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Usage Bar Chart Component (Screen 10 Analytics)
 */
@Composable
fun UsageBarChart(
    dayLabels: List<String>,
    values: List<Float>,
    selectedIndex: Int = dayLabels.lastIndex,
    onSelectDay: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    barColor: Color = BrandCyan,
    selectedBarColor: Color = BrandIndigoLight
) {
    val maxValue = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        dayLabels.forEachIndexed { index, label ->
            val value = values.getOrElse(index) { 0f }
            val fraction = (value / maxValue).coerceIn(0.08f, 1f)
            val isSelected = index == selectedIndex

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectDay(index) }
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height((90 * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (isSelected) selectedBarColor else barColor.copy(alpha = 0.45f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.White else Neutral400
                )
            }
        }
    }
}

/**
 * Reusable Bottom Navigation Bar for the 5 core tabs
 */
@Composable
fun FocusLockBottomBar(
    currentRoute: String,
    onNavigateToHome: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToFocus: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    NavigationBar(
        containerColor = DarkBackground,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = DarkBorderSubtle, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        val items = listOf(
            BottomNavItem("dashboard", "Home", Icons.Default.Home, onNavigateToHome),
            BottomNavItem("apps", "Apps", Icons.Default.GridView, onNavigateToApps),
            BottomNavItem("focus", "Focus", Icons.Default.Timer, onNavigateToFocus),
            BottomNavItem("analytics", "Analytics", Icons.Default.BarChart, onNavigateToAnalytics),
            BottomNavItem("settings", "Settings", Icons.Default.Settings, onNavigateToSettings)
        )

        items.forEach { item ->
            val isSelected = currentRoute == item.route
            NavigationBarItem(
                selected = isSelected,
                onClick = item.onClick,
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandCyan,
                    selectedTextColor = BrandCyan,
                    unselectedIconColor = Neutral600,
                    unselectedTextColor = Neutral600,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Standard Empty State Component
 */
@Composable
fun EmptyState(
    message: String,
    subMessage: String? = null,
    icon: ImageVector = Icons.Default.HourglassEmpty,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Neutral600,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Neutral200,
            textAlign = TextAlign.Center
        )
        if (!subMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subMessage,
                fontSize = 12.sp,
                color = Neutral400,
                textAlign = TextAlign.Center
            )
        }
        if (!actionText.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = actionText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Standard Loading State
 */
@Composable
fun LoadingState(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = BrandCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = Neutral400
            )
        }
    }
}

/**
 * Standard Error State
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = DangerRed,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = Neutral200,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated)
        ) {
            Text(text = "Retry", color = BrandCyan)
        }
    }
}
