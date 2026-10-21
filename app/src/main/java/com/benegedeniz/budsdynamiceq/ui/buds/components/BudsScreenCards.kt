package com.benegedeniz.budsdynamiceq.ui.buds.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.bluetooth.BudsModel
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.foundation.clickable



import com.benegedeniz.budsdynamiceq.ui.components.BaseCard
    
@Composable
fun VoiceDetectCard(
    isConnected: Boolean,
    placementL: PlacementState,
    placementR: PlacementState,
    conversationDetectionEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    BaseCard(
        modifier = modifier.animateContentSize()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
            val bothInEar = isConnected && placementL == PlacementState.WEARING && placementR == PlacementState.WEARING
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .alpha(if (bothInEar) 1f else 0.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.conversation_detection),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.automatically_switches_to_ambient_mode_w),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!bothInEar && isConnected) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.requires_both_earbuds_to_be_worn),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = conversationDetectionEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onCheckedChange(it)
                    },
                    enabled = bothInEar
                )
            }
        }
    }
}

@Composable
fun AutoMediaControlsCard(
    isConnected: Boolean,
    effectiveModel: com.benegedeniz.budsdynamiceq.bluetooth.BudsModel,
    pauseMediaOnConversation: Boolean,
    playMediaOnAnc: Boolean,
    onPauseMediaChange: (Boolean) -> Unit,
    onPlayMediaChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    BaseCard(
        modifier = modifier
    ) {
        Column {
            if (effectiveModel.supportsTransparencyNC) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .alpha(if (isConnected) 1f else 0.5f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_pause_media),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.pauses_media_when_ambient_mode_is_trigge),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = pauseMediaOnConversation,
                        onCheckedChange = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onPauseMediaChange(it)
                        },
                        enabled = isConnected
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .alpha(if (isConnected) 1f else 0.5f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auto_play_on_anc),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.plays_media_when_anc_is_triggered),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = playMediaOnAnc,
                    onCheckedChange = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onPlayMediaChange(it)
                    },
                    enabled = isConnected
                )
            }
        }
    }
}

@Composable
fun WearStateActionsCard(
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseCard(
        modifier = modifier,
        onClick = { onClick() },
        enabled = isConnected
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .alpha(if (isConnected) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Headset,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wear_state_actions),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun FitTestCard(
    isConnected: Boolean,
    placementL: PlacementState,
    placementR: PlacementState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fitTestEnabled = isConnected && placementL == PlacementState.WEARING && placementR == PlacementState.WEARING
    BaseCard(
        modifier = modifier,
        onClick = { onClick() },
        enabled = fitTestEnabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .alpha(if (fitTestEnabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Hearing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.earbud_fit_test),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!fitTestEnabled && isConnected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.requires_both_earbuds_to_be_worn),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun EqualizerCard(
    isConnected: Boolean,
    currentPreset: EqPreset?,
    isRuleActive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseCard(
        modifier = modifier,
        onClick = { onClick() },
        enabled = isConnected && !isRuleActive
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .alpha(if (isConnected && !isRuleActive) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.equalizer),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (currentPreset != null && currentPreset != EqPreset.DEFAULT && currentPreset != EqPreset.IGNORE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(currentPreset.displayNameRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isRuleActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.equalizer_disabled_by_rule),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun FindMyEarbudsCard(
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseCard(
        modifier = modifier,
        onClick = { onClick() },
        enabled = isConnected
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .alpha(if (isConnected) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.find_my_earbuds),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SoundBalanceEntryCard(
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BaseCard(
        modifier = modifier,
        onClick = { onClick() },
        enabled = isConnected
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .alpha(if (isConnected) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.left_right_sound_balance),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}



@Composable
fun DoubleTapEdgeCard(
    isConnected: Boolean,
    doubleTapEdgeEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (isConnected) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.double_tap_earbud_edge),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.double_tap_earbud_edge_desc),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    softWrap = true
                )
            }
        }
        Switch(
            checked = doubleTapEdgeEnabled,
            onCheckedChange = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            },
            enabled = isConnected,
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
fun InEarDetectionForCallsCard(
    isConnected: Boolean,
    inEarDetectionForCalls: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (isConnected) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PhoneCallback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.in_ear_detection_for_calls),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.play_calls_through_earbuds_when_in_ear_s),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    softWrap = true
                )
            }
        }
        Switch(
            checked = inEarDetectionForCalls,
            onCheckedChange = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            },
            enabled = isConnected,
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
fun UseAmbientSoundDuringCallsCard(
    isConnected: Boolean,
    useAmbientSoundDuringCalls: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (isConnected) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PhoneInTalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.use_ambient_sound_during_calls),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = true
            )
        }
        Switch(
            checked = useAmbientSoundDuringCalls,
            onCheckedChange = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            },
            enabled = isConnected,
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
fun NoiseControlWithOneEarbudCard(
    isConnected: Boolean,
    oneEarbudNoiseControlEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (isConnected) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Hearing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.noise_control_with_one_earbud),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = true
            )
        }
        Switch(
            checked = oneEarbudNoiseControlEnabled,
            onCheckedChange = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            },
            enabled = isConnected,
            modifier = Modifier.scale(0.85f)
        )
    }
}




@Composable
fun TransparentModeVolumeCard(
    isConnected: Boolean,
    effectiveModel: BudsModel,
    volume: Int,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    asCard: Boolean = true,
    initiallyExpanded: Boolean = asCard
) {
    if (!effectiveModel.supportsTransparentVolume) return
    val haptic = LocalHapticFeedback.current
    val maxVolume = effectiveModel.maxAmbientVolume.coerceAtLeast(1)
    val currentVolume = volume.coerceIn(0, maxVolume)

    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    var isDragging by remember { mutableStateOf(false) }
    var localVolume by remember(volume) { mutableFloatStateOf(currentVolume.toFloat()) }
    var lastSentTime by remember { mutableLongStateOf(0L) }
    var lastHapticValue by remember { mutableIntStateOf(currentVolume) }

    val activeIntVolume = if (isDragging) kotlin.math.round(localVolume).toInt().coerceIn(0, maxVolume) else currentVolume

    val volumeLabel = when {
        maxVolume <= 2 -> when (activeIntVolume) {
            0 -> stringResource(R.string.ambient_level_low)
            1 -> stringResource(R.string.ambient_level_medium)
            else -> stringResource(R.string.ambient_level_high)
        }
        else -> stringResource(R.string.ambient_level_step, activeIntVolume + 1)
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (asCard) 20.dp else 16.dp, vertical = if (asCard) 20.dp else 8.dp)
                .alpha(if (isConnected) 1f else 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isConnected) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        isExpanded = !isExpanded
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.transparent_mode_volume),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = volumeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.toggle_more_settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessHigh)),
                exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessHigh))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.transparent_mode_volume_desc),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = if (isDragging) localVolume else currentVolume.toFloat(),
                        onValueChange = { newVal ->
                            isDragging = true
                            localVolume = newVal
                            val newInt = kotlin.math.round(newVal).toInt().coerceIn(0, maxVolume)
                            if (newInt != lastHapticValue) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                lastHapticValue = newInt
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastSentTime > 250) {
                                onVolumeChange(newInt)
                                lastSentTime = now
                            }
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            val finalInt = kotlin.math.round(localVolume).toInt().coerceIn(0, maxVolume)
                            onVolumeChange(finalInt)
                        },
                        valueRange = 0f..maxVolume.toFloat(),
                        steps = if (maxVolume > 1) maxVolume - 1 else 0,
                        enabled = isConnected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (maxVolume <= 2) {
                            Text(
                                text = stringResource(R.string.ambient_level_low),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Text(
                                text = stringResource(R.string.ambient_level_medium),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Text(
                                text = stringResource(R.string.ambient_level_high),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        } else {
                            for (step in 1..maxVolume + 1) {
                                Text(
                                    text = "$step",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (asCard) {
        BaseCard(modifier = modifier.animateContentSize()) {
            content()
        }
    } else {
        Box(modifier = modifier.animateContentSize()) {
            content()
        }
    }
}

@Composable
fun AncLevelCard(
    isConnected: Boolean,
    effectiveModel: BudsModel,
    level: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    asCard: Boolean = true,
    initiallyExpanded: Boolean = asCard
) {
    if (!effectiveModel.supportsAncLevel) return
    val haptic = LocalHapticFeedback.current
    val maxLevel = effectiveModel.maxAncLevel.coerceAtLeast(1)
    val currentLevel = level.coerceIn(0, maxLevel)

    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    var isDragging by remember { mutableStateOf(false) }
    var localLevel by remember(level) { mutableFloatStateOf(currentLevel.toFloat()) }
    var lastSentTime by remember { mutableLongStateOf(0L) }
    var lastHapticValue by remember { mutableIntStateOf(currentLevel) }

    val activeIntLevel = if (isDragging) kotlin.math.round(localLevel).toInt().coerceIn(0, maxLevel) else currentLevel

    val levelLabel = when {
        maxLevel <= 1 -> when (activeIntLevel) {
            0 -> stringResource(R.string.anc_level_low)
            else -> stringResource(R.string.anc_level_high)
        }
        else -> stringResource(R.string.anc_level_step, activeIntLevel + 1)
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (asCard) 20.dp else 16.dp, vertical = if (asCard) 20.dp else 8.dp)
                .alpha(if (isConnected) 1f else 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isConnected) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        isExpanded = !isExpanded
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.anc_level),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = levelLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.toggle_more_settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessHigh)),
                exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessHigh))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.anc_level_desc),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = if (isDragging) localLevel else currentLevel.toFloat(),
                        onValueChange = { newVal ->
                            isDragging = true
                            localLevel = newVal
                            val newInt = kotlin.math.round(newVal).toInt().coerceIn(0, maxLevel)
                            if (newInt != lastHapticValue) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                lastHapticValue = newInt
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastSentTime > 250) {
                                onLevelChange(newInt)
                                lastSentTime = now
                            }
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            val finalInt = kotlin.math.round(localLevel).toInt().coerceIn(0, maxLevel)
                            onLevelChange(finalInt)
                        },
                        valueRange = 0f..maxLevel.toFloat(),
                        steps = if (maxLevel > 1) maxLevel - 1 else 0,
                        enabled = isConnected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (maxLevel <= 1) {
                            Text(
                                text = stringResource(R.string.anc_level_low),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Text(
                                text = stringResource(R.string.anc_level_high),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        } else {
                            for (step in 1..maxLevel + 1) {
                                Text(
                                    text = "$step",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (asCard) {
        BaseCard(modifier = modifier.animateContentSize()) {
            content()
        }
    } else {
        Box(modifier = modifier.animateContentSize()) {
            content()
        }
    }
}

@Composable
fun NoiseControlsCard(
    effectiveModel: BudsModel,
    activeNoiseControl: NoiseControlMode,
    isConnected: Boolean,
    placementL: PlacementState,
    placementR: PlacementState,
    oneEarbudNoiseControlEnabled: Boolean,
    onNoiseControlSelect: (NoiseControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    BaseCard(
        modifier = modifier.animateContentSize(
            animationSpec = spring(stiffness = Spring.StiffnessHigh)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val controls = buildList {
                add(NoiseControlMode.OFF)
                if (effectiveModel.supportsTransparencyNC) add(NoiseControlMode.TRANSPARENT)
                if (effectiveModel.supportsAdaptiveNC) add(NoiseControlMode.ADAPTIVE)
                add(NoiseControlMode.NOISE_CANCELLATION)
            }
            val bothInEar = isConnected && placementL == PlacementState.WEARING && placementR == PlacementState.WEARING
            val anyInEar = isConnected && (placementL == PlacementState.WEARING || placementR == PlacementState.WEARING)
            val isBudsTransparencyAllowed = effectiveModel == BudsModel.BUDS_3_PRO || effectiveModel == BudsModel.BUDS_4_PRO
            controls.forEach { mode ->
                val isSelected = activeNoiseControl == mode
                val isModeEnabled = if (!isConnected || !anyInEar) {
                    false
                } else if (bothInEar || oneEarbudNoiseControlEnabled) {
                    true
                } else {
                    if (isBudsTransparencyAllowed) {
                        mode == NoiseControlMode.OFF || mode == NoiseControlMode.TRANSPARENT
                    } else {
                        mode == NoiseControlMode.OFF
                    }
                }
                
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    animationSpec = tween(300), label = ""
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(300), label = ""
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(300), label = ""
                )
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .bounceClick(enabled = isModeEnabled) { onNoiseControlSelect(mode) }
                        .padding(8.dp)
                        .alpha(if (isModeEnabled) 1f else 0.5f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when(mode) {
                                NoiseControlMode.NOISE_CANCELLATION -> Icons.AutoMirrored.Filled.VolumeOff
                                NoiseControlMode.OFF -> Icons.Default.Close
                                NoiseControlMode.TRANSPARENT -> Icons.Default.Hearing
                                NoiseControlMode.ADAPTIVE -> Icons.Default.AutoAwesome
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = iconTint
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(mode.displayNameRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureToggleCard(
    title: String,
    description: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    BaseCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isChecked,
                onCheckedChange = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                },
                enabled = enabled
            )
        }
    }
}

@Composable
fun ActionButtonCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    BaseCard(
        modifier = modifier,
        onClick = { onClick() },
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
