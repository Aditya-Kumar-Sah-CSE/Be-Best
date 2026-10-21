package com.focuslock.app.presentation.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
import com.focuslock.app.core.designsystem.DangerRed
import com.focuslock.app.core.designsystem.DarkBorder
import com.focuslock.app.core.designsystem.DarkBorderSubtle
import com.focuslock.app.core.designsystem.DarkSurface
import com.focuslock.app.core.designsystem.DarkSurfaceElevated
import com.focuslock.app.core.designsystem.Neutral200
import com.focuslock.app.core.designsystem.Neutral400
import com.focuslock.app.core.designsystem.Neutral600
import com.focuslock.app.core.designsystem.SuccessGreen
import com.focuslock.app.core.update.UpdateInfo
import com.focuslock.app.core.update.UpdateState
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialog(
    updateState: UpdateState,
    currentVersionName: String,
    currentVersionCode: Long,
    onUpdateNow: (UpdateInfo) -> Unit,
    onInstallNow: (File, UpdateInfo) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    if (updateState is UpdateState.Idle || updateState is UpdateState.UpToDate || updateState is UpdateState.DismissedForSession) {
        return
    }

    Dialog(
        onDismissRequest = {
            if (updateState !is UpdateState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = updateState !is UpdateState.Downloading,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(BrandCyan.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = when (updateState) {
                            is UpdateState.ReadyToInstall -> Icons.Default.CheckCircle
                            is UpdateState.Downloading -> Icons.Default.Download
                            is UpdateState.Error -> Icons.Default.ErrorOutline
                            else -> Icons.Default.RocketLaunch
                        },
                        contentDescription = null,
                        tint = when (updateState) {
                            is UpdateState.ReadyToInstall -> SuccessGreen
                            is UpdateState.Error -> DangerRed
                            else -> BrandCyan
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = when (updateState) {
                        is UpdateState.Checking -> "Checking for Updates..."
                        is UpdateState.Downloading -> "Downloading Update..."
                        is UpdateState.ReadyToInstall -> "Ready to Install"
                        is UpdateState.Error -> "Update Failed"
                        else -> "New Update Available"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Content body depending on state
                when (updateState) {
                    is UpdateState.Checking -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(
                            color = BrandCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Connecting to GitHub Releases...",
                            fontSize = 13.sp,
                            color = Neutral400
                        )
                    }

                    is UpdateState.UpdateAvailable -> {
                        val info = updateState.info
                        VersionBadgeRow(
                            currentName = currentVersionName,
                            currentCode = currentVersionCode,
                            availableName = info.versionName,
                            availableCode = info.versionCode
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Release Notes Box
                        ReleaseNotesBox(releaseNotes = info.releaseNotes)

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action Buttons: Update Now / Later
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Neutral400)
                            ) {
                                Text(text = "Later", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }

                            Button(
                                onClick = { onUpdateNow(info) },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandCyan)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Update Now",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        val info = updateState.info
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "FocusLock v${info.versionName}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandCyan
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (updateState.progressPercent >= 0) {
                            LinearProgressIndicator(
                                progress = { updateState.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = BrandCyan,
                                trackColor = DarkSurfaceElevated
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = BrandCyan,
                                trackColor = DarkSurfaceElevated
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val downloadedMb = String.format(Locale.US, "%.1f", updateState.downloadedBytes / (1024f * 1024f))
                        val totalMb = if (updateState.totalBytes > 0) {
                            String.format(Locale.US, "%.1f", updateState.totalBytes / (1024f * 1024f))
                        } else null

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (totalMb != null) "$downloadedMb MB / $totalMb MB" else "$downloadedMb MB downloaded",
                                fontSize = 12.sp,
                                color = Neutral400
                            )
                            if (updateState.progressPercent >= 0) {
                                Text(
                                    text = "${updateState.progressPercent}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Verifying cryptographic SHA-256 upon completion...",
                            fontSize = 11.sp,
                            color = Neutral600
                        )
                    }

                    is UpdateState.ReadyToInstall -> {
                        val info = updateState.info
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "FocusLock v${info.versionName} has been downloaded and verified.",
                            fontSize = 13.sp,
                            color = Neutral200,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "SHA-256 and developer signature verified ✓",
                            fontSize = 12.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { onInstallNow(updateState.apkFile, info) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Install Now",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    is UpdateState.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = updateState.message,
                            fontSize = 13.sp,
                            color = DangerRed,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Neutral400)
                            ) {
                                Text(text = "Dismiss", fontSize = 13.sp)
                            }

                            if (updateState.canRetry) {
                                Button(
                                    onClick = onRetry,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan)
                                ) {
                                    Text(
                                        text = "Retry",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun VersionBadgeRow(
    currentName: String,
    currentCode: Long,
    availableName: String,
    availableCode: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceElevated)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Current: v$currentName",
                fontSize = 12.sp,
                color = Neutral400
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(text = "→", fontSize = 14.sp, color = Neutral400)

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BrandCyan.copy(alpha = 0.15f))
                .border(1.dp, BrandCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "New: v$availableName",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BrandCyan
            )
        }
    }
}

@Composable
private fun ReleaseNotesBox(releaseNotes: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkBorderSubtle, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "What's New:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral200
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = releaseNotes,
            fontSize = 12.sp,
            color = Neutral400,
            lineHeight = 18.sp,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }
}
