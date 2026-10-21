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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SpaceDashboard
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
import androidx.compose.runtime.remember
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
import com.focuslock.app.core.designsystem.AnimatedPrimaryButton
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
import com.focuslock.app.core.designsystem.DangerRed
import com.focuslock.app.core.designsystem.DarkBackground
import com.focuslock.app.core.designsystem.DarkBorderSubtle
import com.focuslock.app.core.designsystem.DarkSurfaceElevated
import com.focuslock.app.core.designsystem.FocusLockTheme
import com.focuslock.app.core.designsystem.Neutral200
import com.focuslock.app.core.designsystem.Neutral400
import com.focuslock.app.core.designsystem.Neutral600
import com.focuslock.app.core.designsystem.PrimaryGradient
import com.focuslock.app.core.util.TimeUtils
import kotlinx.coroutines.delay

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
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
    }
}

@Composable
fun BlockingScreen(
    packageName: String,
    appName: String,
    appIcon: Drawable?,
    onGoHome: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    var secondsUntilMidnight by remember {
        mutableLongStateOf(TimeUtils.getSecondsUntilNextMidnight())
    }

    // Dynamic 1-second countdown pulse until next local calendar midnight
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            secondsUntilMidnight = TimeUtils.getSecondsUntilNextMidnight()
        }
    }

    // Subtle gentle breathing pulse on the coral lock ring
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

                Spacer(modifier = Modifier.height(20.dp))

                // Title: Daily Limit Reached (Screen 7)
                Text(
                    text = "Daily Limit Reached",
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

                Text(
                    text = "You've used today's allotted allowance.",
                    fontSize = 13.sp,
                    color = Neutral400,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Screen 7: Circular Warning Ring with Padlock & Midnight Countdown
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(170.dp)
                        .scale(pulseScale)
                ) {
                    // Outer subtle danger glow
                    Box(
                        modifier = Modifier
                            .size(168.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(DangerRed.copy(alpha = 0.25f), Color.Transparent)
                                )
                            )
                    )

                    // Coral Red Progress Ring
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(150.dp),
                        color = DangerRed,
                        strokeWidth = 6.dp,
                        trackColor = Color.Transparent
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = DangerRed,
                            modifier = Modifier.size(28.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Unlocks in",
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

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Come back tomorrow for a fresh start.",
                    fontSize = 13.sp,
                    color = Neutral400,
                    textAlign = TextAlign.Center
                )
            }

            // Screen 7: Bottom Action Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                // Button 1: Go to Home (Gradient Button)
                AnimatedPrimaryButton(
                    text = "Go to Home",
                    icon = Icons.Default.Home,
                    onClick = onGoHome,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Button 2: View Today's Progress (Outlined/Tonal Button)
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
                        text = "View Today's Progress",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
