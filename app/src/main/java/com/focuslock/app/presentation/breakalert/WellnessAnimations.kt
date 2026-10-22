package com.focuslock.app.presentation.breakalert

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
import com.focuslock.app.core.designsystem.BrandTeal
import com.focuslock.app.core.designsystem.DarkSurface
import com.focuslock.app.core.designsystem.DarkSurfaceElevated
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-fidelity 3D-styled animated walking character and drinking water glass
 * crafted in Jetpack Compose Canvas with kinematic gait cycles, lighting highlights,
 * and fluid wave simulations.
 */

@Composable
fun WalkingPerson3DAnimation(
    modifier: Modifier = Modifier,
    walkingSpeedMillis: Int = 1200
) {
    val infiniteTransition = rememberInfiniteTransition(label = "walking")
    val walkPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = walkingSpeedMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "walk_phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width * 0.5f
        val groundY = height * 0.88f

        // 1. Isometric Ground Shadow (breathes with walking stride)
        val shadowScale = 1.0f + 0.15f * abs(sin(walkPhase))
        val shadowWidth = width * 0.45f * shadowScale
        val shadowHeight = height * 0.08f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x66000000), Color(0x22000000), Color.Transparent),
                center = Offset(centerX, groundY),
                radius = shadowWidth * 0.6f
            ),
            topLeft = Offset(centerX - shadowWidth * 0.5f, groundY - shadowHeight * 0.5f),
            size = Size(shadowWidth, shadowHeight)
        )

        // 2. Vertical Torso Bobbing (natural bipedal locomotion)
        val bobbingY = abs(sin(walkPhase * 2f)) * (height * 0.04f)
        val hipY = groundY - height * 0.36f + bobbingY
        val chestY = hipY - height * 0.22f
        val headY = chestY - height * 0.15f

        // Kinematic leg calculations
        val leftLegAngle = sin(walkPhase) * 0.55f
        val rightLegAngle = sin(walkPhase + PI.toFloat()) * 0.55f
        val legLength = height * 0.34f

        // Back leg (Right leg - shaded darker for 3D depth)
        drawLeg(
            hipX = centerX + width * 0.02f,
            hipY = hipY,
            legLength = legLength,
            angle = rightLegAngle,
            thighColor = Color(0xFF1E3A8A), // Deep navy indigo
            shinColor = Color(0xFF1D4ED8),
            shoeColor = Color(0xFF64748B)
        )

        // Back Arm (Right arm - swings opposite to right leg)
        val rightArmAngle = leftLegAngle * 0.85f
        drawArm(
            shoulderX = centerX,
            shoulderY = chestY + height * 0.03f,
            armLength = height * 0.22f,
            angle = rightArmAngle,
            armColor = Color(0xFF0284C7) // Sky blue
        )

        // Torso / Athletic Hoodie (3D gradient shading)
        val torsoWidth = width * 0.22f
        val torsoHeight = height * 0.24f
        val torsoPath = Path().apply {
            moveTo(centerX - torsoWidth * 0.45f, chestY)
            lineTo(centerX + torsoWidth * 0.45f, chestY)
            lineTo(centerX + torsoWidth * 0.35f, hipY)
            lineTo(centerX - torsoWidth * 0.35f, hipY)
            close()
        }
        drawPath(
            path = torsoPath,
            brush = Brush.verticalGradient(
                colors = listOf(BrandCyan, Color(0xFF0284C7), BrandIndigo)
            )
        )

        // Head & Sleek Cap with 3D lighting highlight
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFED7AA), Color(0xFFFDBA74), Color(0xFFFB923C)),
                center = Offset(centerX - width * 0.02f, headY - height * 0.02f),
                radius = width * 0.12f
            ),
            radius = width * 0.10f,
            center = Offset(centerX, headY)
        )

        // Front leg (Left leg - vibrant neon accent)
        drawLeg(
            hipX = centerX - width * 0.02f,
            hipY = hipY,
            legLength = legLength,
            angle = leftLegAngle,
            thighColor = BrandIndigo,
            shinColor = Color(0xFF3B82F6),
            shoeColor = Color(0xFF38BDF8) // Neon cyan runner
        )

        // Front Arm (Left arm - swings opposite to left leg)
        val leftArmAngle = rightLegAngle * 0.85f
        drawArm(
            shoulderX = centerX,
            shoulderY = chestY + height * 0.03f,
            armLength = height * 0.22f,
            angle = leftArmAngle,
            armColor = BrandCyan
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLeg(
    hipX: Float,
    hipY: Float,
    legLength: Float,
    angle: Float,
    thighColor: Color,
    shinColor: Color,
    shoeColor: Color
) {
    val thighLength = legLength * 0.52f
    val kneeX = hipX + sin(angle) * thighLength
    val kneeY = hipY + cos(angle) * thighLength

    val shinAngle = angle - (abs(sin(angle)) * 0.4f)
    val shinLength = legLength * 0.48f
    val footX = kneeX + sin(shinAngle) * shinLength
    val footY = kneeY + cos(shinAngle) * shinLength

    // Thigh
    drawLine(
        color = thighColor,
        start = Offset(hipX, hipY),
        end = Offset(kneeX, kneeY),
        strokeWidth = 14f,
        cap = StrokeCap.Round
    )

    // Shin
    drawLine(
        color = shinColor,
        start = Offset(kneeX, kneeY),
        end = Offset(footX, footY),
        strokeWidth = 12f,
        cap = StrokeCap.Round
    )

    // Athletic Shoe
    drawLine(
        color = shoeColor,
        start = Offset(footX - 4f, footY),
        end = Offset(footX + 18f, footY),
        strokeWidth = 9f,
        cap = StrokeCap.Round
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArm(
    shoulderX: Float,
    shoulderY: Float,
    armLength: Float,
    angle: Float,
    armColor: Color
) {
    val bicepLength = armLength * 0.55f
    val elbowX = shoulderX + sin(angle) * bicepLength
    val elbowY = shoulderY + cos(angle) * bicepLength

    val forearmLength = armLength * 0.45f
    val handX = elbowX + sin(angle + 0.35f) * forearmLength
    val handY = elbowY + cos(angle + 0.35f) * forearmLength

    drawLine(
        color = armColor,
        start = Offset(shoulderX, shoulderY),
        end = Offset(elbowX, elbowY),
        strokeWidth = 11f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = armColor.copy(alpha = 0.9f),
        start = Offset(elbowX, elbowY),
        end = Offset(handX, handY),
        strokeWidth = 9f,
        cap = StrokeCap.Round
    )
}

/**
 * 3D-styled animated drinking water glass with fluid wave surface,
 * bobbing ice cubes, and rising hydration bubbles.
 */
@Composable
fun DrinkingWater3DAnimation(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "water_waves")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width * 0.5f

        // Glass tumbler dimensions
        val topWidth = width * 0.58f
        val bottomWidth = width * 0.44f
        val glassTopY = height * 0.20f
        val glassBottomY = height * 0.85f

        // 1. Isometric Glass Shadow
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x55000000), Color.Transparent),
                center = Offset(centerX, glassBottomY + 12f),
                radius = bottomWidth * 0.7f
            ),
            topLeft = Offset(centerX - bottomWidth * 0.5f, glassBottomY),
            size = Size(bottomWidth, height * 0.12f)
        )

        // 2. Liquid Body (Glowing cyan/teal gradient)
        val liquidTopY = glassTopY + (glassBottomY - glassTopY) * 0.35f
        val liquidPath = Path().apply {
            val waveAmplitude = 6f
            moveTo(centerX - topWidth * 0.45f, liquidTopY)

            // Animated sinusoidal liquid surface
            val steps = 20
            val waveWidth = topWidth * 0.9f
            val startX = centerX - waveWidth * 0.5f
            for (i in 0..steps) {
                val x = startX + (i.toFloat() / steps) * waveWidth
                val y = liquidTopY + sin(wavePhase + (i * 0.4f)) * waveAmplitude
                lineTo(x, y)
            }

            lineTo(centerX + bottomWidth * 0.45f, glassBottomY - 10f)
            lineTo(centerX - bottomWidth * 0.45f, glassBottomY - 10f)
            close()
        }

        drawPath(
            path = liquidPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF38BDF8), // Cyan surface
                    Color(0xFF0284C7), // Ocean blue
                    Color(0xFF0F766E)  // Deep teal
                )
            )
        )

        // 3. Floating Ice Cube (3D isometric rounded cube)
        val iceX = centerX - width * 0.10f
        val iceY = liquidTopY + sin(wavePhase) * 5f - 6f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xD9FFFFFF), Color(0x66BAE6FD)),
                start = Offset(iceX, iceY),
                end = Offset(iceX + 32f, iceY + 32f)
            ),
            topLeft = Offset(iceX, iceY),
            size = Size(28f, 28f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // 4. Rising Hydration Bubbles
        val bubble1Y = (liquidTopY + (wavePhase / (2f * PI.toFloat())) * 70f) % (glassBottomY - liquidTopY) + liquidTopY
        drawCircle(
            color = Color(0x88FFFFFF),
            radius = 3.5f,
            center = Offset(centerX + width * 0.12f, bubble1Y)
        )

        val bubble2Y = (liquidTopY + 30f + ((wavePhase + 1f) / (2f * PI.toFloat())) * 60f) % (glassBottomY - liquidTopY) + liquidTopY
        drawCircle(
            color = Color(0x66BAE6FD),
            radius = 5f,
            center = Offset(centerX - width * 0.05f, bubble2Y)
        )

        // 5. Glass Wall & Specular 3D Reflection
        val glassPath = Path().apply {
            moveTo(centerX - topWidth * 0.5f, glassTopY)
            lineTo(centerX - bottomWidth * 0.5f, glassBottomY)
            lineTo(centerX + bottomWidth * 0.5f, glassBottomY)
            lineTo(centerX + topWidth * 0.5f, glassTopY)
            close()
        }
        drawPath(
            path = glassPath,
            color = Color(0x3338BDF8), // Translucent cyan tint
            style = Stroke(width = 4f)
        )

        // Left specular reflection gleam
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x88FFFFFF), Color.Transparent)
            ),
            start = Offset(centerX - topWidth * 0.42f, glassTopY + 12f),
            end = Offset(centerX - bottomWidth * 0.42f, glassBottomY - 14f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Combined Dual 3D Wellness Animation Card (Walking + Drinking Water)
 */
@Composable
fun WellnessDualAnimationDisplay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkSurfaceElevated, RoundedCornerShape(20.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Walking Person
            Box(
                modifier = Modifier
                    .weight(1f)
                    .size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                WalkingPerson3DAnimation(modifier = Modifier.fillMaxSize())
            }

            // Hydration Glass
            Box(
                modifier = Modifier
                    .weight(1f)
                    .size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                DrinkingWater3DAnimation(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
