package com.benegedeniz.budsdynamiceq.ui.wearstate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benegedeniz.budsdynamiceq.data.model.WearStateAction
import com.benegedeniz.budsdynamiceq.data.model.FlowAction
import com.benegedeniz.budsdynamiceq.data.model.GestureAction
import com.benegedeniz.budsdynamiceq.ui.components.PageHeader
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
import com.benegedeniz.budsdynamiceq.ui.theme.StatusActiveGreen
import com.benegedeniz.budsdynamiceq.ui.theme.StatusErrorRed

@Composable
fun WearStateScreen(
    viewModel: WearStateViewModel = viewModel(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val actions by viewModel.wearStateActions.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    var editingAction by remember { mutableStateOf<WearStateAction?>(null) }
    var showNewActionDialog by remember { mutableStateOf(false) }



    if (editingAction != null) {
        WearStateEditScreen(
            initialAction = editingAction!!,
            onSave = { action ->
                viewModel.saveAction(action)
                editingAction = null
            },
            onDismiss = { editingAction = null }
        )
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            PageHeader(
                title = "Wear Actions",
                isScrolled = scrollState.value > 10,
                actionIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                        modifier = Modifier.bounceClick()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, top = 24.dp)
            ) {
                items(actions, key = { it.id }) { action ->
                    WearStateActionCard(
                        action = action,
                        onEdit = { editingAction = action },
                        onToggle = { enabled -> viewModel.toggleAction(action, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
fun WearStateActionCard(
    action: WearStateAction,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit() }
                    .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action.name.ifBlank { "Unnamed Action" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Trigger: ${action.trigger.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = action.enabled,
                    onCheckedChange = { checked ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle(checked)
                    },
                    modifier = Modifier.scale(0.9f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Edit Action",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun FlowAction.getDisplayName(): String {
    return when (this) {
        is FlowAction.SystemAction -> this.action.displayName
        is FlowAction.DelayAction -> "Delay ${this.ms}ms"
        is FlowAction.AppAction -> "Open ${this.appName}"
        is FlowAction.VolumeAction -> "Set Volume ${this.percentage}%"
        is FlowAction.ModifyVolumeAction -> if (this.increase) "Increase Volume ${this.percentage}%" else "Decrease Volume ${this.percentage}%"
        is FlowAction.TtsAction -> "Say: ${this.text}"
    }
}
