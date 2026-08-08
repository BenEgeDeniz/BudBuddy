package com.benegedeniz.budsdynamiceq.ui.balance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import android.widget.Toast
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benegedeniz.budsdynamiceq.audio.HearingTestManager
import com.benegedeniz.budsdynamiceq.ui.components.PageHeader
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
import com.benegedeniz.budsdynamiceq.ui.buds.BudsViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.R

enum class TestPhase {
    INTRO, LEFT_EAR, RIGHT_EAR, RESULT
}

@Composable
fun SoundBalanceTestScreen(viewModel: BudsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    androidx.activity.compose.BackHandler { onBack() }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(TestPhase.INTRO) }
    var leftThreshold by remember { mutableFloatStateOf(-1f) }
    var rightThreshold by remember { mutableFloatStateOf(-1f) }
    var currentGain by remember { mutableFloatStateOf(1f) }
    
    val testManager = remember { HearingTestManager(context) }
    var isTestActive by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val stereoBalance = uiState.stereoBalance
    val placementL = uiState.placementL
    val placementR = uiState.placementR
    
    LaunchedEffect(placementL, placementR) {
        if (placementL != PlacementState.WEARING || placementR != PlacementState.WEARING) {
            isTestActive = false
            Toast.makeText(context, context.getString(R.string.earbud_removed_aborted), Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    val originalBalance = remember { stereoBalance }
    var didApplyNewBalance by remember { mutableStateOf(false) }

    val originalNcMode = remember { uiState.activeNoiseControl }

    DisposableEffect(Unit) {
        viewModel.setStereoBalance(16)
        viewModel.applyImmediateNoiseControl(NoiseControlMode.NOISE_CANCELLATION)
        onDispose {
            testManager.restoreVolumeImmediately()
            if (!didApplyNewBalance) {
                viewModel.setStereoBalance(originalBalance)
            }
            originalNcMode?.let { viewModel.applyImmediateNoiseControl(it) }
        }
    }

    Scaffold(
        topBar = {
            PageHeader(
                title = stringResource(R.string.sound_balance_test),
                isScrolled = false,
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
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f)).togetherWith(
                        fadeOut(animationSpec = tween(300))
                    )
                },
                modifier = Modifier.fillMaxSize(),
                label = "TestPhaseTransition"
            ) { targetPhase ->
                when (targetPhase) {
                    TestPhase.INTRO -> {
                        IntroPhase {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                testManager.prepareAndMaximizeVolume()
                                phase = TestPhase.LEFT_EAR
                            }
                        }
                    }
                    TestPhase.LEFT_EAR -> {
                        EarTestPhase(
                            title = stringResource(R.string.left_ear_test),
                            description = stringResource(R.string.press_and_hold_the_button_a_tone_will_pl),
                            currentGain = currentGain,
                            testManager = testManager,
                            isLeftEar = true,
                            onGainChanged = { currentGain = it },
                            onTestComplete = { threshold ->
                                leftThreshold = threshold
                                currentGain = 1f
                                phase = TestPhase.RIGHT_EAR
                            }
                        )
                    }
                    TestPhase.RIGHT_EAR -> {
                        EarTestPhase(
                            title = stringResource(R.string.right_ear_test),
                            description = stringResource(R.string.now_testing_your_right_ear_n_npress_and_),
                            currentGain = currentGain,
                            testManager = testManager,
                            isLeftEar = false,
                            onGainChanged = { currentGain = it },
                            onTestComplete = { threshold ->
                                rightThreshold = threshold
                                scope.launch {
                                    testManager.restoreVolume()
                                }
                                phase = TestPhase.RESULT
                            }
                        )
                    }
                    TestPhase.RESULT -> {
                        ResultPhase(
                            leftThreshold = leftThreshold,
                            rightThreshold = rightThreshold,
                            onApply = { recommendedBalance ->
                                didApplyNewBalance = true
                                viewModel.setStereoBalance(recommendedBalance)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            },
                            onRetake = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                leftThreshold = -1f
                                rightThreshold = -1f
                                phase = TestPhase.INTRO
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroPhase(onStart: () -> Unit) {
    var isStarting by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp).padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.find_your_perfect_balance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.this_test_will_determine_the_optimal_lef),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.your_media_will_be_paused_and_system_vol),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.sensory_warning_this_test_plays_a_contin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.please_go_to_a_quiet_environment_active_),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                if (!isStarting) {
                    isStarting = true
                    onStart()
                }
            },
            enabled = !isStarting,
            modifier = Modifier.fillMaxWidth().height(56.dp).bounceClick(),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isStarting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.start_test), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EarTestPhase(
    title: String,
    description: String,
    currentGain: Float,
    testManager: HearingTestManager,
    isLeftEar: Boolean,
    onGainChanged: (Float) -> Unit,
    onTestComplete: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isHolding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Progress indicator for gain (visual feedback, optional but nice)
        if (isHolding) {
            LinearProgressIndicator(
                progress = { currentGain },
                modifier = Modifier.fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isHolding,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { 20 }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { 20 }) + androidx.compose.animation.fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = stringResource(R.string.release_when_silent),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(if (isHolding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        isHolding = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        
                        testManager.startTone(isLeftEar, onGainChanged)
                        
                        var upOrCancel = false
                        while (!upOrCancel) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { !it.pressed }) {
                                upOrCancel = true
                            }
                        }
                        
                        isHolding = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        testManager.stop()
                        onTestComplete(testManager.currentGain)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isHolding,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Text(
                    text = stringResource(R.string.hold_to_hear_tone),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ResultPhase(
    leftThreshold: Float,
    rightThreshold: Float,
    onApply: (Int) -> Unit,
    onRetake: () -> Unit
) {
    // Calculate balance
    // diff ranges from -1.0 to 1.0. 
    // Positive diff means Left needs more volume (Left is worse), shift left (towards 0).
    // Negative diff means Right needs more volume (Right is worse), shift right (towards 32).
    val diff = leftThreshold - rightThreshold
    val recommendedBalance = (16 - (diff * 16)).toInt().coerceIn(0, 32)
    
    val balanceLabel = when (recommendedBalance) {
        16 -> stringResource(R.string.balance_perfectly_balanced)
        in 0..15 -> stringResource(R.string.balance_shifted_left, ((16 - recommendedBalance) / 16f * 100).toInt())
        else -> stringResource(R.string.balance_shifted_right, ((recommendedBalance - 16) / 16f * 100).toInt())
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.test_complete),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.recommended_balance),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = balanceLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { onApply(recommendedBalance) },
            modifier = Modifier.fillMaxWidth().height(56.dp).bounceClick(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.apply_balance), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth().height(56.dp).bounceClick(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.retake_test), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
