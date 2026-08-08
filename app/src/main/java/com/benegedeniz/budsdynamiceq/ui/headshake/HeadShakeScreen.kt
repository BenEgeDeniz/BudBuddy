package com.benegedeniz.budsdynamiceq.ui.headshake

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benegedeniz.budsdynamiceq.ui.theme.StatusActiveGreen
import com.benegedeniz.budsdynamiceq.ui.theme.StatusErrorRed

import com.benegedeniz.budsdynamiceq.ui.components.SearchBarInput
import com.benegedeniz.budsdynamiceq.ui.headshake.components.*
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
import com.benegedeniz.budsdynamiceq.data.model.FitTestResult
import com.benegedeniz.budsdynamiceq.data.model.GestureAction
import com.benegedeniz.budsdynamiceq.data.model.getDisplayName
import com.benegedeniz.budsdynamiceq.data.model.HeadGesture
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.R

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadShakeScreen(
    viewModel: HeadShakeViewModel = viewModel(),
    onOpenSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE)
    var hasSeenGesturesIntro by remember { mutableStateOf(prefs.getBoolean("has_seen_gestures_intro", false)) }
    
    val uiState by viewModel.uiState.collectAsState()
    val gestures = uiState.gestures
    val headShakeEnabled = uiState.headShakeEnabled
    val isMissingEarbud = uiState.isMissingEarbud
    val isConnected = uiState.isConnected
    val recordingState = uiState.recordingState
    val spatialAudioConflict = uiState.spatialAudioConflict
    val doubleTapEdgeConflict = uiState.doubleTapEdgeConflict
    val isUiLocked = uiState.isUiLocked
    val requireBothEarbuds = uiState.requireBothEarbuds
    val lastDetectedGesture = uiState.lastDetectedGesture
    val activeImuSide = uiState.activeImuSide
    val activeImuReason = uiState.activeImuReason
    val invertPitch = uiState.invertPitch
    val isMutedByNoise = lastDetectedGesture?.isNoiseProfile == true && lastDetectedGesture?.blockGesturesOnMatch == true

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val effectiveEnabled = headShakeEnabled && !isMissingEarbud

    var showVisualizer by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showMcIntro by remember { mutableStateOf(false) }
    
    if (headShakeEnabled && !hasSeenGesturesIntro) {
        GesturesIntroDialog(
            onDismiss = {
                prefs.edit().putBoolean("has_seen_gestures_intro", true).apply()
                hasSeenGesturesIntro = true
            }
        )
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20 } }
    


    LaunchedEffect(isConnected) {
        if (isConnected) {
            viewModel.checkSpatialSensorAvailability()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = !viewModel.isMovementCancellingScreenOpen,
            enter = androidx.compose.animation.slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { -it } + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
            exit = androidx.compose.animation.slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { -it } + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
        ) {
            var overscrollAmount by remember { mutableFloatStateOf(0f) }

            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (source == NestedScrollSource.UserInput) {
                            if (available.y > 0 && !listState.canScrollBackward) {
                                overscrollAmount += available.y
                                return Offset(0f, available.y)
                            } else if (overscrollAmount > 0 && available.y < 0) {
                                val consumed = minOf(overscrollAmount, -available.y)
                                overscrollAmount -= consumed
                                return Offset(0f, -consumed)
                            }
                        }
                        return Offset.Zero
                    }
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        return Offset.Zero
                    }
                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (overscrollAmount > 250f) {
                            onOpenSearch()
                        }
                        overscrollAmount = 0f
                        return super.onPreFling(available)
                    }
                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        overscrollAmount = 0f
                        return super.onPostFling(consumed, available)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 140.dp)
                ) {

                item {
                    val searchThreshold = 250f
                    val isReadyToSearch = overscrollAmount > searchThreshold
                    
                    val rotation by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isReadyToSearch) 180f else 0f)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isReadyToSearch) stringResource(R.string.release_to_search_app_wide) else stringResource(R.string.pull_down_to_search),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    GesturesStatusCard(
                        isConnected = isConnected,
                        isMissingEarbud = isMissingEarbud,
                        headShakeEnabled = headShakeEnabled,
                        effectiveEnabled = effectiveEnabled,
                        gestures = gestures,
                        spatialAudioConflict = spatialAudioConflict,
                        doubleTapEdgeConflict = doubleTapEdgeConflict,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            if (checked) {
                                if (isMissingEarbud) {
                                    viewModel.forceHeadshakeOn()
                                } else {
                                    viewModel.toggleHeadShake(true)
                                }
                            } else {
                                viewModel.toggleHeadShake(false)
                            }
                        }
                    )
                }

            if (spatialAudioConflict && isConnected) {
                item {
                    SpatialAudioConflictCard(
                        onRetryClick = { viewModel.checkSpatialSensorAvailability() }
                    )
                }
            }

            if (doubleTapEdgeConflict && isConnected && !spatialAudioConflict) {
                item {
                    DoubleTapEdgeConflictCard()
                }
            }

            item {
                MovementCancellingCard(
                    isConnected = isConnected,
                    isUiLocked = isUiLocked,
                    onCardClick = { viewModel.isMovementCancellingScreenOpen = true },
                    onInfoClick = { showMcIntro = true }
                )

                    var settingsExpanded by remember { mutableStateOf(false) }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateContentSize(
                                animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh)
                            )
                            .clickable { settingsExpanded = !settingsExpanded },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(stringResource(R.string.settings),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = if (settingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (settingsExpanded) stringResource(R.string.desc_collapse_settings) else stringResource(R.string.desc_expand_settings)
                                )
                            }
                            
                            androidx.compose.animation.AnimatedVisibility(
                                visible = settingsExpanded,
                                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150)) + androidx.compose.animation.expandVertically(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh), expandFrom = Alignment.Top),
                                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(120)) + androidx.compose.animation.shrinkVertically(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh), shrinkTowards = Alignment.Top)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.live_preview),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            
                            LivePreviewSection(
                                viewModel = viewModel
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            // requireBothEarbuds is accessed via uiState.requireBothEarbuds above
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.toggleRequireBothEarbuds(!requireBothEarbuds) }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.require_both_earbuds), 
                                    style = MaterialTheme.typography.bodySmall, 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = requireBothEarbuds,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleRequireBothEarbuds(it)
                                    },
                                    enabled = true,
                                    modifier = Modifier.scale(0.7f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            
                                }
                            }
                        }
                    }
                }

            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isMutedByNoise,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Top),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.SensorsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.headshake_muted_movement, lastDetectedGesture?.name ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            val regularGestures = gestures.filter { !it.isNoiseProfile }
            
            if (regularGestures.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.no_gestures_yet),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(stringResource(R.string.tap_to_create_your_first_head_gesture),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                item {
                    
                    if (viewModel.editingFlowForGesture != null) {
                        GestureEditScreen(
                            gesture = viewModel.editingFlowForGesture!!,
                            onSave = { name, actions, playChime ->
                                viewModel.updateGestureNameAndActions(viewModel.editingFlowForGesture!!.id, name, actions, playChime)
                                viewModel.editingFlowForGesture = null
                            },
                            onDismiss = { viewModel.editingFlowForGesture = null }
                        )
                    }
                    
                    Column {
                        regularGestures.forEach { gesture ->
                                GestureCard(
                                    gesture = gesture,
                                    canImprove = isConnected && effectiveEnabled,
                                onToggle = { enabled -> viewModel.toggleGesture(gesture, enabled) },
                                onDelete = { viewModel.deleteGesture(gesture.id) },
                                onImprove = { viewModel.improveDetection(gesture) },
                                onEditFlow = { viewModel.editingFlowForGesture = gesture },
                                isDetected = lastDetectedGesture?.id == gesture.id
                            )
                        }
                    }
                }
            }
        }

        com.benegedeniz.budsdynamiceq.ui.components.PageHeader(
            title = stringResource(R.string.gestures),
            isScrolled = isScrolled,
            actionIcon = {
                Row {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about_gestures), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showVisualizer = true }) {
                        Icon(Icons.Default.QueryStats, contentDescription = stringResource(R.string.data_stream_visualizer))
                    }
                }
            }
        )
        // Floating action button moved to MainActivity's bottom navbar
    }
    }

    var hasVisualizerBeenShown by remember { mutableStateOf(false) }

    LaunchedEffect(showVisualizer) {
        if (showVisualizer) {
            hasVisualizerBeenShown = true
            viewModel.startVisualizer()
        } else if (hasVisualizerBeenShown) {
            viewModel.stopVisualizer()
        }
    }

    if (showVisualizer) {
        IMUVisualizerDialog(
            viewModel = viewModel,
            onDismiss = { showVisualizer = false }
        )
    }

    if (showInfoDialog) {
        GesturesIntroDialog(onDismiss = { showInfoDialog = false })
    }
    
    if (showMcIntro) {
        MovementCancellingIntroDialog(onDismiss = { showMcIntro = false })
    }
    
    androidx.compose.animation.AnimatedVisibility(
        visible = viewModel.isMovementCancellingScreenOpen,
        enter = androidx.compose.animation.slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { it } + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
        exit = androidx.compose.animation.slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { it } + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
    ) {
        androidx.activity.compose.BackHandler(enabled = viewModel.isMovementCancellingScreenOpen) {
            viewModel.isMovementCancellingScreenOpen = false
        }
        DisposableEffect(Unit) {
            onDispose {
                viewModel.stopVisualizer()
                viewModel.isMovementCancellingScreenOpen = false
            }
        }
        MovementCancellingScreen(
            viewModel = viewModel,
            onBack = { viewModel.isMovementCancellingScreenOpen = false }
        )
    }

    if (viewModel.isSensorDebugScreenOpen) {
        val budsController = com.benegedeniz.budsdynamiceq.di.ServiceLocator.provideBudsController(context)
        SensorDebugScreen(
            budsController = budsController,
            onNavigateBack = { viewModel.isSensorDebugScreenOpen = false }
        )
    }

    // removed nested LivePreviewSection    
    
    if (recordingState != RecordingState.IDLE) {
        androidx.activity.compose.BackHandler {
            viewModel.cancelRecording()
        }
        GestureRecordingScreen(viewModel = viewModel)
    }
    } // Close root Box
}

@Composable
fun GestureCard(
    gesture: HeadGesture,
    canImprove: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onImprove: () -> Unit,
    onEditFlow: () -> Unit,
    isDetected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val defaultContainerColor = MaterialTheme.colorScheme.surface
    val flashColor = MaterialTheme.colorScheme.primaryContainer
    
    val containerColor = remember { androidx.compose.animation.Animatable(defaultContainerColor) }

    LaunchedEffect(defaultContainerColor) {
        if (containerColor.targetValue != flashColor) {
            containerColor.snapTo(defaultContainerColor)
        }
    }

    LaunchedEffect(isDetected) {
        if (isDetected) {
            containerColor.animateTo(
                targetValue = flashColor,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 100)
            )
            containerColor.animateTo(
                targetValue = defaultContainerColor,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 800)
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .bounceClick { onEditFlow() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor.value,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gesture.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (gesture.actions.size > 1) Icons.Default.LinearScale else {
                            when(val action = gesture.actions.firstOrNull()) {
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.SystemAction -> {
                                    when(action.action) {
                                        GestureAction.PLAY_PAUSE -> Icons.Default.PlayArrow
                                        GestureAction.PLAY -> Icons.Default.PlayArrow
                                        GestureAction.PAUSE -> Icons.Default.Pause
                                        GestureAction.NEXT_TRACK -> Icons.Default.SkipNext
                                        GestureAction.PREVIOUS_TRACK -> Icons.Default.SkipPrevious
                                        GestureAction.ANNOUNCE_TRACK -> Icons.Default.MusicNote
                                        GestureAction.NC_TOGGLE -> Icons.Default.Hearing
                                        GestureAction.NC_ACTIVE -> Icons.AutoMirrored.Filled.VolumeOff
                                        GestureAction.NC_OFF -> Icons.Default.Close
                                        GestureAction.NC_TRANSPARENT -> Icons.Default.Hearing
                                        GestureAction.NC_ADAPTIVE -> Icons.Default.AutoAwesome
                                        GestureAction.VOICE_ASSISTANT -> Icons.Default.Mic
                                        GestureAction.ACCEPT_CALL -> Icons.Default.Call
                                        GestureAction.REJECT_CALL -> Icons.Default.CallEnd
                                        GestureAction.READ_NOTIFICATIONS -> Icons.Default.Notifications
                                        GestureAction.SPEAK_TEXT -> Icons.Default.RecordVoiceOver
                                        GestureAction.LAUNCH_APP -> Icons.Default.Apps
                                        GestureAction.SET_VOLUME -> Icons.AutoMirrored.Filled.VolumeUp
                                        GestureAction.MODIFY_VOLUME_INCREASE -> Icons.AutoMirrored.Filled.VolumeUp
                                        GestureAction.MODIFY_VOLUME_DECREASE -> Icons.AutoMirrored.Filled.VolumeDown
                                        GestureAction.FIT_TEST -> Icons.Default.CheckCircle
                                        GestureAction.NO_ACTION -> Icons.Default.Cancel
                                    }
                                }
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.AppAction -> Icons.Default.Apps
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.DelayAction -> Icons.Default.Timer
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.VolumeAction -> Icons.AutoMirrored.Filled.VolumeUp
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction -> if (action.increase) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction -> Icons.Default.RecordVoiceOver
                                else -> Icons.Default.HeadsetOff
                            }
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (gesture.actions.size > 1) stringResource(R.string.headshake_steps, gesture.actions.size) else {
                            when(val action = gesture.actions.firstOrNull()) {
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.SystemAction -> action.action.getDisplayName()
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.AppAction -> if (action.appName.isNotBlank() && action.appName != stringResource(R.string.select_app_short)) action.appName else stringResource(R.string.action_start_application)
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.DelayAction -> stringResource(R.string.delay)
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.VolumeAction -> stringResource(R.string.action_set_volume_to, action.percentage)
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction -> if (action.increase) stringResource(R.string.action_increase_vol, action.percentage) else stringResource(R.string.action_decrease_vol, action.percentage)
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction -> stringResource(R.string.action_speak_out_loud)
                                else -> stringResource(R.string.action_none)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            var showDeleteConfirm by remember { mutableStateOf(false) }
            
            if (showDeleteConfirm) {
                AlertDialog(
                    shape = RoundedCornerShape(28.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text(stringResource(R.string.delete_gesture)) },
                    text = { Text(stringResource(R.string.delete_confirm, gesture.name)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirm = false
                            onDelete()
                        }) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            IconButton(onClick = onEditFlow) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_flow), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onImprove, enabled = canImprove) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = stringResource(R.string.improve_detection), tint = if (canImprove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
            }
            Switch(
                checked = gesture.enabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onToggle(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun IMUVisualizerDialog(
    viewModel: HeadShakeViewModel,
    onDismiss: () -> Unit
) {
    val sample by viewModel.spatialDataFlow.collectAsState(initial = null)
    
    AlertDialog(
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.imu_data_stream)) },
        text = {
            Column {
                val s = sample
                if (s == null) {
                    Text(stringResource(R.string.waiting_for_data_make_sure_you_are_conne))
                } else {
                    Text(stringResource(R.string.quaternion), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.x_format, String.format("%.4f", s.x)))
                    Text(stringResource(R.string.y_format, String.format("%.4f", s.y)))
                    Text(stringResource(R.string.z_format, String.format("%.4f", s.z)))
                    Text(stringResource(R.string.w_format, String.format("%.4f", s.w)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun LivePreviewSection(
    viewModel: com.benegedeniz.budsdynamiceq.ui.headshake.HeadShakeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val lastDetectedGesture = uiState.lastDetectedGesture
    val activeImuSide = uiState.activeImuSide
    val activeImuReason = uiState.activeImuReason
    val invertPitch = uiState.invertPitch
    val currentSample by viewModel.spatialDataFlow.collectAsState(initial = null)
    
    Box(contentAlignment = Alignment.Center) {
        Head3DCanvas(
            sample = currentSample,
            modifier = Modifier.size(150.dp),
            resetTrigger = Unit
        )
        // Gesture detection overlay (only for regular gestures)
        androidx.compose.animation.AnimatedVisibility(
            visible = lastDetectedGesture != null && lastDetectedGesture?.isNoiseProfile != true,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (lastDetectedGesture != null && lastDetectedGesture?.isNoiseProfile != true) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = lastDetectedGesture!!.name,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(12.dp))
    
    var debugExpanded by remember { mutableStateOf(false) }

    // Active IMU Status (Debug) Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateContentSize()
            .clickable { debugExpanded = !debugExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.debug_info),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (debugExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            androidx.compose.animation.AnimatedVisibility(visible = debugExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = stringResource(R.string.headshake_active_sensor, activeImuSide.name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.headshake_reason, activeImuReason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (invertPitch) {
                    stringResource(R.string.headshake_pitch_inverted)
                } else {
                    stringResource(R.string.headshake_pitch_normal)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { viewModel.isSensorDebugScreenOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.debug_title))
            }
                }
            }
        }
    }
    
    // Noise profile match status (shown below the head)
    val matchedNoise = lastDetectedGesture?.takeIf { it.isNoiseProfile }
    androidx.compose.animation.AnimatedVisibility(
        visible = matchedNoise != null,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
    ) {
        if (matchedNoise != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SensorsOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = matchedNoise.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.gestures_muted),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GesturesIntroDialog(onDismiss: () -> Unit) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (pagerState.currentPage == page) 1f else 0.5f,
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (page == 0) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .scale(scale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Accessibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = stringResource(R.string.hands_free_control),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.gestures_lets_you_trigger_custom_action_),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.you_can_also_record_continuous_movements),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(stringResource(R.string.spatial_audio_warning),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(stringResource(R.string.spatial_audio_360_audio_must_be_disabled),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .scale(scale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = stringResource(R.string.need_recalibration),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.due_to_a_known_issue_with_the_earbuds_se),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.if_your_gestures_stop_working_correctly_),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(2) { iteration ->
                            val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            val width by androidx.compose.animation.core.animateDpAsState(
                                targetValue = if (pagerState.currentPage == iteration) 24.dp else 8.dp,
                                animationSpec = androidx.compose.animation.core.tween(300)
                            )
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(width)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (pagerState.currentPage < 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage < 1) stringResource(R.string.btn_next) else stringResource(R.string.btn_got_it),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
