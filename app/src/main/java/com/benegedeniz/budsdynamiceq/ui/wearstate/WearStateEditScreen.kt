package com.benegedeniz.budsdynamiceq.ui.wearstate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.reorderable
import com.benegedeniz.budsdynamiceq.data.model.FlowAction
import com.benegedeniz.budsdynamiceq.data.model.GestureAction
import com.benegedeniz.budsdynamiceq.data.model.WearStateAction
import com.benegedeniz.budsdynamiceq.data.model.WearStateTrigger
import com.benegedeniz.budsdynamiceq.ui.headshake.FlowActionItem
import com.benegedeniz.budsdynamiceq.ui.headshake.ActionSelectionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearStateEditScreen(
    initialAction: WearStateAction,
    onSave: (WearStateAction) -> Unit,
    onDismiss: () -> Unit
) {
    data class ActionWrapper(val id: String = java.util.UUID.randomUUID().toString(), var action: FlowAction)
    var actionWrappers by remember { 
        mutableStateOf(initialAction.actions.map { ActionWrapper(action = it) }) 
    }

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
                            Text("Cancel")
                        }
                        
                        VerticalDivider(
                            modifier = Modifier.height(32.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        
                        TextButton(
                            onClick = { 
                                val newAction = initialAction.copy(
                                    actions = actionWrappers.map { it.action }
                                )
                                onSave(newAction) 
                            },
                            modifier = Modifier.weight(1f),
                            enabled = actionWrappers.isNotEmpty()
                        ) {
                            Text("Save Changes")
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
                    text = "Edit Flow: ${initialAction.name}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Action Flow",
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
                                allowRespectManualIntent = true,
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
                        var showSelectionDialog by remember { mutableStateOf(false) }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Button(
                                onClick = { showSelectionDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Action")
                            }
                            OutlinedButton(
                                onClick = {
                                    actionWrappers = actionWrappers + ActionWrapper(action = FlowAction.DelayAction(500L))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delay")
                            }
                        }
                        
                        if (showSelectionDialog) {
                            val forbidden = if (initialAction.trigger == WearStateTrigger.EARBUD_REMOVED) listOf(GestureAction.FIT_TEST) else emptyList()
                            ActionSelectionDialog(
                                hasExistingActions = actionWrappers.isNotEmpty(),
                                forbiddenActions = forbidden,
                                allowFitTestWithOtherActions = true,
                                onDismissRequest = { showSelectionDialog = false },
                                onActionSelected = { gestureAction ->
                                    val newFlowAction = when (gestureAction) {
                                        GestureAction.SET_VOLUME -> FlowAction.VolumeAction()
                                        GestureAction.MODIFY_VOLUME_INCREASE -> FlowAction.ModifyVolumeAction(increase = true)
                                        GestureAction.MODIFY_VOLUME_DECREASE -> FlowAction.ModifyVolumeAction(increase = false)
                                        GestureAction.LAUNCH_APP -> FlowAction.AppAction()
                                        GestureAction.SPEAK_TEXT -> FlowAction.TtsAction()
                                        else -> FlowAction.SystemAction(gestureAction)
                                    }
                                    actionWrappers = actionWrappers + ActionWrapper(action = newFlowAction)
                                    showSelectionDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
