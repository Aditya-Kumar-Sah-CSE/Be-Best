package com.focuslock.app.presentation.settings

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.focuslock.app.core.designsystem.AnimatedPrimaryButton
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
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
import com.focuslock.app.core.designsystem.PermissionStatusCard
import com.focuslock.app.core.designsystem.SuccessGreen
import com.focuslock.app.core.designsystem.WarningAmber
import com.focuslock.app.core.util.BatteryOptimizationHelper

@Composable
fun PermissionStatusScreen(
    onBack: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current

    var hasUsageAccess by remember { mutableStateOf(false) }
    var hasAccessibility by remember { mutableStateOf(false) }
    var hasNotifications by remember { mutableStateOf(false) }
    var hasBatteryOpt by remember { mutableStateOf(false) }
    var hasOverlay by remember { mutableStateOf(false) }

    fun refresh() {
        hasUsageAccess = checkUsageAccess(context)
        hasAccessibility = checkAccessibilityEnabled(context)
        hasNotifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
        hasBatteryOpt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        hasOverlay = com.focuslock.app.core.overlay.FloatingTimerManager.hasOverlayPermission(context)
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val allGranted = hasUsageAccess && hasAccessibility

    Scaffold(
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Spacer(modifier = Modifier.height(8.dp))

                // Screen 11 Top Bar: Back arrow, "Permission Status"
                FocusLockTopBar(
                    title = "Permission Status",
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    onNavigationClick = onBack
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Card 1: Usage Access
                    item {
                        PermissionStatusCard(
                            title = "Usage Access",
                            subtitle = if (hasUsageAccess) "Enabled" else "Tap to Grant in Settings",
                            isEnabled = hasUsageAccess,
                            icon = Icons.Default.Visibility,
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        )
                    }

                    // Card 2: Accessibility Service
                    item {
                        PermissionStatusCard(
                            title = "Accessibility Service",
                            subtitle = if (hasAccessibility) "Enabled" else "Tap to Grant in Settings",
                            isEnabled = hasAccessibility,
                            icon = Icons.Default.Accessibility,
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        )
                    }

                    // Card 3: Notifications
                    item {
                        PermissionStatusCard(
                            title = "Notifications",
                            subtitle = if (hasNotifications) "Enabled" else "Tap to Enable",
                            isEnabled = hasNotifications,
                            icon = Icons.Default.Notifications,
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    // Card 4: Battery Optimization
                    item {
                        PermissionStatusCard(
                            title = "Battery Optimization",
                            subtitle = if (hasBatteryOpt) "Optimized" else "Tap to Exempt",
                            isEnabled = hasBatteryOpt,
                            icon = Icons.Default.BatteryChargingFull,
                            onClick = {
                                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                            }
                        )
                    }

                    // Card 5: Display Over Other Apps (Floating Timer Overlay)
                    item {
                        PermissionStatusCard(
                            title = "Display Over Other Apps",
                            subtitle = if (hasOverlay) "Enabled (Floating Pill Active)" else "Tap to Grant Permission for Floating Timer",
                            isEnabled = hasOverlay,
                            icon = Icons.Default.Timer,
                            onClick = {
                                com.focuslock.app.core.overlay.FloatingTimerManager.requestOverlayPermission(context)
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Screen 11: Protection Status Banner
                    item {
                        val bannerBg = if (allGranted) Color(0xFF064E3B).copy(alpha = 0.3f) else WarningAmber.copy(alpha = 0.15f)
                        val bannerBorder = if (allGranted) SuccessGreen.copy(alpha = 0.5f) else WarningAmber.copy(alpha = 0.4f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(bannerBg)
                                .border(1.dp, bannerBorder, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = if (allGranted) Icons.Default.Shield else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (allGranted) SuccessGreen else WarningAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (allGranted) "Protection Active" else "Setup Incomplete",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (allGranted) "All required permissions are enabled. FocusLock is working properly."
                                        else "Please enable Usage Access and Accessibility Service to ensure quota enforcement and lock screens function reliably.",
                                        fontSize = 12.sp,
                                        color = Neutral200,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Screen 11 Bottom Button: "Back to Dashboard"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                AnimatedPrimaryButton(
                    text = "Back to Dashboard",
                    onClick = onNavigateToDashboard,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun checkUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun checkAccessibilityEnabled(context: Context): Boolean {
    val expectedService = "${context.packageName}/${com.focuslock.app.service.FocusLockAccessibilityService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(expectedService)
}
