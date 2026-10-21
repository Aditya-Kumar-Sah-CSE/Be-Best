package com.focuslock.app.presentation.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.app.R
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
import com.focuslock.app.core.designsystem.BrandViolet
import com.focuslock.app.core.designsystem.DarkBackground
import com.focuslock.app.core.designsystem.DarkBorderSubtle
import com.focuslock.app.core.designsystem.Neutral400
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    // Check if animations are turned off at the system level (reduced-motion accessibility)
    val animatorScale = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: Exception) {
        1f
    }
    val isReducedMotion = animatorScale == 0f

    // 3D & Transform Animation values
    val scaleAnim = remember { Animatable(if (isReducedMotion) 1f else 0.72f) }
    val alphaAnim = remember { Animatable(if (isReducedMotion) 1f else 0f) }
    val tiltXAnim = remember { Animatable(if (isReducedMotion) 0f else -14f) }
    val tiltYAnim = remember { Animatable(if (isReducedMotion) 0f else 18f) }
    val auraScaleAnim = remember { Animatable(if (isReducedMotion) 1f else 0.6f) }
    val textAlphaAnim = remember { Animatable(if (isReducedMotion) 1f else 0f) }
    val textOffsetAnim = remember { Animatable(if (isReducedMotion) 0f else 24f) }

    // Subtle gentle infinite orbiting particle / shimmer
    val infiniteTransition = rememberInfiniteTransition(label = "splash_infinite")
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )

    LaunchedEffect(Unit) {
        if (isReducedMotion) {
            delay(300L)
            onSplashFinished()
            return@LaunchedEffect
        }

        // Orchestrated sequential spring & cubic entrance
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
            )
        }
        launch {
            tiltXAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
            )
        }
        launch {
            tiltYAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
            )
        }
        launch {
            auraScaleAnim.animateTo(
                targetValue = 1.25f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
        }

        delay(350L)

        // Text fade and slide in
        launch {
            textAlphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        launch {
            textOffsetAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }

        // Hold briefly for visual polish, then navigate
        delay(900L)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        // Deep background light glow
        Box(
            modifier = Modifier
                .size(340.dp)
                .scale(auraScaleAnim.value)
                .alpha(alphaAnim.value * 0.45f)
                .blur(40.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandIndigo.copy(alpha = 0.5f), BrandViolet.copy(alpha = 0.25f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // 3D-Style Center Logo Container
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(170.dp)
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        alpha = alphaAnim.value
                        rotationX = tiltXAnim.value
                        rotationY = tiltYAnim.value
                        cameraDistance = 14f * density
                    }
            ) {
                // Orbiting light particle ring around logo
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .rotate(orbitRotation)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    BrandCyan.copy(alpha = 0.7f),
                                    BrandViolet.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Logo Drawable Asset
                Image(
                    painter = painterResource(id = R.drawable.ic_focuslock_logo),
                    contentDescription = "FocusLock Logo",
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(32.dp))
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Text Group with Slide & Fade
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(textAlphaAnim.value)
                    .offset { IntOffset(0, textOffsetAnim.value.roundToInt()) }
            ) {
                Text(
                    text = "FocusLock",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Take Back Your Time",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandCyan,
                    letterSpacing = 0.2.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Subtitle Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF13192B))
                        .border(1.dp, DarkBorderSubtle, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "• Less Distraction  • More Progress",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Neutral400
                    )
                }
            }
        }

        // Bottom Loading Progress
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .alpha(textAlphaAnim.value)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(BrandCyan)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Loading your focus...",
                fontSize = 12.sp,
                color = Neutral400
            )
        }
    }
}
