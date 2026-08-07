package com.benegedeniz.budsdynamiceq.ui.findmybuds

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import com.benegedeniz.budsdynamiceq.ui.buds.BudsViewModel
import com.benegedeniz.budsdynamiceq.ui.components.PageHeader
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
import com.benegedeniz.budsdynamiceq.ui.theme.StatusErrorRed

@Composable
fun FindMyBudsScreen(
    viewModel: BudsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.activity.compose.BackHandler { onBack() }
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    val lInEar = uiState.placementL == PlacementState.WEARING
    val rInEar = uiState.placementR == PlacementState.WEARING
    val anyInEar = lInEar || rInEar

    val isSearching = uiState.isSearching
    val isLeftMuted = uiState.isLeftMuted
    val isRightMuted = uiState.isRightMuted

    val scrollState = rememberScrollState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopFindMyEarbuds()
        }
    }

    Scaffold(
        topBar = {
            PageHeader(
                title = stringResource(R.string.find_my_earbuds),
                isScrolled = scrollState.value > 10,
                actionIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Warning Card if earbuds are in ears
            if (anyInEar) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = StatusErrorRed.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val warningText = when {
                            lInEar && rInEar -> stringResource(R.string.fmg_warning_both)
                            lInEar -> stringResource(R.string.fmg_warning_left)
                            else -> stringResource(R.string.fmg_warning_right)
                        }
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusErrorRed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.fmg_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Mute Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MuteCard(
                    title = stringResource(R.string.left_earbud),
                    isMuted = isLeftMuted,
                    isSearching = isSearching,
                    onToggleMute = {
                        viewModel.muteEarbud(!isLeftMuted, isRightMuted)
                    },
                    modifier = Modifier.weight(1f)
                )

                MuteCard(
                    title = stringResource(R.string.right_earbud),
                    isMuted = isRightMuted,
                    isSearching = isSearching,
                    onToggleMute = {
                        viewModel.muteEarbud(isLeftMuted, !isRightMuted)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Start / Stop Test Action Button
            val buttonBgColor by animateColorAsState(
                targetValue = if (isSearching) StatusErrorRed else MaterialTheme.colorScheme.primary,
                animationSpec = tween(300),
                label = "buttonBgColor"
            )

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isSearching) {
                        viewModel.stopFindMyEarbuds()
                    } else {
                        viewModel.startFindMyEarbuds()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .bounceClick(),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonBgColor,
                    contentColor = Color.White
                )
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.stop_finding),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.start_finding),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MuteCard(
    title: String,
    isMuted: Boolean,
    isSearching: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = modifier.bounceClick(enabled = isSearching) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggleMute()
        },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isSearching) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            val chipBgColor = when {
                !isSearching -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                isMuted -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.primaryContainer
            }

            val chipContentColor = when {
                !isSearching -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                isMuted -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(chipBgColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = chipContentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute),
                        style = MaterialTheme.typography.labelMedium,
                        color = chipContentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
