package com.benegedeniz.budsdynamiceq.ui.headshake

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.benegedeniz.budsdynamiceq.data.model.GestureAction
import com.benegedeniz.budsdynamiceq.data.model.getDisplayName
import com.benegedeniz.budsdynamiceq.data.model.getDisplayNameString
import com.benegedeniz.budsdynamiceq.ui.components.SearchBarInput
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSelectionDialog(
    hasExistingActions: Boolean = false,
    forbiddenActions: List<GestureAction> = emptyList(),
    allowFitTestWithOtherActions: Boolean = false,
    onDismissRequest: () -> Unit,
    onActionSelected: (GestureAction) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val effectiveModel = com.benegedeniz.budsdynamiceq.di.ServiceLocator.provideBudsController(context).effectiveModel.collectAsState().value
    val effectiveForbidden = remember(forbiddenActions, effectiveModel) {
        if (!effectiveModel.supportsFitTest) forbiddenActions + GestureAction.FIT_TEST else forbiddenActions
    }

    val gridLayout = remember(effectiveForbidden) {
        listOf(
            context.getString(R.string.action_group_media) to listOf(
                listOf(GestureAction.PLAY_PAUSE),
                listOf(GestureAction.PLAY, GestureAction.PAUSE),
                listOf(GestureAction.SET_VOLUME),
                listOf(GestureAction.MODIFY_VOLUME_INCREASE, GestureAction.MODIFY_VOLUME_DECREASE),
                listOf(GestureAction.PREVIOUS_TRACK, GestureAction.NEXT_TRACK),
                listOf(GestureAction.ANNOUNCE_TRACK)
            ),
            context.getString(R.string.action_group_noise_controls) to buildList {
                add(listOf(GestureAction.NC_TOGGLE))
                if (effectiveModel.supportsTransparencyNC) {
                    add(listOf(GestureAction.NC_ACTIVE, GestureAction.NC_TRANSPARENT))
                } else {
                    add(listOf(GestureAction.NC_ACTIVE))
                }
                if (effectiveModel.supportsAdaptiveNC) {
                    add(listOf(GestureAction.NC_ADAPTIVE))
                }
                add(listOf(GestureAction.NC_OFF))
            },
            context.getString(R.string.action_group_system) to listOf(
                listOf(GestureAction.ACCEPT_CALL, GestureAction.REJECT_CALL),
                listOf(GestureAction.VOICE_ASSISTANT),
                listOf(GestureAction.LAUNCH_APP),
                listOf(GestureAction.READ_NOTIFICATIONS),
                listOf(GestureAction.SPEAK_TEXT)
            ),
            context.getString(R.string.action_group_other) to listOf(
                listOf(GestureAction.FIT_TEST),
                listOf(GestureAction.NO_ACTION)
            )
        ).map { group ->
            group.first to group.second.map { row ->
                row.filter { it !in effectiveForbidden }
            }.filter { it.isNotEmpty() }
        }.filter { it.second.isNotEmpty() }
    }

    val filteredActions = remember(searchQuery, effectiveForbidden) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            GestureAction.entries.filter { 
                it !in effectiveForbidden && (it.getDisplayNameString(context).contains(searchQuery, ignoreCase = true) || 
                context.getString(it.groupRes).contains(searchQuery, ignoreCase = true))
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.select_action)) },
                    navigationIcon = {
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Search Bar
                SearchBarInput(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = stringResource(R.string.search_actions),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp)
                ) {
                    if (searchQuery.isBlank()) {
                        gridLayout.forEach { (group, rows) ->
                            item {
                                Text(
                                    text = group,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = if (group == gridLayout.first().first) 0.dp else 24.dp)
                                )
                            }
                            items(rows) { rowActions ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ActionItem(
                                        action = rowActions[0], 
                                        hasExistingActions = hasExistingActions,
                                        allowFitTestWithOtherActions = allowFitTestWithOtherActions,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        onActionSelected(rowActions[0])
                                    }
                                    if (rowActions.size > 1) {
                                        ActionItem(
                                            action = rowActions[1], 
                                            hasExistingActions = hasExistingActions,
                                            allowFitTestWithOtherActions = allowFitTestWithOtherActions,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            onActionSelected(rowActions[1])
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (filteredActions.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.no_actions_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(filteredActions) { action ->
                                ActionItem(
                                    action = action, 
                                    hasExistingActions = hasExistingActions,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    onActionSelected(action)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionItem(
    action: GestureAction, 
    hasExistingActions: Boolean = false, 
    allowFitTestWithOtherActions: Boolean = false,
    modifier: Modifier = Modifier, 
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDisabledFitTest = action == GestureAction.FIT_TEST && hasExistingActions && !allowFitTestWithOtherActions
    
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (isDisabledFitTest) {
                    android.widget.Toast.makeText(context, context.getString(R.string.fit_test_only_action), android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    onClick()
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDisabledFitTest) 0.2f else 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when(action) {
                GestureAction.PLAY_PAUSE -> Icons.Default.PlayArrow
                GestureAction.PLAY -> Icons.Default.PlayArrow
                GestureAction.PAUSE -> Icons.Default.Pause
                GestureAction.SET_VOLUME -> Icons.AutoMirrored.Filled.VolumeUp
                GestureAction.MODIFY_VOLUME_INCREASE -> Icons.AutoMirrored.Filled.VolumeUp
                GestureAction.MODIFY_VOLUME_DECREASE -> Icons.AutoMirrored.Filled.VolumeDown
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
                GestureAction.LAUNCH_APP -> Icons.Default.Apps
                GestureAction.SPEAK_TEXT -> Icons.Default.RecordVoiceOver
                GestureAction.FIT_TEST -> Icons.Default.CheckCircle
                GestureAction.NO_ACTION -> Icons.Default.Cancel
            }
            val alpha = if (isDisabledFitTest) 0.3f else 1f
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.getDisplayName(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
