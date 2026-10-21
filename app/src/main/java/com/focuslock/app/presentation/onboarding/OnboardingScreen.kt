package com.focuslock.app.presentation.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.app.core.designsystem.AmoledBackground
import com.focuslock.app.core.designsystem.CyanAccent
import com.focuslock.app.core.designsystem.GlassmorphicCard
import com.focuslock.app.core.designsystem.IridescentPebbleBrush
import com.focuslock.app.core.designsystem.PurpleAccent
import com.focuslock.app.core.designsystem.TextMuted
import com.focuslock.app.core.designsystem.TextPrimary
import com.focuslock.app.core.designsystem.TextSecondary

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Step Indicator Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentStep) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (index == currentStep) CyanAccent else Color(0xFF262C40))
                )
            }
        }

        // Step Content
        AnimatedContent(targetState = currentStep, label = "onboarding_steps") { step ->
            when (step) {
                0 -> WelcomeStep()
                1 -> PhilosophyStep()
                2 -> PermissionsStep(
                    onOpenUsage = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                else -> ReadyStep()
            }
        }

        // Bottom Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = { currentStep-- },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Back")
                }
            }

            Button(
                onClick = {
                    if (currentStep < 3) {
                        currentStep++
                    } else {
                        onFinishOnboarding()
                    }
                },
                modifier = Modifier
                    .weight(if (currentStep > 0) 1.5f else 1f)
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text(
                    text = if (currentStep < 3) "Continue" else "Launch FocusLock",
                    color = AmoledBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = AmoledBackground,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun WelcomeStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(IridescentPebbleBrush)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Reclaim Your Focus",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "FocusLock helps you overcome compulsive social media and gaming habits by enforcing hard daily quotas with countdown timers.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun PhilosophyStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(PurpleAccent.copy(alpha = 0.2f))
                .border(1.dp, PurpleAccent, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = PurpleAccent,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "30-Minute Daily Policy",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Selected distractions receive an independent 30-minute daily allowance. Once exhausted, the app is blocked until midnight reset.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun PermissionsStep(
    onOpenUsage: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Transparent Permissions",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "FocusLock is 100% local-first. We never send your data to any cloud servers.",
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Usage Access Item
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "1. Usage Access", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Tracks daily minutes accurately", fontSize = 12.sp, color = TextSecondary)
                }
                Button(
                    onClick = onOpenUsage,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2235))
                ) {
                    Text("Grant", color = CyanAccent, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Accessibility Service Item
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "2. Accessibility Service", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Displays lock screen when app opens", fontSize = 12.sp, color = TextSecondary)
                }
                Button(
                    onClick = onOpenAccessibility,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2235))
                ) {
                    Text("Grant", color = CyanAccent, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ReadyStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(CyanAccent.copy(alpha = 0.2f))
                .border(1.dp, CyanAccent, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "You're All Set!",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "FocusLock is ready to guard your hours. Remember:\n\n\"Your future self will thank you.\"",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}
