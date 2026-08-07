package com.benegedeniz.budsdynamiceq.ui.headshake

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.DialogProperties
import com.benegedeniz.budsdynamiceq.data.model.GestureAction
import com.benegedeniz.budsdynamiceq.data.model.getDisplayName
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureRecordingScreen(viewModel: HeadShakeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.recordingState
    val isConnected by viewModel.isConnected.collectAsState()
    val isRecordingNoise by viewModel.isRecordingNoise.collectAsState()
    val blockGesturesOnMatch by viewModel.blockGesturesOnMatch.collectAsState()
    
    var gestureName by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf<com.benegedeniz.budsdynamiceq.data.model.FlowAction>(com.benegedeniz.budsdynamiceq.data.model.FlowAction.SystemAction(GestureAction.PLAY_PAUSE)) }
    
    // For Noise Profiles
    var blockGestures by remember { mutableStateOf(blockGesturesOnMatch) }

    val conflict by viewModel.conflictWarning.collectAsState()
    
    if (conflict != null) {
        val inTestAndSave = state == RecordingState.ALL_RECORDED || state == RecordingState.TESTING || state == RecordingState.SAVING
        AlertDialog(
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { 
                if (!inTestAndSave) viewModel.dismissConflictWarning() else viewModel.cancelRecording()
            },
            title = { Text(stringResource(R.string.similar_gesture_detected)) },
            text = { 
                if (inTestAndSave) {
                    Text(stringResource(R.string.similar_gesture_save, conflict!!.conflictingGesture?.name ?: ""))
                } else {
                    Text(stringResource(R.string.similar_sample_redo, conflict!!.conflictingGesture?.name ?: ""))
                }
            },
            confirmButton = {
                if (inTestAndSave) {
                    TextButton(onClick = { viewModel.saveGesture(gestureName, selectedAction, ignoreConflict = true) }) {
                        Text(stringResource(R.string.save_anyway))
                    }
                } else {
                    TextButton(onClick = { 
                        viewModel.dismissConflictWarning()
                        viewModel.redoLastRecording() 
                    }) {
                        Text(stringResource(R.string.redo_sample))
                    }
                }
            },
            dismissButton = {
                if (inTestAndSave) {
                    TextButton(onClick = { viewModel.cancelRecording() }) {
                        Text(stringResource(R.string.cancel))
                    }
                } else {
                    TextButton(onClick = { viewModel.dismissConflictWarning() }) {
                        Text(stringResource(R.string.keep_sample))
                    }
                }
            }
        )
    }

    Dialog(
        onDismissRequest = { viewModel.cancelRecording() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                // App bar
                CenterAlignedTopAppBar(
                    title = { 
                        val editingGesture by viewModel.editingGesture.collectAsState()
                        Text(
                            when {
                                editingGesture != null -> stringResource(R.string.recording_improve_detection)
                                isRecordingNoise -> stringResource(R.string.recording_new_noise_profile)
                                else -> stringResource(R.string.recording_new_gesture)
                            }
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.cancelRecording() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                if (!isConnected) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.earbuds_disconnected),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.please_connect_your_galaxy_buds_to_recor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                    return@Surface
                }

                when (state) {
                    RecordingState.SETUP -> SetupStep(
                        name = gestureName,
                        onNameChange = { gestureName = it },
                        action = selectedAction,
                        onActionChange = { selectedAction = it },
                        isNoiseProfile = isRecordingNoise,
                        blockGestures = blockGestures,
                        onBlockGesturesChange = { blockGestures = it },
                        onNext = {
                            if (isRecordingNoise) {
                                viewModel.updateNoiseProfileSettings(gestureName, blockGestures)
                            }
                            viewModel.startRecordingSetup()
                        }
                    )
                    RecordingState.READY_FOR_SAMPLE,
                    RecordingState.COUNTDOWN,
                    RecordingState.RECORDING,
                    RecordingState.CONTINUOUS_RECORDING,
                    RecordingState.SAMPLE_DONE -> RecordingStep(viewModel)
                    RecordingState.ALL_RECORDED,
                    RecordingState.TESTING,
                    RecordingState.SAVING -> TestAndSaveStep(viewModel, gestureName, selectedAction)
                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupStep(
    name: String,
    onNameChange: (String) -> Unit,
    action: com.benegedeniz.budsdynamiceq.data.model.FlowAction,
    onActionChange: (com.benegedeniz.budsdynamiceq.data.model.FlowAction) -> Unit,
    isNoiseProfile: Boolean,
    blockGestures: Boolean,
    onBlockGesturesChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.step_1_details),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(if (isNoiseProfile) stringResource(R.string.noise_profile_name_hint) else stringResource(R.string.gesture_name_hint)) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isNoiseProfile) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeOff, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.global_mute),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.when_this_background_noise_is_detected_a),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        } else {
            var showActionDialog by remember { mutableStateOf(false) }
            var showAppSelectionDialog by remember { mutableStateOf(false) }

            val actionDisplayName = when (action) {
                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.SystemAction -> action.action.getDisplayName()
                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.DelayAction -> "Delay"
                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.AppAction -> stringResource(R.string.action_start_application)
                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.VolumeAction -> stringResource(R.string.action_set_volume_to, action.percentage)
                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction -> {
                    if (action.increase) stringResource(R.string.action_increase_volume_by, action.percentage)
                    else stringResource(R.string.action_decrease_volume_by, action.percentage)
                }
                is com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction -> stringResource(R.string.action_speak_out_loud)
            }

            OutlinedTextField(
                value = actionDisplayName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.action)) },
                shape = RoundedCornerShape(16.dp),
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    .also { interactionSource ->
                        LaunchedEffect(interactionSource) {
                            interactionSource.interactions.collect {
                                if (it is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                    showActionDialog = true
                                }
                            }
                        }
                    }
            )
            
            if (action is com.benegedeniz.budsdynamiceq.data.model.FlowAction.AppAction) {
                Button(
                    onClick = { showAppSelectionDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(if (action.packageName.isEmpty()) stringResource(R.string.select_app_short) else stringResource(R.string.change_app_format, action.appName))
                }
            }

            if (showActionDialog) {
                ActionSelectionDialog(
                    onDismissRequest = { showActionDialog = false },
                    onActionSelected = { a ->
                        if (a == GestureAction.LAUNCH_APP) {
                            onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.AppAction())
                            showAppSelectionDialog = true
                        } else if (a == GestureAction.SET_VOLUME) {
                            onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.VolumeAction())
                        } else if (a == GestureAction.MODIFY_VOLUME_INCREASE) {
                            val currentPct = if (action is com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction) (action as com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction).percentage else 10
                            onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction(increase = true, percentage = currentPct))
                        } else if (a == GestureAction.MODIFY_VOLUME_DECREASE) {
                            val currentPct = if (action is com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction) (action as com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction).percentage else 10
                            onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction(increase = false, percentage = currentPct))
                        } else if (a == GestureAction.SPEAK_TEXT) {
                            val currentText = if (action is com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction) (action as com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction).text else ""
                            val currentAnn = if (action is com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction) (action as com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction).asAnnouncement else true
                            onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction(text = currentText, asAnnouncement = currentAnn))
                        } else {
                            onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.SystemAction(a))
                        }
                        showActionDialog = false
                    }
                )
            }
            
            if (action is com.benegedeniz.budsdynamiceq.data.model.FlowAction.VolumeAction) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.percentage_format, action.percentage.toString()), modifier = Modifier.width(48.dp))
                    Slider(
                        value = action.percentage / 100f,
                        onValueChange = { onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.VolumeAction((it * 100).toInt())) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            if (action is com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction) {
                var pctText by remember(action.percentage) { mutableStateOf(action.percentage.toString()) }
                val focusManager = LocalFocusManager.current
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.by), modifier = Modifier.padding(end = 8.dp))
                    OutlinedTextField(
                        value = pctText,
                        onValueChange = { 
                            pctText = it 
                            val parsed = it.toIntOrNull()
                            if (parsed != null) {
                                onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction(action.increase, parsed.coerceIn(1, 100)))
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state ->
                                if (!state.isFocused) {
                                    if (pctText.isEmpty()) {
                                        pctText = "10"
                                        onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.ModifyVolumeAction(action.increase, 10))
                                    }
                                }
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        suffix = { Text(stringResource(R.string.unknown_string)) }
                    )
                }
            }
            
            if (action is com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction) {
                var ttsText by remember(action.text) { mutableStateOf(action.text) }
                val focusManager = LocalFocusManager.current
                
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = ttsText,
                        onValueChange = { 
                            ttsText = it 
                            onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction(it, action.asAnnouncement))
                        },
                        placeholder = { Text(stringResource(R.string.enter_text_to_speak)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(stringResource(R.string.as_announcement_boost_volume), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = action.asAnnouncement,
                            onCheckedChange = { 
                                onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.TtsAction(action.text, it))
                            }
                        )
                    }
                }
            }
            
            if (showAppSelectionDialog) {
                AppSelectionDialog(
                    onDismissRequest = { showAppSelectionDialog = false },
                    onAppSelected = { pkg, app ->
                        onActionChange(com.benegedeniz.budsdynamiceq.data.model.FlowAction.AppAction(pkg, app))
                        showAppSelectionDialog = false
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.you_can_string_together_multiple_actions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = name.isNotBlank() && !(action is com.benegedeniz.budsdynamiceq.data.model.FlowAction.AppAction && action.packageName.isEmpty()),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(stringResource(R.string.next_step), fontSize = 16.sp)
        }
    }
}

@Composable
fun RecordingStep(viewModel: HeadShakeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.recordingState
    val index by viewModel.currentRecordingIndex.collectAsState()
    val countdown by viewModel.countdownSeconds.collectAsState()
    val liveSample by viewModel.spatialDataFlow.collectAsState(initial = null)
    val consistencyWarning by viewModel.consistencyWarning.collectAsState()
    


    val editingGesture by viewModel.editingGesture.collectAsState()
    val isRecordingNoise by viewModel.isRecordingNoise.collectAsState()
    val targetCount = if (isRecordingNoise) 1 else if (editingGesture != null) 3 else 5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        Text(
            text = if (isRecordingNoise) stringResource(R.string.recording_step2_recording) 
                   else if (editingGesture != null) stringResource(R.string.recording_training_variation, index + 1) 
                   else stringResource(R.string.recording_step2_recording_steps, index + 1),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            
            // Recording dots
            for (i in 0 until targetCount) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if ((i < index || (i == index && state == RecordingState.SAMPLE_DONE))) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
                if (i < targetCount - 1) Spacer(modifier = Modifier.width(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            if (state == RecordingState.RECORDING) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFFFB300), // Amber
                    trackColor = Color.LightGray.copy(alpha = 0.2f),
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round
                )
            } else if (state == RecordingState.CONTINUOUS_RECORDING) {
                val rawProgress by viewModel.recordingProgress.collectAsState()
                val smoothProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = rawProgress,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, easing = androidx.compose.animation.core.LinearEasing),
                    label = "SmoothRecordingProgress"
                )
                CircularProgressIndicator(
                    progress = { smoothProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFFFB300), // Amber
                    trackColor = Color.LightGray.copy(alpha = 0.2f),
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = Color.LightGray.copy(alpha = 0.2f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Head3DCanvas(
                sample = liveSample,
                size = 180.dp,
                resetTrigger = state
            )

            // Center content overlay
            when (state) {
                RecordingState.READY_FOR_SAMPLE -> {}
                RecordingState.COUNTDOWN -> {
                    Text(
                        text = countdown.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                RecordingState.RECORDING -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.perform_gesture),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300)
                        )
                    }
                }
                RecordingState.CONTINUOUS_RECORDING -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.keep_moving),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300)
                        )
                    }
                }
                RecordingState.SAMPLE_DONE -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {}
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        
        if (state == RecordingState.READY_FOR_SAMPLE || state == RecordingState.SAMPLE_DONE || state == RecordingState.RECORDING || state == RecordingState.CONTINUOUS_RECORDING) {
            val isRecordingNoise by viewModel.isRecordingNoise.collectAsState()
            Text(
                text = when (state) {
                    RecordingState.RECORDING, RecordingState.CONTINUOUS_RECORDING -> stringResource(R.string.recording_keep_going)
                    RecordingState.SAMPLE_DONE -> if (index == targetCount - 1) stringResource(R.string.recording_awesome_finish) else stringResource(R.string.recording_great_next)
                    else -> stringResource(R.string.recording_press_start)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else if (state == RecordingState.COUNTDOWN) {
            Text(
                text = stringResource(R.string.get_ready),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        
        if (consistencyWarning != null && state == RecordingState.SAMPLE_DONE) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text(
                    text = consistencyWarning!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (state == RecordingState.READY_FOR_SAMPLE) {
            val isRecordingNoise by viewModel.isRecordingNoise.collectAsState()
            Button(
                onClick = { 
                    if (isRecordingNoise) viewModel.startContinuousRecording()
                    else viewModel.startRecording()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(if (isRecordingNoise) stringResource(R.string.start_continuous_recording) else stringResource(R.string.start_recording), fontSize = 16.sp)
            }
        } else if (state == RecordingState.CONTINUOUS_RECORDING) {
            Button(
                onClick = { viewModel.stopContinuousRecording() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.stop_save), fontSize = 16.sp)
            }
        } else if (state == RecordingState.SAMPLE_DONE) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { viewModel.redoLastRecording() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(stringResource(R.string.redo))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { viewModel.nextSample() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(if (index == targetCount - 1) stringResource(R.string.finish_text) else stringResource(R.string.next_text), fontSize = 16.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun TestAndSaveStep(
    viewModel: HeadShakeViewModel,
    name: String,
    action: com.benegedeniz.budsdynamiceq.data.model.FlowAction
) {
    val testResult by viewModel.testResult.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.recordingState

    val liveSample by viewModel.spatialDataFlow.collectAsState(initial = null)

    val editingGesture by viewModel.editingGesture.collectAsState()
    val isRecordingNoise by viewModel.isRecordingNoise.collectAsState()
    val targetCount = if (isRecordingNoise) 1 else if (editingGesture != null) 3 else 5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (editingGesture != null) stringResource(R.string.recording_step3_integrate) else stringResource(R.string.recording_step3_save),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (state == RecordingState.TESTING) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Head3DCanvas(
                    sample = liveSample,
                    size = 170.dp,
                    resetTrigger = state
                )

                if (testResult != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "✅ " + stringResource(testResult!!),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = { viewModel.stopTesting() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(stringResource(R.string.stop_testing))
            }
        } else {
            Icon(
                Icons.Default.DoneAll,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.recording_samples_recorded, targetCount, targetCount),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { viewModel.testGesture() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(stringResource(R.string.test_gesture))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { 
                    if (editingGesture != null) viewModel.cancelRecording()
                    else viewModel.redoAll() 
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                enabled = state != RecordingState.TESTING
            ) {
                Text(
                    text = if (editingGesture != null) stringResource(R.string.discard) else stringResource(R.string.redo_all),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = { viewModel.saveGesture(name, action) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                enabled = state != RecordingState.TESTING
            ) {
                Text(
                    text = if (editingGesture != null) stringResource(R.string.integrate) else stringResource(R.string.save_gesture_btn),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
