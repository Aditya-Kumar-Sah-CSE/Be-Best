package com.focuslock.app.presentation.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.app.FocusLockApp
import com.focuslock.app.core.designsystem.AppTheme
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
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
import com.focuslock.app.core.designsystem.SuccessGreen
import com.focuslock.app.core.designsystem.ThemeManager
import com.focuslock.app.core.designsystem.ThemeMode

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit = {},
    showBottomBar: Boolean = false,
    onNavigateToHome: () -> Unit = {},
    onNavigateToApps: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {}
) {
    val context = LocalContext.current
    val securityManager = remember { FocusLockApp.instance.pinSecurityManager }

    var isHardcoreEnabled by remember { mutableStateOf(securityManager.isHardcoreModeActive()) }
    var hasPin by remember { mutableStateOf(securityManager.hasPin()) }
    var pinInput by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }

    var showGeneralSection by remember { mutableStateOf(false) }
    var showHardcoreSection by remember { mutableStateOf(false) }
    var showLimitsSection by remember { mutableStateOf(false) }
    var showWellbeingSection by remember { mutableStateOf(false) }
    var showPrivacySection by remember { mutableStateOf(false) }
    var showAboutSection by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("focuslock_app_prefs", Context.MODE_PRIVATE) }
    val currentTheme by ThemeManager.themeMode.collectAsState()
    var breakAlertEnabled by remember {
        mutableStateOf(prefs.getBoolean("continuous_break_alert", true))
    }

    Scaffold(
        containerColor = AppTheme.colors.background,
        bottomBar = {
            if (showBottomBar) {
                FocusLockBottomBar(
                    currentRoute = "settings",
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToApps = onNavigateToApps,
                    onNavigateToFocus = onNavigateToFocus,
                    onNavigateToAnalytics = onNavigateToAnalytics,
                    onNavigateToSettings = { /* Already on settings */ }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Screen 9 Top Bar: Back arrow, "Settings"
            FocusLockTopBar(
                title = "Settings",
                navigationIcon = if (!showBottomBar) Icons.AutoMirrored.Filled.ArrowBack else null,
                onNavigationClick = onBack
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Item 1: General (Theme, language, app behavior) (Screen 9)
                item {
                    SettingsGroupCard(
                        icon = Icons.Default.Palette,
                        iconColor = BrandCyan,
                        title = "General",
                        subtitle = "Theme, appearance, and app behavior",
                        isExpanded = showGeneralSection,
                        onClick = { showGeneralSection = !showGeneralSection }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text(
                                text = "Appearance Theme",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    ThemeMode.SYSTEM to "System",
                                    ThemeMode.DARK to "Dark Navy",
                                    ThemeMode.LIGHT to "Light"
                               ).forEach { (mode, label) ->
                                    val isSelected = currentTheme == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) BrandIndigo else AppTheme.colors.surfaceElevated)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) BrandCyan else AppTheme.colors.borderSubtle,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                ThemeManager.setTheme(context, mode)
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else AppTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Item 2: Daily Limits & 12:00 AM Reset
                item {
                    SettingsGroupCard(
                        icon = Icons.Default.Timer,
                        iconColor = BrandIndigo,
                        title = "Daily Limits & Reset",
                        subtitle = "Default 30m limit • Renews daily at 12:00 AM (Midnight)",
                        isExpanded = showLimitsSection,
                        onClick = { showLimitsSection = !showLimitsSection }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text(
                                text = "Daily Quota Renewal",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All app quotas reset every night at 12:00 AM midnight (00:00:00). Daily usage counts restart fresh for every new calendar day.",
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Item 3: 30-Minute Screen Time Break Alert
                item {
                    SettingsGroupCard(
                        icon = Icons.Default.Info,
                        iconColor = BrandCyan,
                        title = "Continuous Screen Break Alert",
                        subtitle = "5-min break alert after 30 min continuous usage",
                        isExpanded = showWellbeingSection,
                        onClick = { showWellbeingSection = !showWellbeingSection }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "30-Min Walk & Drink Water Alert",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Automatically alerts you to take a 5-minute break to walk, stretch, and drink water when your screen is on continuously for 30 minutes.",
                                        fontSize = 12.sp,
                                        color = AppTheme.colors.textSecondary,
                                        lineHeight = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = breakAlertEnabled,
                                    onCheckedChange = { checked ->
                                        breakAlertEnabled = checked
                                        prefs.edit().putBoolean("continuous_break_alert", checked).apply()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = BrandCyan
                                    )
                                )
                            }
                        }
                    }
                }

                // Item 4: Hardcore Mode (Screen 9)
                item {
                    SettingsGroupCard(
                        icon = Icons.Default.Lock,
                        iconColor = DangerRed,
                        title = "Hardcore Mode",
                        subtitle = "Extra protection, friction timer & PIN",
                        isExpanded = showHardcoreSection,
                        onClick = { showHardcoreSection = !showHardcoreSection }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Anti-Tampering Delay",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Requires 60s cooldown before altering limits",
                                        fontSize = 11.sp,
                                        color = Neutral400
                                    )
                                }
                                Switch(
                                    checked = isHardcoreEnabled,
                                    onCheckedChange = { enabled ->
                                        isHardcoreEnabled = enabled
                                        securityManager.setHardcoreMode(enabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = DangerRed
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // PIN Configuration
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Security PIN",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (hasPin) "Salted PBKDF2 PIN Active" else "No PIN configured",
                                        fontSize = 11.sp,
                                        color = if (hasPin) SuccessGreen else Neutral400
                                    )
                                }
                                Button(
                                    onClick = { showPinDialog = !showPinDialog },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated)
                                ) {
                                    Text(
                                        text = if (hasPin) "Change PIN" else "Set PIN",
                                        fontSize = 12.sp,
                                        color = BrandCyan
                                    )
                                }
                            }

                            if (showPinDialog) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = { if (it.length <= 6) pinInput = it },
                                    label = { Text("Enter 4-6 digit PIN", color = Neutral400) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (pinInput.length >= 4) {
                                            securityManager.setPin(pinInput)
                                            hasPin = true
                                            showPinDialog = false
                                            pinInput = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                                ) {
                                    Text(text = "Save Security PIN", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Item 4: Permissions (Screen 9) -> Navigates to Permission Status Screen
                item {
                    SettingsGroupCard(
                        icon = Icons.Default.Security,
                        iconColor = SuccessGreen,
                        title = "Permissions",
                        subtitle = "Usage access, accessibility service diagnostics",
                        isExpanded = false,
                        onClick = onNavigateToPermissions
                    )
                }

                // Item 5: Privacy & Data (Screen 9)
                item {
                    SettingsGroupCard(
                        icon = Icons.Default.Storage,
                        iconColor = BrandCyan,
                        title = "Privacy & Data",
                        subtitle = "Local storage, on-device data management",
                        isExpanded = showPrivacySection,
                        onClick = { showPrivacySection = !showPrivacySection }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text(
                                text = "100% On-Device Local Storage",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "FocusLock runs completely offline without sending any metrics, telemetry, or app tracking to remote servers. All logs and quotas are stored securely in local SQLite Room database.",
                                fontSize = 12.sp,
                                color = Neutral400,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Item 6: About FocusLock (Screen 9)
                item {
                    val updateManager = remember { com.focuslock.app.core.update.AppUpdateManager.getInstance() }
                    val currentVersion = remember { updateManager.getInstalledVersionName(context) }
                    val coroutineScope = rememberCoroutineScope()
                    val updateState by updateManager.updateState.collectAsState()
                    val isChecking = updateState is com.focuslock.app.core.update.UpdateState.Checking
                    val isUpToDate = updateState is com.focuslock.app.core.update.UpdateState.UpToDate

                    SettingsGroupCard(
                        icon = Icons.Default.Info,
                        iconColor = Neutral400,
                        title = "About",
                        subtitle = "FocusLock Version $currentVersion",
                        isExpanded = showAboutSection,
                        onClick = { showAboutSection = !showAboutSection }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text(
                                text = "FocusLock — Digital Wellbeing",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Take Back Your Time. Built with modern Kotlin, Jetpack Compose, and Room local persistence.",
                                fontSize = 12.sp,
                                color = Neutral400,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    if (!isChecking) {
                                        coroutineScope.launch {
                                            val result = updateManager.checkForUpdates(context, force = true)
                                            if (result == null && updateManager.updateState.value is com.focuslock.app.core.update.UpdateState.UpToDate) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "No update available. FocusLock is up to date (v$currentVersion).",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !isChecking,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandCyan)
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(
                                        color = BrandCyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Checking for updates...",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = BrandCyan
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Check for Updates",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (isUpToDate) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(com.focuslock.app.core.designsystem.SuccessGreen.copy(alpha = 0.12f))
                                        .border(
                                            1.dp,
                                            com.focuslock.app.core.designsystem.SuccessGreen.copy(alpha = 0.3f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = com.focuslock.app.core.designsystem.SuccessGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "No update available. You're on the latest version.",
                                        fontSize = 12.sp,
                                        color = com.focuslock.app.core.designsystem.SuccessGreen,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    expandedContent: (@Composable () -> Unit)? = null
) {
    FocusCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 16.dp,
        backgroundColor = AppTheme.colors.surface,
        borderColor = AppTheme.colors.border,
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
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconColor.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                if (expandedContent != null) {
                    expandedContent()
                }
            }
        }
    }
}
