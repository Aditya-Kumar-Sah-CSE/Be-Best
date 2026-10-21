package com.focuslock.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.app.R
import com.focuslock.app.core.designsystem.AmoledBackground
import com.focuslock.app.core.designsystem.CyanAccent
import com.focuslock.app.core.designsystem.FocusLockTheme
import com.focuslock.app.core.designsystem.IridescentPebbleBrush
import com.focuslock.app.core.designsystem.PurpleAccent
import com.focuslock.app.core.designsystem.TextMuted
import com.focuslock.app.core.designsystem.TextPrimary
import com.focuslock.app.core.designsystem.TextSecondary
import com.focuslock.app.core.util.TimeUtils
import kotlinx.coroutines.delay

class BlockingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Selected App"

        setContent {
            FocusLockTheme {
                BlockingScreen(
                    appName = appName,
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
    appName: String,
    onGoHome: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    var secondsUntilMidnight by remember {
        mutableLongStateOf(TimeUtils.getSecondsUntilNextMidnight())
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            secondsUntilMidnight = TimeUtils.getSecondsUntilNextMidnight()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lock_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(30.dp))

            // Upper 3D Iridescent Pebble & Floating Lock Badge (Exact Match with Reference)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(130.dp)
                        .scale(scalePulse)
                ) {
                    // Ambient radial glow behind squircle
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        CyanAccent.copy(alpha = 0.35f),
                                        PurpleAccent.copy(alpha = 0.20f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // 3D Iridescent Squircle Pebble
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(96.dp)
                            .shadow(
                                elevation = 24.dp,
                                shape = RoundedCornerShape(32.dp),
                                ambientColor = CyanAccent,
                                spotColor = PurpleAccent
                            )
                            .clip(RoundedCornerShape(32.dp))
                            .background(IridescentPebbleBrush)
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.7f), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(32.dp)
                            )
                    ) {
                        // Padlock icon
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Focus Lock",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Hero Headline
                Text(
                    text = stringResource(R.string.stay_focused),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Hero Motivational Quote from User Reference
                Text(
                    text = stringResource(R.string.future_self_quote),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Live Midnight Countdown Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(Color(0xFF141724))
                        .border(1.dp, Color(0xFF282E47), RoundedCornerShape(50.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.unlocks_in, TimeUtils.formatCountdownSeconds(secondsUntilMidnight)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent
                    )
                }
            }

            // Bottom Section: Explanatory Subtitle & Exit Actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "This app is blocked because you're in a fluid focus session.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onGoHome,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E2232),
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.go_to_home),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenDashboard,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CyanAccent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SpaceDashboard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Dashboard",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
