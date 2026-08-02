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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benegedeniz.budsdynamiceq.ui.theme.StatusActiveGreen
import com.benegedeniz.budsdynamiceq.ui.theme.StatusErrorRed
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
import com.benegedeniz.budsdynamiceq.data.model.FitTestResult
import com.benegedeniz.budsdynamiceq.data.model.GestureAction
import com.benegedeniz.budsdynamiceq.data.model.HeadGesture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadShakeScreen(
    modifier: Modifier = Modifier,
    viewModel: HeadShakeViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE)
    var hasSeenGesturesIntro by remember { mutableStateOf(prefs.getBoolean("has_seen_gestures_intro", false)) }
    
    val gestures by viewModel.gestures.collectAsState()
    val headShakeEnabled by viewModel.headShakeEnabled.collectAsState()
    val isMissingEarbud by viewModel.isMissingEarbudForHeadshake.collectAsState()
    
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val effectiveEnabled = headShakeEnabled && !isMissingEarbud
    val isConnected by viewModel.isConnected.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val spatialAudioConflict by viewModel.spatialAudioConflict.collectAsState()
    val lastDetectedGesture by viewModel.lastDetectedGesture.collectAsState()
    val isMutedByNoise = lastDetectedGesture?.isNoiseProfile == true && lastDetectedGesture?.blockGesturesOnMatch == true

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
    
    val isUiLocked by viewModel.isUiLocked.collectAsState()

    LaunchedEffect(isConnected) {
        if (isConnected) {
            viewModel.checkSpatialSensorAvailability()
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = !viewModel.isMovementCancellingScreenOpen,
        enter = androidx.compose.animation.slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { -it } + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
        exit = androidx.compose.animation.slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(300)) { -it } + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, top = 140.dp)
            ) {

            item {
                Card(
                    modifier = Modifier
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
                                text = "Gestures",
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
                                    text = if (!isConnected) "Disconnected" 
                                           else if (isMissingEarbud && headShakeEnabled) "Disabled (Earbud missing)"
                                           else if (headShakeEnabled) "Active • ${gestures.filter { !it.isNoiseProfile }.size} gestures" 
                                           else "Disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Switch(
                            checked = effectiveEnabled,
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
                            },
                            enabled = !spatialAudioConflict,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            if (spatialAudioConflict && isConnected) {
                item {
                    Card(
                        modifier = Modifier
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
                                Text(
                                    "Gestures is disabled. Spatial Audio (360 Audio) must be disabled in the Samsung Wearable app. The buds cannot stream raw IMU data properly if Samsung's own head tracking is actively hijacking the sensor.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            androidx.compose.material3.Button(
                                onClick = { viewModel.checkSpatialSensorAvailability() },
                                modifier = Modifier.align(Alignment.End),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                    contentColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text("Retry Connection")
                            }
                        }
                    }
                }
            }


            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .alpha(if (isConnected && !isUiLocked) 1f else 0.5f)
                        .clickable(enabled = isConnected && !isUiLocked) { viewModel.isMovementCancellingScreenOpen = true },
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
                                Text(
                                    "Movement Cancelling",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showMcIntro = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "About Movement Cancelling",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open Movement Cancelling"
                                )
                            }
                        }
                    }

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
                                    Text(
                                        "Settings",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = if (settingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (settingsExpanded) "Collapse Settings" else "Expand Settings"
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
                            Text(
                                "Live Preview",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            
                            LivePreviewSection(
                                viewModel = viewModel
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            val requireBothEarbuds by viewModel.requireBothEarbuds.collectAsState()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.toggleRequireBothEarbuds(!requireBothEarbuds) }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Require Both Earbuds", 
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
                                text = "Gestures muted due to movement: ${lastDetectedGesture?.name}",
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
                        Text(
                            "No gestures yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Tap + to create your first head gesture.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                item {
                    var editingFlowForGesture by remember { mutableStateOf<HeadGesture?>(null) }
                    
                    if (editingFlowForGesture != null) {
                        GestureEditScreen(
                            gesture = editingFlowForGesture!!,
                            onSave = { name, actions, playChime ->
                                viewModel.updateGestureNameAndActions(editingFlowForGesture!!.id, name, actions, playChime)
                                editingFlowForGesture = null
                            },
                            onDismiss = { editingFlowForGesture = null }
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
                                onEditFlow = { editingFlowForGesture = gesture },
                                isDetected = lastDetectedGesture?.id == gesture.id
                            )
                        }
                    }
                }
            }
        }

        com.benegedeniz.budsdynamiceq.ui.components.PageHeader(
            title = "Gestures",
            isScrolled = isScrolled,
            actionIcon = {
                Row {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About Gestures", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showVisualizer = true }) {
                        Icon(Icons.Default.QueryStats, contentDescription = "Data Stream Visualizer")
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

    // removed nested LivePreviewSection    
    
    if (recordingState != RecordingState.IDLE) {
        androidx.activity.compose.BackHandler {
            viewModel.cancelRecording()
        }
        GestureRecordingScreen(viewModel = viewModel)
    }

}

@Composable
fun GestureCard(
    gesture: HeadGesture,
    canImprove: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onImprove: () -> Unit,
    onEditFlow: () -> Unit,
    isDetected: Boolean = false
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
        modifier = Modifier
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
                    color = androidx.compose.ui.graphics.Color.Unspecified
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
                                        GestureAction.NC_ACTIVE -> Icons.Default.VolumeOff
                                        GestureAction.NC_OFF -> Icons.Default.Close
                                        GestureAction.NC_TRANSPARENT -> Icons.Default.Hearing
                                        GestureAction.NC_ADAPTIVE -> Icons.Default.AutoAwesome
                                        GestureAction.VOICE_ASSISTANT -> Icons.Default.Mic
                                        GestureAction.ACCEPT_CALL -> Icons.Default.Call
                                        GestureAction.REJECT_CALL -> Icons.Default.CallEnd
                                        GestureAction.READ_NOTIFICATIONS -> Icons.Default.Notifications
                                        GestureAction.SPEAK_TEXT -> Icons.Default.RecordVoiceOver
                                        GestureAction.LAUNCH_APP -> Icons.Default.Apps
                                        GestureAction.SET_VOLUME -> Icons.Default.VolumeUp
                                        GestureAction.MODIFY_VOLUME_INCREASE -> Icons.Default.VolumeUp
                                        GestureAction.MODIFY_VOLUME_DECREASE -> Icons.Default.VolumeDown
                                        GestureAction.FIT_TEST -> Icons.Default.CheckCircle
                                        GestureAction.NO_ACTION -> Icons.Default.Cancel
                                    }
                                }
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.AppAction -> Icons.Default.Apps
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.DelayAction -> Icons.Default.Timer
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.VolumeAction -> Icons.Default.VolumeUp
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction -> if (action.increase) Icons.Default.VolumeUp else Icons.Default.VolumeDown
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
                        text = if (gesture.actions.size > 1) "${gesture.actions.size} Steps" else {
                            when(val action = gesture.actions.firstOrNull()) {
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.SystemAction -> action.action.displayName
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.AppAction -> if (action.appName.isNotBlank() && action.appName != "Select App") action.appName else "Start Application"
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.DelayAction -> "Delay"
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.VolumeAction -> "Set Volume to ${action.percentage}%"
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction -> if (action.increase) "Increase Vol by ${action.percentage}%" else "Decrease Vol by ${action.percentage}%"
                                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction -> "Speak out loud"
                                else -> "No Action"
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
                    title = { Text("Delete Gesture") },
                    text = { Text("Are you sure you want to delete '${gesture.name}'?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirm = false
                            onDelete()
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            IconButton(onClick = onEditFlow) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Flow", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onImprove, enabled = canImprove) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Improve Detection", tint = if (canImprove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
        title = { Text("IMU Data Stream") },
        text = {
            Column {
                val s = sample
                if (s == null) {
                    Text("Waiting for data... Make sure you are connected and have Gestures enabled.")
                } else {
                    Text("Quaternion:", fontWeight = FontWeight.Bold)
                    Text("X: ${String.format("%.4f", s.x)}")
                    Text("Y: ${String.format("%.4f", s.y)}")
                    Text("Z: ${String.format("%.4f", s.z)}")
                    Text("W: ${String.format("%.4f", s.w)}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun LivePreviewSection(
    viewModel: com.benegedeniz.budsdynamiceq.ui.headshake.HeadShakeViewModel
) {
    val currentSample by viewModel.spatialDataFlow.collectAsState(initial = null)
    val lastDetectedGesture by viewModel.lastDetectedGesture.collectAsState()
    
    val activeImuSide by viewModel.activeImuSide.collectAsState()
    val activeImuReason by viewModel.activeImuReason.collectAsState()
    val invertPitch by viewModel.invertPitch.collectAsState()
    
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
                    text = "Debug Info",
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
                text = "Active Sensor: ${activeImuSide.name}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Reason: $activeImuReason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (invertPitch) {
                    "Pitch: INVERTED (Left earbud IMU is physically rotated 180°)"
                } else {
                    "Pitch: NORMAL (Right earbud is the baseline reference)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
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
                            text = "Gestures muted",
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
                                text = "Hands-free Control",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Gestures lets you trigger custom action flows simply by moving your head. You can record one-off gestures (like a quick nod) to control your media, noise cancellation, calls, and more.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "You can also record continuous movements (like walking) as a 'Movement Cancelling Profile' to mute gestures and prevent accidental triggers while on the move.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "⚠️ Spatial Audio Warning",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Spatial Audio (360 Audio) must be disabled in the Samsung Wearable app. The buds cannot stream raw IMU data properly if Samsung's own head tracking is actively hijacking the sensor.",
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
                                text = "Need Recalibration?",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Due to a known issue with the earbuds' sensor reporting, the gesture pitch (up/down axis) might invert randomly after putting them in.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "If your gestures stop working correctly or seem upside down, simply take one earbud out of your ear and put it back in to recalibrate the sensors.",
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
                            text = if (pagerState.currentPage < 1) "Next" else "Got it",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
