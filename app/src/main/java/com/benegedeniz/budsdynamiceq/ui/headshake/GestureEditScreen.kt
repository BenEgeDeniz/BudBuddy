package com.benegedeniz.budsdynamiceq.ui.headshake

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.reorderable
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.model.getDisplayName
import com.benegedeniz.budsdynamiceq.data.model.FlowAction
import com.benegedeniz.budsdynamiceq.data.model.GestureAction
import com.benegedeniz.budsdynamiceq.data.model.HeadGesture
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureEditScreen(
    gesture: HeadGesture,
    onSave: (name: String, actions: List<FlowAction>, playChime: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(gesture.name) }
    var playChime by remember { mutableStateOf(gesture.playChime) }
    
    data class ActionWrapper(val id: String = java.util.UUID.randomUUID().toString(), var action: FlowAction)
    var actionWrappers by remember { mutableStateOf(gesture.actions.map { ActionWrapper(action = it) }) }

    val state = rememberReorderableLazyListState(
        onMove = { from, to ->
            actionWrappers = actionWrappers.toMutableList().apply {
                val fromIndex = indexOfFirst { it.id == from.key }
                val toIndex = indexOfFirst { it.id == to.key }
                if (fromIndex != -1 && toIndex != -1) {
                    val item = removeAt(fromIndex)
                    add(toIndex.coerceIn(0, size), item)
                }
            }
        },
        canDragOver = { draggedOver, _ -> draggedOver.key is String }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val pillWidth = configuration.screenWidthDp.dp - 96.dp
                    
                    Row(
                        modifier = Modifier
                            .width(pillWidth)
                            .height(64.dp)
                            .shadow(4.dp, androidx.compose.foundation.shape.CircleShape)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.cancel), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        
                        VerticalDivider(
                            modifier = Modifier.height(32.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        
                        TextButton(
                            onClick = { onSave(name, actionWrappers.map { it.action }, playChime) },
                            modifier = Modifier.weight(1f),
                            enabled = name.isNotBlank() && actionWrappers.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.save_changes), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.edit_gesture),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.gesture_name)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.play_tone_on_recognition),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                    Switch(
                        checked = playChime,
                        onCheckedChange = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            playChime = it
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.action_flow),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    state = state.listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .reorderable(state),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(actionWrappers, key = { it.id }) { wrapper ->
                        ReorderableItem(state, key = wrapper.id) { isDragging ->
                            val elevation = if (isDragging) 8.dp else 0.dp
                            FlowActionItem(
                                action = wrapper.action,
                                hasExistingActions = actionWrappers.size > 1,
                                modifier = Modifier
                                    .shadow(elevation, RoundedCornerShape(12.dp)),
                                dragModifier = Modifier.detectReorder(state),
                                onRemove = {
                                    val mut = actionWrappers.toMutableList()
                                    mut.remove(wrapper)
                                    actionWrappers = mut
                                },
                                onUpdate = { newAction ->
                                    val mut = actionWrappers.toMutableList()
                                    val idx = mut.indexOf(wrapper)
                                    if (idx != -1) {
                                        mut[idx] = wrapper.copy(action = newAction)
                                        actionWrappers = mut
                                    }
                                }
                            )
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        val hasFitTest = actionWrappers.any { 
                            val act = it.action
                            act is FlowAction.SystemAction && act.action == GestureAction.FIT_TEST 
                        }
                        
                        if (hasFitTest) {
                            Text(
                                text = stringResource(R.string.fit_test_is_an_exclusive_action_no_other),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    actionWrappers = actionWrappers + ActionWrapper(action = FlowAction.SystemAction(GestureAction.PLAY_PAUSE))
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !hasFitTest
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.action))
                            }
                            OutlinedButton(
                                onClick = {
                                    actionWrappers = actionWrappers + ActionWrapper(action = FlowAction.DelayAction(500L))
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !hasFitTest
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.delay))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowActionItem(
    action: FlowAction,
    hasExistingActions: Boolean,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    allowRespectManualIntent: Boolean = false,
    onRemove: () -> Unit,
    onUpdate: (FlowAction) -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.drag_to_reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragModifier.padding(end = 12.dp)
            )
            when (action) {
                is FlowAction.SystemAction -> {
                    var showActionDialog by remember { mutableStateOf(false) }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = action.action.getDisplayName(),
                                onValueChange = {},
                                readOnly = true,
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
                        }

                        if (allowRespectManualIntent && action.action in listOf(GestureAction.PLAY, GestureAction.PAUSE, GestureAction.PLAY_PAUSE)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.force_action),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.enabling_this_will_forcefully_do_the_act),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = !action.respectManualIntent,
                                    onCheckedChange = { onUpdate(action.copy(respectManualIntent = !it)) }
                                )
                            }
                        }
                    }

                    if (showActionDialog) {
                        ActionSelectionDialog(
                            hasExistingActions = hasExistingActions,
                            onDismissRequest = { showActionDialog = false },
                            onActionSelected = { a ->
                                if (a == GestureAction.LAUNCH_APP) {
                                    onUpdate(FlowAction.AppAction())
                                } else if (a == GestureAction.SET_VOLUME) {
                                    onUpdate(FlowAction.VolumeAction())
                                } else if (a == GestureAction.MODIFY_VOLUME_INCREASE) {
                                    onUpdate(FlowAction.ModifyVolumeAction(increase = true))
                                } else if (a == GestureAction.MODIFY_VOLUME_DECREASE) {
                                    onUpdate(FlowAction.ModifyVolumeAction(increase = false))
                                } else if (a == GestureAction.SPEAK_TEXT) {
                                    onUpdate(FlowAction.TtsAction())
                                } else {
                                    onUpdate(FlowAction.SystemAction(a))
                                }
                                showActionDialog = false
                            }
                        )
                    }
                }
                is FlowAction.AppAction -> {
                    var showAppSelectionDialog by remember { mutableStateOf(false) }
                    var showActionDialog by remember { mutableStateOf(false) }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = stringResource(R.string.action_start_application),
                            onValueChange = {},
                            readOnly = true,
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
                        
                        Button(
                            onClick = { showAppSelectionDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(if (action.packageName.isEmpty()) stringResource(R.string.select_app_short) else stringResource(R.string.change_app_format, action.appName))
                        }
                    }

                    if (showActionDialog) {
                        ActionSelectionDialog(
                            hasExistingActions = hasExistingActions,
                            onDismissRequest = { showActionDialog = false },
                            onActionSelected = { a ->
                                if (a == GestureAction.LAUNCH_APP) {
                                    // already AppAction, just show app selection
                                    showAppSelectionDialog = true
                                } else if (a == GestureAction.SET_VOLUME) {
                                    onUpdate(FlowAction.VolumeAction())
                                } else if (a == GestureAction.SPEAK_TEXT) {
                                    onUpdate(FlowAction.TtsAction())
                                } else {
                                    onUpdate(FlowAction.SystemAction(a))
                                }
                                showActionDialog = false
                            }
                        )
                    }

                    if (showAppSelectionDialog) {
                        AppSelectionDialog(
                            onDismissRequest = { showAppSelectionDialog = false },
                            onAppSelected = { pkg, app ->
                                onUpdate(FlowAction.AppAction(pkg, app))
                                showAppSelectionDialog = false
                            }
                        )
                    }
                }
                is FlowAction.DelayAction -> {
                    var delayText by remember(action.ms) { mutableStateOf(action.ms.toString()) }
                    val focusManager = LocalFocusManager.current
                    Text(stringResource(R.string.delay_for), modifier = Modifier.padding(end = 8.dp))
                    OutlinedTextField(
                        value = delayText,
                        onValueChange = { 
                            delayText = it
                            val parsed = it.toLongOrNull()
                            if (parsed != null) {
                                onUpdate(FlowAction.DelayAction(if (parsed < 100L) 100L else parsed))
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { state ->
                                if (!state.isFocused) {
                                    if (delayText.isEmpty()) {
                                        delayText = "100"
                                        onUpdate(FlowAction.DelayAction(100L))
                                    }
                                }
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        suffix = { Text(stringResource(R.string.ms)) }
                    )
                }
                is FlowAction.VolumeAction -> {
                    var showActionDialog by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = "Set Volume",
                            onValueChange = {},
                            readOnly = true,
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(stringResource(R.string.percentage_format, action.percentage.toString()), modifier = Modifier.width(48.dp))
                            Slider(
                                value = action.percentage / 100f,
                                onValueChange = { onUpdate(FlowAction.VolumeAction((it * 100).toInt())) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (showActionDialog) {
                        ActionSelectionDialog(
                            hasExistingActions = hasExistingActions,
                            onDismissRequest = { showActionDialog = false },
                            onActionSelected = { a ->
                                if (a == GestureAction.LAUNCH_APP) {
                                    onUpdate(FlowAction.AppAction())
                                } else if (a == GestureAction.SET_VOLUME) {
                                    onUpdate(FlowAction.VolumeAction())
                                } else if (a == GestureAction.SPEAK_TEXT) {
                                    onUpdate(FlowAction.TtsAction())
                                } else {
                                    onUpdate(FlowAction.SystemAction(a))
                                }
                                showActionDialog = false
                            }
                        )
                    }
                }
                is FlowAction.ModifyVolumeAction -> {
                    var showActionDialog by remember { mutableStateOf(false) }
                    var pctText by remember(action.percentage) { mutableStateOf(action.percentage.toString()) }
                    val focusManager = LocalFocusManager.current
                    
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = if (action.increase) stringResource(R.string.action_increase_volume) else stringResource(R.string.action_decrease_volume),
                            onValueChange = {},
                            readOnly = true,
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(stringResource(R.string.by), modifier = Modifier.padding(end = 8.dp))
                            OutlinedTextField(
                                value = pctText,
                                onValueChange = { 
                                    pctText = it 
                                    val parsed = it.toIntOrNull()
                                    if (parsed != null) {
                                        onUpdate(FlowAction.ModifyVolumeAction(action.increase, parsed.coerceIn(1, 100)))
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { state ->
                                        if (!state.isFocused) {
                                            if (pctText.isEmpty()) {
                                                pctText = "10"
                                                onUpdate(FlowAction.ModifyVolumeAction(action.increase, 10))
                                            }
                                        }
                                    },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                suffix = { Text(stringResource(R.string.unknown_string)) }
                            )
                        }
                    }
                    if (showActionDialog) {
                        ActionSelectionDialog(
                            hasExistingActions = hasExistingActions,
                            onDismissRequest = { showActionDialog = false },
                            onActionSelected = { a ->
                                if (a == GestureAction.LAUNCH_APP) {
                                    onUpdate(FlowAction.AppAction())
                                } else if (a == GestureAction.SET_VOLUME) {
                                    onUpdate(FlowAction.VolumeAction())
                                } else if (a == GestureAction.MODIFY_VOLUME_INCREASE) {
                                    onUpdate(FlowAction.ModifyVolumeAction(increase = true, percentage = action.percentage))
                                } else if (a == GestureAction.MODIFY_VOLUME_DECREASE) {
                                    onUpdate(FlowAction.ModifyVolumeAction(increase = false, percentage = action.percentage))
                                } else if (a == GestureAction.SPEAK_TEXT) {
                                    onUpdate(FlowAction.TtsAction())
                                } else {
                                    onUpdate(FlowAction.SystemAction(a))
                                }
                                showActionDialog = false
                            }
                        )
                    }
                }
                is FlowAction.TtsAction -> {
                    var showActionDialog by remember { mutableStateOf(false) }
                    var ttsText by remember(action.text) { mutableStateOf(action.text) }
                    val focusManager = LocalFocusManager.current
                    
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = stringResource(R.string.action_speak_out_loud),
                            onValueChange = {},
                            readOnly = true,
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
                        OutlinedTextField(
                            value = ttsText,
                            onValueChange = { 
                                ttsText = it 
                                onUpdate(FlowAction.TtsAction(it, action.asAnnouncement))
                            },
                            placeholder = { Text(stringResource(R.string.enter_text_to_speak)) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(stringResource(R.string.as_announcement_boost_volume), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = action.asAnnouncement,
                                onCheckedChange = { 
                                    onUpdate(FlowAction.TtsAction(action.text, it))
                                }
                            )
                        }
                    }
                    if (showActionDialog) {
                        ActionSelectionDialog(
                            hasExistingActions = hasExistingActions,
                            onDismissRequest = { showActionDialog = false },
                            onActionSelected = { a ->
                                if (a == GestureAction.LAUNCH_APP) {
                                    onUpdate(FlowAction.AppAction())
                                } else if (a == GestureAction.SET_VOLUME) {
                                    onUpdate(FlowAction.VolumeAction())
                                } else if (a == GestureAction.MODIFY_VOLUME_INCREASE) {
                                    onUpdate(FlowAction.ModifyVolumeAction(increase = true, percentage = 10))
                                } else if (a == GestureAction.MODIFY_VOLUME_DECREASE) {
                                    onUpdate(FlowAction.ModifyVolumeAction(increase = false, percentage = 10))
                                } else if (a == GestureAction.SPEAK_TEXT) {
                                    onUpdate(FlowAction.TtsAction(action.text, action.asAnnouncement))
                                } else {
                                    onUpdate(FlowAction.SystemAction(a))
                                }
                                showActionDialog = false
                            }
                        )
                    }
                }
            }
            
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
