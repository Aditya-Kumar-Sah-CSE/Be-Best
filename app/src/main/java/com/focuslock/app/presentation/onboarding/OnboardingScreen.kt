package com.focuslock.app.presentation.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.app.R
import com.focuslock.app.core.designsystem.AnimatedPrimaryButton
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
import com.focuslock.app.core.designsystem.BrandViolet
import com.focuslock.app.core.designsystem.DarkBackground
import com.focuslock.app.core.designsystem.DarkBorder
import com.focuslock.app.core.designsystem.DarkBorderSubtle
import com.focuslock.app.core.designsystem.DarkSurface
import com.focuslock.app.core.designsystem.DarkSurfaceElevated
import com.focuslock.app.core.designsystem.FocusCard
import com.focuslock.app.core.designsystem.Neutral200
import com.focuslock.app.core.designsystem.Neutral400
import com.focuslock.app.core.designsystem.Neutral600
import com.focuslock.app.core.designsystem.PrimaryGradient
import com.focuslock.app.core.designsystem.SuccessGreen

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    var hasUsageAccess by remember { mutableStateOf(false) }
    var hasAccessibility by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        hasUsageAccess = checkUsageAccess(context)
        hasAccessibility = checkAccessibilityEnabled(context)
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    // Step dots at top
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Step Indicators & Back action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 0) {
                IconButton(onClick = { currentStep-- }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Step Indicator Dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    val isSelected = index == currentStep
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 22.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) BrandCyan else DarkBorderSubtle)
                    )
                }
            }

            Spacer(modifier = Modifier.width(48.dp))
        }

        // Screen Content
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "onboarding_animation",
            modifier = Modifier.weight(1f)
        ) { step ->
            when (step) {
                0 -> WelcomeScreenContent()
                1 -> UsagePermissionScreenContent(
                    hasUsage = hasUsageAccess,
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
                else -> AccessibilityPermissionScreenContent(
                    hasAccessibility = hasAccessibility,
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }

        // Bottom Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentStep) {
                0 -> {
                    AnimatedPrimaryButton(
                        text = "Get Started",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = {
                            refreshPermissions()
                            currentStep = 1
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                1 -> {
                    AnimatedPrimaryButton(
                        text = if (hasUsageAccess) "Continue" else "Open Settings",
                        onClick = {
                            if (hasUsageAccess) {
                                currentStep = 2
                            } else {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Skip for now",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Neutral400,
                        modifier = Modifier
                            .clickable {
                                currentStep = 2
                            }
                            .padding(8.dp)
                    )
                }
                2 -> {
                    AnimatedPrimaryButton(
                        text = if (hasAccessibility) "Enter FocusLock" else "Open Settings",
                        onClick = {
                            if (hasAccessibility || hasUsageAccess) {
                                val prefs = context.getSharedPreferences("focuslock_app_prefs", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("is_onboarded", true).apply()
                                onFinishOnboarding()
                            } else {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Skip and finish setup",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Neutral400,
                        modifier = Modifier
                            .clickable {
                                val prefs = context.getSharedPreferences("focuslock_app_prefs", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("is_onboarded", true).apply()
                                onFinishOnboarding()
                            }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Screen 2: Welcome to FocusLock with 3 Feature Cards
 */
@Composable
private fun WelcomeScreenContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Hero Graphic with Floating App Icons and Shield
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp)
        ) {
            // Ambient Aura
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(BrandIndigo.copy(alpha = 0.4f), Color.Transparent)
                        )
                    )
            )

            // Center Logo Squircle
            Image(
                painter = painterResource(id = R.drawable.ic_focuslock_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to FocusLock",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Block distractions, track your usage and build a better you.",
            fontSize = 14.sp,
            color = Neutral400,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 3 Feature Cards matching reference mockup
        FeatureCardItem(
            icon = Icons.Default.Visibility,
            iconBgColor = Color(0xFF10B981),
            title = "Track Your Usage",
            subtitle = "See where your time goes"
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureCardItem(
            icon = Icons.Default.Speed,
            iconBgColor = Color(0xFF3B82F6),
            title = "Set Daily Limits",
            subtitle = "Control your social media & apps"
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureCardItem(
            icon = Icons.Default.Lock,
            iconBgColor = Color(0xFF8B5CF6),
            title = "Stay Focused",
            subtitle = "Build productive habits"
        )
    }
}

@Composable
private fun FeatureCardItem(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String
) {
    FocusCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        backgroundColor = DarkSurface,
        borderColor = DarkBorder,
        contentPadding = 14.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBgColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Neutral400
                )
            }
        }
    }
}

/**
 * Screen 3: Allow Usage Access
 */
@Composable
private fun UsagePermissionScreenContent(
    hasUsage: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Allow Usage Access",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "We need Usage Access permission to track your app usage accurately.",
            fontSize = 13.sp,
            color = Neutral400,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Visual Card Mockup (Settings Toggle Graphic matching Screen 3)
        FocusCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            backgroundColor = DarkSurface,
            borderColor = DarkBorder,
            contentPadding = 18.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Mock Search Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Neutral600,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Usage access",
                        fontSize = 12.sp,
                        color = Neutral400
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // App Row with Active Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_focuslock_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "FocusLock",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    Switch(
                        checked = hasUsage,
                        onCheckedChange = { onOpenSettings() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandCyan
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Purple/Blue Info Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1B4B).copy(alpha = 0.5f))
                .border(1.dp, BrandIndigo.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = BrandCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "This permission is required to read your app usage data. We do not collect any personal information.",
                    fontSize = 12.sp,
                    color = Neutral200,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

/**
 * Screen 3b: Accessibility Permission Setup
 */
@Composable
private fun AccessibilityPermissionScreenContent(
    hasAccessibility: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Accessibility Service",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Accessibility access is used solely to detect when protected apps open and present the blocking lockscreen.",
            fontSize = 13.sp,
            color = Neutral400,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Visual Card
        FocusCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            backgroundColor = DarkSurface,
            borderColor = DarkBorder,
            contentPadding = 18.dp
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
                            .background(BrandIndigo.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Accessibility,
                            contentDescription = null,
                            tint = BrandCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "FocusLock Monitor",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (hasAccessibility) "Service active & protecting" else "Tap to enable in Settings",
                            fontSize = 12.sp,
                            color = if (hasAccessibility) SuccessGreen else Neutral400
                        )
                    }
                }

                Switch(
                    checked = hasAccessibility,
                    onCheckedChange = { onOpenSettings() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandCyan
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF13192B))
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Zero Data Collection: FocusLock never reads content on your screen, keystrokes, or notifications. All detection remains 100% on-device.",
                    fontSize = 12.sp,
                    color = Neutral200,
                    lineHeight = 17.sp
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
