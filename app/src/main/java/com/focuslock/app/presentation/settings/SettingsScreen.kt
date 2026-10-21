package com.focuslock.app.presentation.settings

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.app.FocusLockApp
import com.focuslock.app.core.designsystem.AmoledBackground
import com.focuslock.app.core.designsystem.CyanAccent
import com.focuslock.app.core.designsystem.DangerRed
import com.focuslock.app.core.designsystem.GlassmorphicCard
import com.focuslock.app.core.designsystem.PurpleAccent
import com.focuslock.app.core.designsystem.SuccessEmerald
import com.focuslock.app.core.designsystem.TextMuted
import com.focuslock.app.core.designsystem.TextPrimary
import com.focuslock.app.core.designsystem.TextSecondary
import com.focuslock.app.core.util.BatteryOptimizationHelper

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val securityManager = remember { FocusLockApp.instance.pinSecurityManager }

    var isHardcoreEnabled by remember { mutableStateOf(securityManager.isHardcoreModeActive()) }
    var hasPin by remember { mutableStateOf(securityManager.hasPin()) }
    var pinInput by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Security & Anti-Bypass",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Configure Hardcore Mode, PIN lock, and system permissions",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Hardcore Mode Card
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    backgroundColor = if (isHardcoreEnabled) Color(0xFF24141E) else Color(0xFF141724)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isHardcoreEnabled) DangerRed.copy(alpha = 0.2f) else Color(0xFF1E2234)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isHardcoreEnabled) DangerRed else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Hardcore Anti-Tampering",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Adds friction cooldowns before modifying limits",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Switch(
                                checked = isHardcoreEnabled,
                                onCheckedChange = { enabled ->
                                    isHardcoreEnabled = enabled
                                    securityManager.setHardcoreMode(enabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AmoledBackground,
                                    checkedTrackColor = DangerRed,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = Color(0xFF1F2436)
                                )
                            )
                        }

                        if (isHardcoreEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "When Hardcore Mode is active, extending daily limits or disabling protection requires entering your PIN and waiting through an anti-impulse 60-second cooldown.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // PIN Setup Card
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Pin,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Security PIN",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (hasPin) "PIN Protection is configured" else "No PIN configured",
                                        fontSize = 12.sp,
                                        color = if (hasPin) SuccessEmerald else TextMuted
                                    )
                                }
                            }

                            Button(
                                onClick = { showPinDialog = !showPinDialog },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2235))
                            ) {
                                Text(
                                    text = if (hasPin) "Change PIN" else "Set PIN",
                                    fontSize = 12.sp,
                                    color = CyanAccent
                                )
                            }
                        }

                        if (showPinDialog) {
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { if (it.length <= 6) pinInput = it },
                                label = { Text("Enter 4-6 digit PIN") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF10121B),
                                    unfocusedContainerColor = Color(0xFF10121B),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = CyanAccent
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
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
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                            ) {
                                Text(text = "Save Encrypted PIN", color = AmoledBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // System Permissions Direct Configuration Section
            item {
                Text(
                    text = "System Permissions & Reliability",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Accessibility Permission Link
            item {
                PermissionShortcutRow(
                    title = "Accessibility Service",
                    subtitle = "Required to detect when controlled apps transition to foreground",
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }

            // Usage Access Permission Link
            item {
                PermissionShortcutRow(
                    title = "Usage Access",
                    subtitle = "Allows reconciliation of foreground sessions with system stats",
                    onClick = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }

            // Battery Optimization Link
            item {
                PermissionShortcutRow(
                    title = "Battery Optimization Exemption",
                    subtitle = "Prevents aggressive OEM task killers from terminating enforcement",
                    onClick = {
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun PermissionShortcutRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF121420))
            .border(1.dp, Color(0xFF22283A), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "OPEN",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )
        }
    }
}
