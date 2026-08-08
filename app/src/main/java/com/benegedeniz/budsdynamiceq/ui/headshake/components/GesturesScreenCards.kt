package com.benegedeniz.budsdynamiceq.ui.headshake.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.data.model.HeadGesture
import com.benegedeniz.budsdynamiceq.ui.theme.StatusActiveGreen
import com.benegedeniz.budsdynamiceq.ui.theme.StatusErrorRed

@Composable
fun GesturesStatusCard(
    isConnected: Boolean,
    isMissingEarbud: Boolean,
    headShakeEnabled: Boolean,
    effectiveEnabled: Boolean,
    gestures: List<HeadGesture>,
    spatialAudioConflict: Boolean,
    doubleTapEdgeConflict: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.gestures),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) StatusActiveGreen else StatusErrorRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (!isConnected) stringResource(R.string.buds_disconnected) 
                               else if (isMissingEarbud && headShakeEnabled) stringResource(R.string.headshake_disabled_missing)
                               else if (headShakeEnabled) stringResource(R.string.headshake_active_gestures, gestures.filter { !it.isNoiseProfile }.size) 
                               else stringResource(R.string.status_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            Switch(
                checked = effectiveEnabled,
                onCheckedChange = onCheckedChange,
                enabled = !spatialAudioConflict && !doubleTapEdgeConflict,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun SpatialAudioConflictCard(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.gestures_is_disabled_spatial_audio_360_a),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRetryClick,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                    contentColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(stringResource(R.string.retry_connection))
            }
        }
    }
}

@Composable
fun DoubleTapEdgeConflictCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.gestures_is_disabled_double_tap_edge),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun MovementCancellingCard(
    isConnected: Boolean,
    isUiLocked: Boolean,
    onCardClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (isConnected && !isUiLocked) 1f else 0.5f)
            .clickable(enabled = isConnected && !isUiLocked) { onCardClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SensorsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.movement_cancelling),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.about_movement_cancelling),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.open_movement_cancelling)
                )
            }
        }
    }
}
