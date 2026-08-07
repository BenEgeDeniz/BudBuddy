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
import com.benegedeniz.budsdynamiceq.ui.components.PageHeader
import com.benegedeniz.budsdynamiceq.ui.components.bounceClick
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

@Composable
fun BudsScreen(
    viewModel: BudsViewModel,
    onFitTestClick: () -> Unit = {},
    onWearStateClick: () -> Unit = {},
    onSoundBalanceTestClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onFindMyBudsClick: () -> Unit = {},
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
    var pauseMediaOnConversation by remember { androidx.compose.runtime.mutableStateOf(prefsLocal.getBoolean("pause_media_on_conversation", false)) }
    
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

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 140.dp, bottom = 120.dp)
        ) {
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .alpha(if (isConnected) 1f else 0.5f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Hearing,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.noise_control_with_one_earbud),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        softWrap = true
                                    )
                                }
                                Switch(
                                    checked = oneEarbudNoiseControlEnabled,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.setOneEarbudNoiseControl(it)
                                    },
                                    enabled = isConnected,
                                    modifier = Modifier.scale(0.85f)
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )

                            // 2. Use ambient sound during calls
                            if (effectiveModel.supportsTransparencyNC) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .alpha(if (isConnected) 1f else 0.5f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhoneInTalk,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.use_ambient_sound_during_calls),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            softWrap = true
                                        )
                                    }
                                    Switch(
                                        checked = uiState.useAmbientSoundDuringCalls,
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            viewModel.setUseAmbientSoundDuringCalls(it)
                                        },
                                        enabled = uiState.isConnected,
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            }

                            // 3. In-ear detection for calls
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .alpha(if (isConnected) 1f else 0.5f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PhoneCallback,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.in_ear_detection_for_calls),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            softWrap = true
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.play_calls_through_earbuds_when_in_ear_s),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                            softWrap = true
                                        )
                                    }
                                }
                                Switch(
                                    checked = inEarDetectionForCalls,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.setInEarDetectionForCalls(it)
                                    },
                                    enabled = isConnected,
                                    modifier = Modifier.scale(0.85f)
                                )
                            }
                            
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                            
                            // Double Tap Earbud Edge (only Buds 2 / Buds 2 Pro)
                            val currentModel = connectedModel.takeIf { it != com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.UNKNOWN } ?: effectiveModel
                            if (currentModel == com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_2 || currentModel == com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_2_PRO) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .alpha(if (isConnected) 1f else 0.5f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TouchApp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.double_tap_earbud_edge),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                softWrap = true
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = stringResource(R.string.double_tap_earbud_edge_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                softWrap = true
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = doubleTapEdgeEnabled,
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            viewModel.setDoubleTapEdgeEnabled(it)
                                        },
                                        enabled = isConnected,
                                        modifier = Modifier.scale(0.85f)
                                    )
                                }
                                
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                            
                            // 4. Left/Right Sound Balance
                            var isDraggingBalance by remember { mutableStateOf(false) }
                            var localBalance by remember(stereoBalance) { mutableFloatStateOf(stereoBalance.toFloat()) }
                            var lastSentBalanceTime by remember { mutableLongStateOf(0L) }
                            var lastHapticValue by remember { mutableIntStateOf(stereoBalance) }
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .alpha(if (isConnected) 1f else 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 0.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.left_right_sound_balance),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                Slider(
                                    value = if (isDraggingBalance) localBalance else stereoBalance.toFloat(),
                                    onValueChange = { newValue ->
                                        isDraggingBalance = true
                                        val snapped = if (newValue in 15f..17f) {
                                            16f
                                        } else {
                                            newValue
                                        }
                                        
                                        val newInt = snapped.toInt()
                                        if (newInt != lastHapticValue) {
                                            if (newInt == 16) {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            } else {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            }
                                            lastHapticValue = newInt
                                        }
                                        
                                        localBalance = snapped
                                        
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastSentBalanceTime > 300) {
                                            viewModel.setStereoBalance(newInt)
                                            lastSentBalanceTime = currentTime
                                        }
                                    },
                                    onValueChangeFinished = {
                                        isDraggingBalance = false
                                        viewModel.setStereoBalance(localBalance.toInt())
                                    },
                                    valueRange = 0f..32f,
                                    enabled = isConnected,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.l), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = run {
                                            val current = if (isDraggingBalance) localBalance.toInt() else stereoBalance
                                            when (current) {
                                                16 -> stringResource(R.string.buds_balanced)
                                                in 0..15 -> stringResource(R.string.buds_battery_l, ((16 - current) / 16f * 100).toInt())
                                                else -> stringResource(R.string.buds_battery_r, ((current - 16) / 16f * 100).toInt())
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(stringResource(R.string.r), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                val bothInEar = isConnected && placementL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING && placementR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                                
                                OutlinedButton(
                                    onClick = onSoundBalanceTestClick,
                                    enabled = bothInEar,
                                    modifier = Modifier.fillMaxWidth().height(42.dp).bounceClick(enabled = bothInEar),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.take_hearing_test), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Voice Detect Switch Card
            if (effectiveModel.supportsConversationDetection) item {
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                        val bothInEar = isConnected && placementL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING && placementR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .alpha(if (bothInEar) 1f else 0.5f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.conversation_detection),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.automatically_switches_to_ambient_mode_w),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!bothInEar && isConnected) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.requires_both_earbuds_to_be_worn),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = conversationDetectionEnabled,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.setConversationDetection(it)
                                },
                                enabled = bothInEar
                            )
                        }

                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Auto-Pause on Transparency Mode — independent toggle
            if (effectiveModel.supportsTransparencyNC) {
                item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .alpha(if (isConnected) 1f else 0.5f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auto_pause_media),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.pauses_media_when_ambient_mode_is_trigge),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = pauseMediaOnConversation,
                            onCheckedChange = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                pauseMediaOnConversation = it
                                prefsLocal.edit().putBoolean("pause_media_on_conversation", it).apply()
                                com.benegedeniz.budsdynamiceq.di.ServiceLocator.setPauseMediaOnConversation(it)
                            },
                            enabled = isConnected
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Wear State Actions Button
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(enabled = isConnected) { onWearStateClick() },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .alpha(if (isConnected) 1f else 0.5f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headset,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.wear_state_actions),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Fit Test Button
            if (connectedModel.supportsFitTest) {
                item {
                val fitTestEnabled = isConnected && placementL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING && placementR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(enabled = fitTestEnabled) { onFitTestClick() },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .alpha(if (fitTestEnabled) 1f else 0.5f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.earbud_fit_test),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!fitTestEnabled && isConnected) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.requires_both_earbuds_to_be_worn),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            }
            
            // Find My Earbuds Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(enabled = isConnected) { onFindMyBudsClick() },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .alpha(if (isConnected) 1f else 0.5f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.find_my_earbuds),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
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
