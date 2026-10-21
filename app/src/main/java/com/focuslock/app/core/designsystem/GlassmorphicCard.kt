package com.focuslock.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    backgroundColor: Color = AppTheme.colors.surface,
    borderColor: Color = AppTheme.colors.border,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = shape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x33000000)
            )
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}

// Retain GlassmorphicCard as alias for seamless backward compatibility
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color = AppTheme.colors.surface,
    content: @Composable BoxScope.() -> Unit
) {
    FocusCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        backgroundColor = backgroundColor,
        content = content
    )
}
