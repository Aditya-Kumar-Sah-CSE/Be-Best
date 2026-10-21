package com.focuslock.app.presentation.focus

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.app.core.designsystem.AmoledBackground
import com.focuslock.app.core.designsystem.CyanAccent
import com.focuslock.app.core.designsystem.DangerRed
import com.focuslock.app.core.designsystem.GlassmorphicCard
import com.focuslock.app.core.designsystem.PurpleAccent
import com.focuslock.app.core.designsystem.TextMuted
import com.focuslock.app.core.designsystem.TextPrimary
import com.focuslock.app.core.designsystem.TextSecondary
import com.focuslock.app.core.util.TimeUtils
import kotlinx.coroutines.delay

@Composable
fun FocusModeScreen(
    onBack: () -> Unit
) {
    var selectedDurationMinutes by remember { mutableIntStateOf(25) }
    var isRunning by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(25 * 60) }

    LaunchedEffect(isRunning, secondsRemaining) {
        if (isRunning && secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining -= 1
        } else if (secondsRemaining == 0) {
            isRunning = false
        }
    }

    val totalDurationSeconds = selectedDurationMinutes * 60
    val progress = if (totalDurationSeconds > 0) {
        (totalDurationSeconds - secondsRemaining).toFloat() / totalDurationSeconds.toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(targetValue = progress, label = "focus_progress")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar
        Column {
            Spacer(modifier = Modifier.height(16.dp))
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
                        text = "Focus Session",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Lock all distracting apps during your deep work block",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Center 3D Timer Ring
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(260.dp)
            ) {
                // Background radial glow
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    PurpleAccent.copy(alpha = 0.25f),
                                    CyanAccent.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Track ring
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(240.dp),
                    color = Color(0xFF1E2235),
                    strokeWidth = 12.dp
                )

                // Active animated progress ring
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(240.dp),
                    color = PurpleAccent,
                    strokeWidth = 12.dp,
                    strokeCap = StrokeCap.Round
                )

                // Inner Time Display
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = TimeUtils.formatCountdownSeconds(secondsRemaining.toLong()),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isRunning) "Deep Focus Active" else "Ready to Focus",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isRunning) CyanAccent else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Preset duration selectors (only when not running)
            if (!isRunning) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(15, 25, 50, 90).forEach { mins ->
                        val isSelected = selectedDurationMinutes == mins
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) PurpleAccent else Color(0xFF181B28))
                                .border(1.dp, if (isSelected) CyanAccent else Color(0xFF282E44), RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedDurationMinutes = mins
                                    secondsRemaining = mins * 60
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${mins}m",
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isRunning) {
                Button(
                    onClick = { isRunning = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Start Focus Session", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { isRunning = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2235))
                    ) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = null, tint = CyanAccent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Pause", color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            isRunning = false
                            secondsRemaining = selectedDurationMinutes * 60
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "End Session", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
