package com.focuslock.app.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassDisabled
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.focuslock.app.FocusLockApp
import com.focuslock.app.core.designsystem.AnimatedPrimaryButton
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.DangerRed
import com.focuslock.app.core.designsystem.DarkBackground
import com.focuslock.app.core.designsystem.DarkBorderSubtle
import com.focuslock.app.core.designsystem.DarkSurfaceElevated
import com.focuslock.app.core.designsystem.FocusLockTheme
import com.focuslock.app.core.designsystem.Neutral200
import com.focuslock.app.core.designsystem.Neutral400
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.domain.engine.AppQuotaEvaluation
import com.focuslock.app.domain.engine.EmergencyStartResult
import com.focuslock.app.domain.engine.ProtectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BlockingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Protected App"

        val appIcon: Drawable? = try {
            if (packageName.isNotBlank()) packageManager.getApplicationIcon(packageName) else null
        } catch (e: Exception) {
            null
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                finish()
            }
        })

        setContent {
            FocusLockTheme {
                BlockingScreen(
                    packageName = packageName,
                    appName = appName,
                    appIcon = appIcon,
                    onGoHome = {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    },
                    onOpenDashboard = {
                        val dashIntent = Intent(this@BlockingActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(dashIntent)
                        finish()
                    },
                    onEmergencyAccessGranted = {
                        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        }
                        finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isBlockingScreenVisible = true
        activeBlockedPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
    }

    override fun onPause() {
        super.onPause()
        isBlockingScreenVisible = false
        activeBlockedPackage = null
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"

        @Volatile
        var isBlockingScreenVisible: Boolean = false
            private set

        @Volatile
        var activeBlockedPackage: String? = null
            private set
    }
}

@Composable
fun BlockingScreen(
    packageName: String,
    appName: String,
    appIcon: Drawable?,
    onGoHome: () -> Unit,
    onOpenDashboard: () -> Unit,
    onEmergencyAccessGranted: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var secondsUntilMidnight by remember {
        mutableLongStateOf(TimeUtils.getSecondsUntilNextMidnight())
    }

    var evaluation by remember { mutableStateOf<AppQuotaEvaluation?>(null) }
    var emergencyErrorMessage by remember { mutableStateOf<String?>(null) }

    // Dynamic 1-second pulse to refresh authoritative quota and midnight countdown
    LaunchedEffect(packageName) {
        while (true) {
            secondsUntilMidnight = TimeUtils.getSecondsUntilNextMidnight()
            if (packageName.isNotBlank()) {
                evaluation = FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)
            }
            delay(1000L)
        }
    }

    val currentEval = evaluation
    val isEmergencyActive = currentEval?.status == ProtectionState.EMERGENCY_ACTIVE
    val emergencyUsedToday = currentEval?.emergencyUsedToday == true
    val canUseEmergency = currentEval?.canUseEmergency == true && !emergencyUsedToday

    // Subtle gentle breathing pulse on the danger ring
    val infiniteTransition = rememberInfiniteTransition(label = "blocking_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
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
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Upper App Identity & Warning
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // App Icon
                if (appIcon != null) {
                    val bitmap = appIcon.toBitmap(80, 80, Bitmap.Config.ARGB_8888)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = appName,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(DarkSurfaceElevated)
                    ) {
                        Text(
                            text = appName.take(1).uppercase(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title: Daily Limit Reached
                Text(
                    text = "Daily limit reached",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = appName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandCyan,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Used today real duration
                val usedDurationText = currentEval?.let {
                    "${TimeUtils.formatDurationShort(it.usedSeconds)} used today"
                } ?: "Daily allowance exhausted."

                Text(
                    text = usedDurationText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Neutral200,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Circular Status Ring with Padlock / Countdown
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(170.dp)
                        .scale(pulseScale)
                ) {
                    val ringColor = if (isEmergencyActive) Color(0xFFF59E0B) else DangerRed

                    // Outer subtle danger glow
                    Box(
                        modifier = Modifier
                            .size(168.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(ringColor.copy(alpha = 0.25f), Color.Transparent)
                                )
                            )
                    )

                    // Progress Ring
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(150.dp),
                        color = ringColor,
                        strokeWidth = 6.dp,
                        trackColor = Color.Transparent
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isEmergencyActive) Icons.Default.Timer else Icons.Default.Lock,
                            contentDescription = if (isEmergencyActive) "Emergency Active" else "Locked",
                            tint = ringColor,
                            modifier = Modifier.size(28.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isEmergencyActive) {
                            Text(
                                text = "Emergency access",
                                fontSize = 11.sp,
                                color = Neutral400
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val remSecs = currentEval?.remainingEmergencySeconds ?: 0L
                            Text(
                                text = "00:${String.format("%02d", remSecs)}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFF59E0B),
                                letterSpacing = 1.sp
                            )
                        } else {
                            Text(
                                text = "Locked until 4:00 AM",
                                fontSize = 11.sp,
                                color = Neutral400
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = TimeUtils.formatCountdown(secondsUntilMidnight),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Contextual status text based on emergency lifecycle
                when {
                    isEmergencyActive -> {
                        Text(
                            text = "Emergency access remaining: 00:${String.format("%02d", currentEval?.remainingEmergencySeconds ?: 0L)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF59E0B),
                            textAlign = TextAlign.Center
                        )
                    }
                    emergencyUsedToday -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassDisabled,
                                contentDescription = null,
                                tint = DangerRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Emergency access already used today.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DangerRed,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "Locked until tomorrow.",
                            fontSize = 13.sp,
                            color = Neutral400,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (emergencyErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = emergencyErrorMessage ?: "",
                        fontSize = 12.sp,
                        color = DangerRed,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom Action Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                // Button 1: Emergency Open — 30 seconds
                if (canUseEmergency) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val result = FocusLockApp.instance.quotaEngine.startEmergencySession(packageName)
                                if (result is EmergencyStartResult.Success) {
                                    onEmergencyAccessGranted()
                                } else {
                                    emergencyErrorMessage = "Emergency session unavailable or already consumed."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Emergency Open — 30 seconds",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else if (isEmergencyActive) {
                    Button(
                        onClick = onEmergencyAccessGranted,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Return to App (${currentEval?.remainingEmergencySeconds}s left)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Button 2: Return Home
                AnimatedPrimaryButton(
                    text = "Return Home",
                    icon = Icons.Default.Home,
                    onClick = onGoHome,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Button 3: View Usage / View Today's Progress
                OutlinedButton(
                    onClick = onOpenDashboard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Neutral200
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.SpaceDashboard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = BrandCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View Usage",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
