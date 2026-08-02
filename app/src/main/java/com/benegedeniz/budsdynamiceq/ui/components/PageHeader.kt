package com.benegedeniz.budsdynamiceq.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

@Composable
fun PageHeader(
    title: String,
    isScrolled: Boolean,
    modifier: Modifier = Modifier,
    actionIcon: @Composable (() -> Unit)? = null
) {
    // Smooth progress for layout padding and text size scaling
    val textProgress by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "textProgress"
    )

    // Dissolve background pill first (90ms) when scrolling back to top
    val pillAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isScrolled) 250 else 90,
            easing = FastOutLinearInEasing
        ),
        label = "pillAlpha"
    )

    val headerPaddingHorizontal = lerp(24.dp, 8.dp, textProgress)
    val headerPaddingTop = lerp(48.dp, 8.dp, textProgress)
    val titlePaddingH = lerp(0.dp, 16.dp, textProgress)
    val titlePaddingV = lerp(0.dp, 8.dp, textProgress)
    val fontSize = lerp(30.sp, 18.sp, textProgress)

    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f * pillAlpha)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = headerPaddingHorizontal)
            .padding(top = headerPaddingTop)
            .zIndex(1f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title Bubble
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(backgroundColor)
                .padding(horizontal = titlePaddingH, vertical = titlePaddingV)
        ) {
            Text(
                text = title,
                fontSize = fontSize,
                fontWeight = if (textProgress > 0.5f) FontWeight.SemiBold else FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Action Bubble
        if (actionIcon != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(backgroundColor)
            ) {
                actionIcon()
            }
        }
    }
}
