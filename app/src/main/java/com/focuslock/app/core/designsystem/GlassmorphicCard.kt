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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    backgroundColor: Color = SurfaceCard.copy(alpha = 0.85f),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Color(0x66000000),
                spotColor = CyanAccent.copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                brush = CardGlowBorder,
                shape = shape
            )
            .padding(20.dp),
        content = content
    )
}
