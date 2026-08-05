package com.benegedeniz.budsdynamiceq.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    color: Color? = null,
    alpha: Float = 0.5f,
    minAlpha: Float = 0.2f
): Modifier = composed {
    val targetAlpha = if (state.isScrollInProgress) alpha else minAlpha
    val duration = if (state.isScrollInProgress) 150 else 500
    
    val scrollbarAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration),
        label = "scrollbar_alpha"
    )
    
    val scrollbarColor = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    
    drawWithContent {
        drawContent()
        
        val needDrawScrollbar = state.isScrollInProgress || scrollbarAlpha > 0.0f
        
        if (needDrawScrollbar && state.maxValue > 0) {
            val visibleHeight: Float = this.size.height - state.maxValue
            val scrollbarHeight: Float = (visibleHeight * (visibleHeight / this.size.height)).coerceAtLeast(10.dp.toPx())
            val scrollPercent: Float = state.value.toFloat() / state.maxValue.toFloat()
            val scrollbarOffsetY: Float = scrollPercent * (this.size.height - scrollbarHeight)
            
            drawRoundRect(
                color = scrollbarColor.copy(alpha = scrollbarAlpha),
                topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
}
