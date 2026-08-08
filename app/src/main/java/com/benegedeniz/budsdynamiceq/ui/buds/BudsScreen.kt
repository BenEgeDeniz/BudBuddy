package com.benegedeniz.budsdynamiceq.ui.buds

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.core.app.NotificationManagerCompat
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import androidx.compose.ui.graphics.graphicsLayer
import com.benegedeniz.budsdynamiceq.ui.components.SearchBarInput
import com.benegedeniz.budsdynamiceq.ui.components.PageHeader
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
import com.benegedeniz.budsdynamiceq.ui.buds.components.*
import com.benegedeniz.budsdynamiceq.ui.rules.RulesViewModel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.ui.buds.BudsViewModel
import com.benegedeniz.budsdynamiceq.ui.buds.components.AppSearchOverlay
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.draw.blur

import com.benegedeniz.budsdynamiceq.ui.headshake.HeadShakeViewModel

@Composable
fun BudsScreen(
    viewModel: BudsViewModel,
    rulesViewModel: RulesViewModel,
    headShakeViewModel: HeadShakeViewModel,
    onFitTestClick: () -> Unit = {},
    onWearStateClick: () -> Unit = {},
    onSoundBalanceTestClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onFindMyBudsClick: () -> Unit = {},
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val currentMetadata = uiState.currentMetadata
    val isConnected = uiState.isConnected
    val isConnecting = uiState.isConnecting
    val lastMatchedRule = uiState.lastMatchedRule
    val manualPreset = uiState.manualPreset
    val manualNoiseControl = uiState.manualNoiseControl
    val activeNoiseControl = uiState.activeNoiseControl
    val conversationDetectionEnabled = uiState.conversationDetectionEnabled
    val oneEarbudNoiseControlEnabled = uiState.oneEarbudNoiseControlEnabled
    val useAmbientSoundDuringCalls = uiState.useAmbientSoundDuringCalls
    val inEarDetectionForCalls = uiState.inEarDetectionForCalls
    val doubleTapEdgeEnabled = uiState.doubleTapEdgeEnabled
    val stereoBalance = uiState.stereoBalance
    val pairedDevices = uiState.pairedDevices
    val savedDeviceMac = uiState.savedDeviceMac
    
    val context = LocalContext.current
    val prefsLocal = remember(context) { context.getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE) }
    val pauseMediaOnConversation by rulesViewModel.pauseMediaOnConversationEnabled.collectAsState()
    
    var isMoreSettingsExpanded by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        viewModel.setHomePageVisible(true)
        onDispose {
            viewModel.setHomePageVisible(false)
        }
    }
    
    val batteryL = uiState.batteryL
    val batteryR = uiState.batteryR
    val batteryCase = uiState.batteryCase
    val placementL = uiState.placementL
    val placementR = uiState.placementR

    val chargingL = uiState.chargingL
    val chargingR = uiState.chargingR
    val chargingCase = uiState.chargingCase
    val temperatureL = uiState.temperatureL
    val temperatureR = uiState.temperatureR

    val effectiveModel = uiState.effectiveModel
    val modelOverride = uiState.modelOverride
    val connectedModel = uiState.connectedModel


    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    var showDeviceDialog by remember { mutableStateOf(false) }
    var showConnectingDialog by remember { mutableStateOf(false) }
    var showDeviceMenu by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }

    val isModelUnknown = isConnected && effectiveModel == com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.UNKNOWN

    LaunchedEffect(isModelUnknown) {
        if (isModelUnknown) {
            kotlinx.coroutines.delay(3000)
            showModelDialog = true
        } else {
            showModelDialog = false
        }
    }

    val listState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20 } }

    var overscrollAmount by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y > 0 && !listState.canScrollBackward) {
                        // Consume downward drag at the top so it feels like a raw touch gesture without scroll physics resistance
                        overscrollAmount += available.y
                        return Offset(0f, available.y)
                    } else if (overscrollAmount > 0 && available.y < 0) {
                        // Consume upward drag to reduce accumulated amount
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

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 140.dp, bottom = 120.dp)
        ) {
            // Pull down hint
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

            // Status Section
            item {
                val isNotificationGranted = remember(context) {
                    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                }

                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Icon
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) 
                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Text: Connected/Disconnected & Playing
                            val savedDeviceName = remember(savedDeviceMac, pairedDevices) {
                                pairedDevices.find { it.address == savedDeviceMac }?.let {
                                    try { it.name } catch (e: SecurityException) { null }
                                } ?: savedDeviceMac
                            }

                            Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isConnected) stringResource(R.string.buds_connected) 
                                               else if (isConnecting) (if (savedDeviceName != null) stringResource(R.string.buds_connecting_to, savedDeviceName) else stringResource(R.string.buds_connecting)) 
                                               else stringResource(R.string.buds_disconnected),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isConnected) MaterialTheme.colorScheme.onSurface else if (isConnecting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = if (isConnecting) Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = androidx.compose.foundation.MarqueeAnimationMode.Immediately) else Modifier
                                    )
                                    if (isConnecting) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                
                                if (isConnected && currentMetadata != null && currentMetadata!!.displayString.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.buds_playing, currentMetadata!!.displayString),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = androidx.compose.foundation.MarqueeAnimationMode.Immediately)
                                    )
                                } else if (!isConnected && !isConnecting && savedDeviceName != null) {
                                    Text(
                                        text = stringResource(R.string.buds_saved, savedDeviceName),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = androidx.compose.foundation.MarqueeAnimationMode.Immediately)
                                    )
                                }
                            }
                            
                            // Actions (Connect/Disconnect/Forget)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isConnected) {
                                    TextButton(
                                        onClick = { viewModel.disconnect(forget = false) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                                    ) {
                                        Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                                    }
                                    Box {
                                        IconButton(
                                            onClick = { showDeviceMenu = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = stringResource(R.string.device_options),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showDeviceMenu,
                                            onDismissRequest = { showDeviceMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.select_another_device)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Bluetooth,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showDeviceMenu = false
                                                    viewModel.refreshPairedDevices()
                                                    showDeviceDialog = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text(stringResource(R.string.change_model))
                                                        Text(
                                                            text = if (modelOverride != null) stringResource(R.string.buds_override, stringResource(effectiveModel.displayNameRes)) else stringResource(R.string.buds_auto, stringResource(effectiveModel.displayNameRes)),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Tune,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    showDeviceMenu = false
                                                    showModelDialog = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.forget_device), color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                onClick = {
                                                    showDeviceMenu = false
                                                    viewModel.disconnect(forget = true)
                                                }
                                            )
                                        }
                                    }
                                } else if (isConnecting) {
                                    TextButton(
                                        onClick = {
                                            viewModel.disconnect(forget = false)
                                            showConnectingDialog = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (savedDeviceMac == null) {
                                                viewModel.refreshPairedDevices()
                                                showDeviceDialog = true
                                            } else {
                                                val savedDevice = pairedDevices.find { it.address == savedDeviceMac }
                                                if (savedDevice != null) {
                                                    viewModel.connectToDevice(savedDevice)
                                                } else {
                                                    viewModel.startAutoConnect()
                                                }
                                                showConnectingDialog = true
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Text(stringResource(R.string.connect), style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        
                        // Battery Sub-bar
                        if (isConnected && batteryL >= 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BudBatteryInfo(stringResource(R.string.buds_left), batteryL, placementL, chargingL, temperatureL)
                                BudBatteryInfo(stringResource(R.string.buds_case), batteryCase, com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN, chargingCase, null)
                                BudBatteryInfo(stringResource(R.string.buds_right), batteryR, placementR, chargingR, temperatureR)
                            }
                        }
                        

                        if (!isNotificationGranted) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.allow_notifications_required))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Noise Controls Card
            item {
                Text(
                    text = stringResource(R.string.noise_controls),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(
                        animationSpec = spring(stiffness = Spring.StiffnessHigh)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val controls = buildList {
                            add(NoiseControlMode.OFF)
                            if (effectiveModel.supportsTransparencyNC) add(NoiseControlMode.TRANSPARENT)
                            if (effectiveModel.supportsAdaptiveNC) add(NoiseControlMode.ADAPTIVE)
                            add(NoiseControlMode.NOISE_CANCELLATION)
                        }
                        val bothInEar = isConnected && placementL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING && placementR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                        val anyInEar = isConnected && (placementL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING || placementR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING)
                        val isBudsTransparencyAllowed = effectiveModel == com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_3_PRO || effectiveModel == com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_4_PRO
                        controls.forEach { mode ->
                            val isSelected = activeNoiseControl == mode
                            val isModeEnabled = if (!isConnected || !anyInEar) {
                                false
                            } else if (bothInEar || oneEarbudNoiseControlEnabled) {
                                true
                            } else {
                                if (isBudsTransparencyAllowed) {
                                    mode == NoiseControlMode.OFF || mode == NoiseControlMode.TRANSPARENT
                                } else {
                                    mode == NoiseControlMode.OFF
                                }
                            }
                            
                            val bgColor by androidx.compose.animation.animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                animationSpec = androidx.compose.animation.core.tween(300)
                            )
                            val iconTint by androidx.compose.animation.animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = androidx.compose.animation.core.tween(300)
                            )
                            val textColor by androidx.compose.animation.animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = androidx.compose.animation.core.tween(300)
                            )
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .bounceClick(enabled = isModeEnabled) { viewModel.applyImmediateNoiseControl(mode) }
                                    .padding(8.dp)
                                    .alpha(if (isModeEnabled) 1f else 0.5f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(bgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when(mode) {
                                            NoiseControlMode.NOISE_CANCELLATION -> Icons.AutoMirrored.Filled.VolumeOff
                                            NoiseControlMode.OFF -> Icons.Default.Close
                                            NoiseControlMode.TRANSPARENT -> Icons.Default.Hearing
                                            NoiseControlMode.ADAPTIVE -> Icons.Default.AutoAwesome
                                            else -> Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        tint = iconTint
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(mode.displayNameRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = isConnected) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                isMoreSettingsExpanded = !isMoreSettingsExpanded
                            }
                            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp)
                            .alpha(if (isConnected) 1f else 0.5f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.more_settings),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (isMoreSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.toggle_more_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = isMoreSettingsExpanded && isConnected,
                        enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessHigh)),
                        exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessHigh))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            // 1. Noise Control with One Earbud
                            NoiseControlWithOneEarbudCard(
                                isConnected = isConnected,
                                oneEarbudNoiseControlEnabled = oneEarbudNoiseControlEnabled,
                                onCheckedChange = { viewModel.setOneEarbudNoiseControl(it) }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )

                            // 2. Use ambient sound during calls
                            if (effectiveModel.supportsTransparencyNC) {
                                UseAmbientSoundDuringCallsCard(
                                    isConnected = uiState.isConnected,
                                    useAmbientSoundDuringCalls = uiState.useAmbientSoundDuringCalls,
                                    onCheckedChange = { viewModel.setUseAmbientSoundDuringCalls(it) }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            }

                            // 3. In-ear detection for calls
                            InEarDetectionForCallsCard(
                                isConnected = isConnected,
                                inEarDetectionForCalls = inEarDetectionForCalls,
                                onCheckedChange = { viewModel.setInEarDetectionForCalls(it) }
                            )
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                            
                            // Double Tap Earbud Edge
                            if (effectiveModel.supportsDoubleTapEdge) {
                                DoubleTapEdgeCard(
                                    isConnected = isConnected,
                                    doubleTapEdgeEnabled = doubleTapEdgeEnabled,
                                    onCheckedChange = { viewModel.setDoubleTapEdgeEnabled(it) }
                                )
                                
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                            
                            SoundBalanceCard(
                                isConnected = isConnected,
                                placementL = placementL,
                                placementR = placementR,
                                stereoBalance = stereoBalance,
                                onBalanceChange = { viewModel.setStereoBalance(it) },
                                onBalanceChangeFinished = { viewModel.setStereoBalance(it) },
                                onSoundBalanceTestClick = onSoundBalanceTestClick
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Voice Detect Switch Card
            if (effectiveModel.supportsConversationDetection) item {
                VoiceDetectCard(
                    isConnected = isConnected,
                    placementL = placementL,
                    placementR = placementR,
                    conversationDetectionEnabled = conversationDetectionEnabled,
                    onCheckedChange = { viewModel.setConversationDetection(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Auto-Pause on Transparency Mode — independent toggle
            if (effectiveModel.supportsTransparencyNC) {
                item {
                AutoPauseMediaCard(
                    isConnected = isConnected,
                    pauseMediaOnConversation = pauseMediaOnConversation,
                    onCheckedChange = { rulesViewModel.setPauseMediaOnConversation(it, prefsLocal) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Wear State Actions Button
            item {
                WearStateActionsCard(
                    isConnected = isConnected,
                    onClick = onWearStateClick
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Fit Test Button
            if (effectiveModel.supportsFitTest) {
                item {
                    FitTestCard(
                        isConnected = isConnected,
                        placementL = placementL,
                        placementR = placementR,
                        onClick = onFitTestClick
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // Find My Earbuds Button
            item {
                FindMyEarbudsCard(
                    isConnected = isConnected,
                    onClick = onFindMyBudsClick
                )
            }
        }

        PageHeader(
            title = stringResource(R.string.bud_buddy),
            isScrolled = isScrolled,
            actionIcon = {
                val isUpdateAvailable by com.benegedeniz.budsdynamiceq.util.UpdateChecker.isUpdateAvailable.collectAsState()
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onSettingsClick()
                    },
                    modifier = Modifier.bounceClick()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isUpdateAvailable) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 3.dp, y = (-3).dp)
                                    .background(com.benegedeniz.budsdynamiceq.ui.theme.ErrorRed, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PriorityHigh,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    if (showDeviceDialog) {
        com.benegedeniz.budsdynamiceq.ui.components.DeviceSelectionDialog(
            pairedDevices = pairedDevices,
            savedDeviceMac = savedDeviceMac,
            isConnected = isConnected,
            isConnecting = isConnecting,
            isBluetoothEnabled = viewModel.isBluetoothEnabled(),
            onSelectDevice = { device ->
                viewModel.connectToDevice(device)
                showDeviceDialog = false
                showConnectingDialog = true
            },
            onRefresh = { viewModel.refreshPairedDevices() },
            onForgetDevice = { viewModel.disconnect(forget = true) },
            onDismissRequest = { showDeviceDialog = false }
        )
    }

    if (showConnectingDialog) {
        val savedDeviceName = remember(savedDeviceMac, pairedDevices) {
            pairedDevices.find { it.address == savedDeviceMac }?.let {
                try { it.name } catch (e: SecurityException) { null }
            } ?: savedDeviceMac
        }
        com.benegedeniz.budsdynamiceq.ui.components.ConnectingDialog(
            deviceName = savedDeviceName ?: stringResource(R.string.buds_galaxy_buds_default),
            macAddress = savedDeviceMac,
            isConnected = isConnected,
            onCancel = {
                viewModel.disconnect(forget = false)
                showConnectingDialog = false
            },
            onForgetAndSelectOther = {
                viewModel.disconnect(forget = true)
                showConnectingDialog = false
                viewModel.refreshPairedDevices()
                showDeviceDialog = true
            },
            onDismissRequest = { showConnectingDialog = false }
        )
    }

    if (showModelDialog) {
        AlertDialog(
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { 
                if (!isModelUnknown) {
                    showModelDialog = false 
                }
            },
            title = {
                Text(stringResource(R.string.device_model), style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (connectedModel != com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.UNKNOWN) {
                        Text(
                            text = stringResource(R.string.buds_auto_detected, stringResource(connectedModel.displayNameRes)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (isModelUnknown) {
                        Text(
                            text = stringResource(R.string.buds_auto_detect_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Auto-detect option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setModelOverride(null)
                                if (connectedModel != com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.UNKNOWN) {
                                    showModelDialog = false
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = modelOverride == null,
                            onClick = {
                                viewModel.setModelOverride(null)
                                if (connectedModel != com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.UNKNOWN) {
                                    showModelDialog = false
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.auto_detect),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.detect_model_from_firmware_version),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    
                    // Manual model options
                    val models = listOf(
                        com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_4_PRO,
                        com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_4,
                        com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_3_PRO,
                        com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_3,
                        com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_2_PRO,
                        com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_2
                    )
                    models.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setModelOverride(model)
                                    showModelDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = modelOverride == model,
                                onClick = {
                                    viewModel.setModelOverride(model)
                                    showModelDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(model.displayNameRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!isModelUnknown) {
                    TextButton(onClick = { showModelDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        )
    }
}

@Composable
fun BudBatteryInfo(label: String, battery: Int, placement: com.benegedeniz.budsdynamiceq.data.model.PlacementState, isCharging: Boolean = false, temperature: Double? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val color = if (battery <= 0) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        } else if (placement == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Text(text = label, style = MaterialTheme.typography.titleMedium, color = color)
        Spacer(modifier = Modifier.height(4.dp))
        val isActuallyCharging = isCharging && battery > 0 && (label == "Case" || placement == com.benegedeniz.budsdynamiceq.data.model.PlacementState.CASE || placement == com.benegedeniz.budsdynamiceq.data.model.PlacementState.CLOSED_CASE)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isActuallyCharging) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringResource(R.string.charging),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp).padding(end = 2.dp)
                )
            }
            if (battery > 0) {
                Text(text = "$battery%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            } else {
                Text(text = "--", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (temperature != null && battery > 0) {
            Text(text = "${String.format("%.1f", temperature)}°C", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
