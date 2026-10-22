package com.focuslock.app.presentation.breakalert

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
import com.focuslock.app.core.designsystem.BrandTeal
import com.focuslock.app.core.designsystem.DarkBackground
import com.focuslock.app.core.designsystem.DarkBorder
import com.focuslock.app.core.designsystem.DarkBorderSubtle
import com.focuslock.app.core.designsystem.DarkSurface
import com.focuslock.app.core.designsystem.DarkSurfaceElevated
import com.focuslock.app.core.designsystem.FocusCard
import com.focuslock.app.core.designsystem.FocusLockTheme
import com.focuslock.app.core.designsystem.Neutral200
import com.focuslock.app.core.designsystem.Neutral400
import com.focuslock.app.core.designsystem.SuccessGreen
import com.focuslock.app.core.util.BreakReminderHelper
import com.focuslock.app.core.wellness.WellnessPreferences
import com.focuslock.app.core.wellness.WellnessSoundManager
import kotlinx.coroutines.delay

class BreakAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START, false)

        setContent {
            FocusLockTheme {
                BackHandler {
                    BreakReminderHelper.snooze(this@BreakAlertActivity, 5L)
                    finish()
                }

                BreakAlertScreen(
                    autoStart = autoStart,
                    onStartBreak = {
                        BreakReminderHelper.startBreak(this@BreakAlertActivity)
                    },
                    onBreakCompleted = {
                        BreakReminderHelper.completeBreak(this@BreakAlertActivity)
                    },
                    onRemindIn5Minutes = {
                        BreakReminderHelper.snooze(this@BreakAlertActivity, 5L)
                        finish()
                    },
                    onDismiss = {
                        BreakReminderHelper.dismiss(this@BreakAlertActivity)
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isBreakAlertVisible = true
    }

    override fun onPause() {
        super.onPause()
        isBreakAlertVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        WellnessSoundManager.getInstance(this).stop()
    }

    companion object {
        const val EXTRA_AUTO_START = "extra_auto_start"

        @Volatile
        var isBreakAlertVisible: Boolean = false
            private set

        fun launch(context: Context, autoStart: Boolean = false) {
            val intent = Intent(context, BreakAlertActivity::class.java).apply {
                putExtra(EXTRA_AUTO_START, autoStart)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun BreakAlertScreen(
    autoStart: Boolean = false,
    onStartBreak: () -> Unit = {},
    onBreakCompleted: () -> Unit = {},
    onRemindIn5Minutes: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = remember { WellnessPreferences.getInstance(context) }
    val customVideoUriString = preferences.customVideoUriString

    val isOriginallyActive = remember { BreakReminderHelper.isBreakActive(context) }
    var isBreakInProgress by remember {
        mutableStateOf(autoStart || isOriginallyActive)
    }
    var remainingBreakSeconds by remember {
        mutableLongStateOf(
            if (isOriginallyActive) BreakReminderHelper.getRemainingBreakSeconds(context) else 300L
        )
    }
    var isBreakCompleted by remember {
        mutableStateOf(BreakReminderHelper.isBreakCompleted(context))
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (BreakReminderHelper.isBreakCompleted(context)) {
                    isBreakCompleted = true
                    isBreakInProgress = false
                    remainingBreakSeconds = 0L
                } else if (BreakReminderHelper.isBreakActive(context)) {
                    isBreakInProgress = true
                    remainingBreakSeconds = BreakReminderHelper.getRemainingBreakSeconds(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isBreakInProgress) {
        if (!isBreakInProgress) return@LaunchedEffect
        if (!BreakReminderHelper.isBreakActive(context) && !BreakReminderHelper.isBreakCompleted(context)) {
            onStartBreak()
        }
        while (isBreakInProgress) {
            val remaining = BreakReminderHelper.getRemainingBreakSeconds(context)
            remainingBreakSeconds = remaining
            if (remaining <= 0L) {
                isBreakCompleted = true
                isBreakInProgress = false
                onBreakCompleted()
                break
            }
            delay(1000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top Screen-Time Pill Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE60B132B))
                        .border(1.dp, Color(0x4D38BDF8), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isBreakInProgress) "☕ 5-Minute Wellness Break" else "⏱ 30:00 Continuous Screen Time",
                        color = if (isBreakInProgress) SuccessGreen else BrandCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary User Message
                Text(
                    text = "Please, sir! It's time to take care of yourself.",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "You've been using your screen continuously for 30 minutes. Take a small break and refresh yourself.",
                    fontSize = 13.5.sp,
                    color = Neutral400,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Central Visual / 3D Animation Section
            if (isBreakInProgress) {
                // Active 5-minute break circular countdown
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(190.dp)
                        .scale(pulseScale)
                ) {
                    val progress = (remainingBreakSeconds.toFloat() / 300f).coerceIn(0f, 1f)
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(180.dp),
                        color = BrandCyan,
                        strokeWidth = 8.dp,
                        trackColor = DarkSurfaceElevated
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val minutes = remainingBreakSeconds / 60
                        val seconds = remainingBreakSeconds % 60
                        val formatted = String.format("%02d:%02d", minutes, seconds)

                        Text(
                            text = formatted,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (remainingBreakSeconds > 0) "Rest your eyes" else "Break Complete! 🎉",
                            fontSize = 12.sp,
                            color = if (remainingBreakSeconds > 0) BrandTeal else SuccessGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else if (!customVideoUriString.isNullOrBlank()) {
                // Custom User-Selected Video Player
                CustomVideoPlayerCard(
                    videoUri = Uri.parse(customVideoUriString),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                )
            } else {
                // Dual 3D Animated Person Walking & Drinking Water
                WellnessDualAnimationDisplay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Core Wellness Action Points
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WellnessActionRow(
                    icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    iconTint = BrandTeal,
                    title = "🚶 Go for a short walk.",
                    subtitle = "Stretch your posture and walk around a bit."
                )

                WellnessActionRow(
                    icon = Icons.Default.Visibility,
                    iconTint = BrandCyan,
                    title = "👀 Rest your eyes.",
                    subtitle = "Look 20 feet away into the distance for 20 seconds."
                )

                WellnessActionRow(
                    icon = Icons.Default.LocalDrink,
                    iconTint = Color(0xFF38BDF8),
                    title = "💧 Drink a glass of water.",
                    subtitle = "Hydrate your body & brain to stay fresh & sharp."
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom User Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isBreakInProgress && !isBreakCompleted) {
                    // Button 1: "Take a 5-Minute Break"
                    Button(
                        onClick = {
                            onStartBreak()
                            isBreakInProgress = true
                            remainingBreakSeconds = BreakReminderHelper.getRemainingBreakSeconds(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Take a 5-Minute Break",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    // Button 2 & 3: "Remind Me in 5 Minutes" & "Dismiss"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRemindIn5Minutes,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Neutral200)
                        ) {
                            Text(
                                text = "Remind in 5m",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Neutral400)
                        ) {
                            Text(
                                text = "Dismiss",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else if (isBreakInProgress) {
                    // Break in progress controls
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal)
                    ) {
                        Text(
                            text = "Break In Progress (Walking & Hydrating)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Neutral400)
                    ) {
                        Text(text = "End Break & Continue", fontSize = 13.sp)
                    }
                } else {
                    // Break Completed
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Break Complete! Continue ✓",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WellnessActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    FocusCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        backgroundColor = DarkSurface,
        borderColor = DarkBorder,
        contentPadding = 10.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = Neutral400
                )
            }
        }
    }
}

@Composable
fun CustomVideoPlayerCard(
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        start()
                    }
                    setOnErrorListener { _, _, _ ->
                        // If video fails, silent fallback
                        true
                    }
                    setVideoURI(videoUri)
                }
            }
        )
    }
}
