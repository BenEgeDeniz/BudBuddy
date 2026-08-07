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
import com.benegedeniz.budsdynamiceq.data.model.getDisplayName
import com.benegedeniz.budsdynamiceq.ui.components.PageHeader
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
import com.benegedeniz.budsdynamiceq.ui.theme.StatusActiveGreen
import com.benegedeniz.budsdynamiceq.ui.theme.StatusErrorRed
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.R

@Composable
fun WearStateScreen(
    viewModel: WearStateViewModel = viewModel(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.activity.compose.BackHandler { onBack() }
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val actions = uiState.actions
    val isConnected = uiState.isConnected

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
                title = stringResource(R.string.wear_actions),
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
                    val displayName = when (action.id) {
                        "default_removed" -> stringResource(R.string.trigger_earbud_removed)
                        "default_wearing" -> stringResource(R.string.trigger_both_worn)
                        else -> action.name.ifBlank { stringResource(R.string.wearstate_unnamed_action) }
                    }
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(action.trigger.displayNameRes),
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
                    contentDescription = stringResource(R.string.edit_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun FlowAction.getDisplayName(): String {
    return when (this) {
        is FlowAction.SystemAction -> this.action.getDisplayName()
        is FlowAction.DelayAction -> stringResource(R.string.action_delay_ms, this.ms)
        is FlowAction.AppAction -> stringResource(R.string.action_open_app, this.appName)
        is FlowAction.VolumeAction -> stringResource(R.string.action_set_volume_pct, this.percentage)
        is FlowAction.ModifyVolumeAction -> if (this.increase) stringResource(R.string.action_increase_volume_pct, this.percentage) else stringResource(R.string.action_decrease_volume_pct, this.percentage)
        is FlowAction.TtsAction -> stringResource(R.string.action_say_text, this.text)
    }
}
