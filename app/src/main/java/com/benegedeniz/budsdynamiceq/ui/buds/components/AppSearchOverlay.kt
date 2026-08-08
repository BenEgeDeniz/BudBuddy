package com.benegedeniz.budsdynamiceq.ui.buds.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Pause
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.bluetooth.BudsModel
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.benegedeniz.budsdynamiceq.ui.buds.BudsViewModel
import com.benegedeniz.budsdynamiceq.ui.components.SearchBarInput
import com.benegedeniz.budsdynamiceq.ui.headshake.HeadShakeViewModel
import com.benegedeniz.budsdynamiceq.ui.rules.RulesViewModel
import com.benegedeniz.budsdynamiceq.ui.headshake.GestureCard
import com.benegedeniz.budsdynamiceq.ui.headshake.NoiseProfileCard
import com.benegedeniz.budsdynamiceq.ui.rules.RuleItem
import com.benegedeniz.budsdynamiceq.ui.buds.components.ActionButtonCard

import com.benegedeniz.budsdynamiceq.ui.buds.components.*
import com.benegedeniz.budsdynamiceq.ui.rules.components.*
import com.benegedeniz.budsdynamiceq.ui.headshake.components.*

@Composable
fun AppSearchOverlay(
    isVisible: Boolean,
    onClose: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    gesturesTabEnabled: Boolean,
    budsViewModel: BudsViewModel,
    rulesViewModel: RulesViewModel,
    headShakeViewModel: HeadShakeViewModel,
    onFitTestClick: () -> Unit,
    onWearStateClick: () -> Unit,
    onSoundBalanceTestClick: () -> Unit,
    onFindMyBudsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val context = LocalContext.current

    // Gather data
    val budsState by budsViewModel.uiState.collectAsState()
    val rulesState by rulesViewModel.uiState.collectAsState()
    val gesturesState by headShakeViewModel.uiState.collectAsState()

    val effectiveModel = budsState.effectiveModel

    LaunchedEffect(isVisible) {
        if (isVisible) {
            searchQuery = ""
            try { focusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(spring()) + fadeIn(),
        exit = shrinkVertically(spring()) + fadeOut(),
        modifier = modifier.fillMaxSize().zIndex(100f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClose() })
                }
        ) {
            // Background blur layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
            )

            // Content layer
            Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(130.dp)) // padding for top bar
            
            SearchBarInput(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholderText = stringResource(R.string.search_app_wide),
                modifier = Modifier.padding(horizontal = 16.dp).focusRequester(focusRequester)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val query = searchQuery.lowercase()
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, top = 16.dp)
            ) {
                if (searchQuery.isBlank()) {
                    item(key = "empty_start") {
                        Column(
                            modifier = Modifier.fillMaxWidth().animateItem().padding(top = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.start_typing_to_search),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    // 1. Search Toggles
                    val toggleNoiseControls = context.getString(R.string.noise_controls).lowercase()
                    val ancAliases = listOf(
                        context.getString(R.string.nc_off).lowercase(),
                        context.getString(R.string.nc_anc).lowercase(),
                        context.getString(R.string.nc_transparent).lowercase(),
                        context.getString(R.string.nc_adaptive).lowercase()
                    )
                    val mNoiseControls = toggleNoiseControls.contains(query) || ancAliases.any { it.contains(query) }
                    
                    val titleOneEarbud = context.getString(R.string.noise_control_with_one_earbud).lowercase()
                    val mOneEarbud = titleOneEarbud.contains(query)
                    
                    val titleAmbientCall = context.getString(R.string.use_ambient_sound_during_calls).lowercase()
                    val mAmbientCall = effectiveModel.supportsTransparencyNC && titleAmbientCall.contains(query)
                    
                    val titleInEarCall = context.getString(R.string.in_ear_detection_for_calls).lowercase()
                    val mInEarCall = titleInEarCall.contains(query)
                    
                    val titleFitTest = context.getString(R.string.earbud_fit_test).lowercase()
                    val mFitTest = effectiveModel.supportsFitTest && titleFitTest.contains(query)
                    
                    val titleWearState = context.getString(R.string.wear_state_actions).lowercase()
                    val mWearState = titleWearState.contains(query)
                    
                    val titleSoundBalance = context.getString(R.string.take_hearing_test).lowercase()
                    val mSoundBalance = titleSoundBalance.contains(query) || context.getString(R.string.left_right_sound_balance).lowercase().contains(query)
                    
                    val titleFindBuds = context.getString(R.string.find_my_earbuds).lowercase()
                    val mFindBuds = titleFindBuds.contains(query)
                    
                    val toggleDoubleTap = context.getString(R.string.double_tap_earbud_edge).lowercase()
                    val mDoubleTap = effectiveModel.supportsDoubleTapEdge && toggleDoubleTap.contains(query)
                    
                    val toggleVoiceDetect = context.getString(R.string.conversation_detection).lowercase()
                    val mVoiceDetect = effectiveModel.supportsConversationDetection && toggleVoiceDetect.contains(query)
                    
                    val togglePauseMedia = context.getString(R.string.auto_pause_media).lowercase()
                    val pauseMediaDesc = context.getString(R.string.pauses_media_when_ambient_mode_is_trigge).lowercase()
                    val mPauseMedia = effectiveModel.supportsConversationDetection && (togglePauseMedia.contains(query) || pauseMediaDesc.contains(query))

                    if (mNoiseControls || mOneEarbud || mAmbientCall || mInEarCall || mFitTest || mWearState || mSoundBalance || mFindBuds || mDoubleTap || mVoiceDetect || mPauseMedia) {
                        item(key = "header_home") {
                            SearchSectionHeader(
                                appName = context.getString(R.string.app_name),
                                sectionName = context.getString(R.string.tab_home),
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    if (mNoiseControls) {
                        item(key = "noise_controls") {
                            NoiseControlsCard(
                                effectiveModel = budsState.effectiveModel,
                                activeNoiseControl = budsState.activeNoiseControl ?: NoiseControlMode.OFF,
                                isConnected = budsState.isConnected,
                                placementL = budsState.placementL,
                                placementR = budsState.placementR,
                                oneEarbudNoiseControlEnabled = budsState.oneEarbudNoiseControlEnabled,
                                onNoiseControlSelect = { mode -> budsViewModel.applyImmediateNoiseControl(mode) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (mOneEarbud) {
                        item(key = "one_earbud") {
                            NoiseControlWithOneEarbudCard(
                                isConnected = budsState.isConnected,
                                oneEarbudNoiseControlEnabled = budsState.oneEarbudNoiseControlEnabled,
                                onCheckedChange = { budsViewModel.setOneEarbudNoiseControl(it) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (mAmbientCall) {
                        item(key = "ambient_call") {
                            UseAmbientSoundDuringCallsCard(
                                isConnected = budsState.isConnected,
                                useAmbientSoundDuringCalls = budsState.useAmbientSoundDuringCalls,
                                onCheckedChange = { budsViewModel.setUseAmbientSoundDuringCalls(it) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (mInEarCall) {
                        item(key = "in_ear_call") {
                            InEarDetectionForCallsCard(
                                isConnected = budsState.isConnected,
                                inEarDetectionForCalls = budsState.inEarDetectionForCalls,
                                onCheckedChange = { budsViewModel.setInEarDetectionForCalls(it) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (mFitTest) {
                        item(key = "fit_test") {
                            FitTestCard(
                                isConnected = budsState.isConnected,
                                placementL = budsState.placementL,
                                placementR = budsState.placementR,
                                onClick = { 
                                    onClose()
                                    onFitTestClick() 
                                },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (mWearState) {
                        item(key = "wear_state") {
                            WearStateActionsCard(
                                isConnected = budsState.isConnected,
                                onClick = { 
                                    onClose()
                                    onWearStateClick() 
                                },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (mSoundBalance) {
                        item(key = "sound_balance") {
                            SoundBalanceCard(
                                isConnected = budsState.isConnected,
                                placementL = budsState.placementL,
                                placementR = budsState.placementR,
                                stereoBalance = budsState.stereoBalance,
                                onBalanceChange = { budsViewModel.setStereoBalance(it) },
                                onBalanceChangeFinished = { budsViewModel.setStereoBalance(it) },
                                onSoundBalanceTestClick = { 
                                    onClose()
                                    onSoundBalanceTestClick() 
                                },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (mFindBuds) {
                        item(key = "find_buds") {
                            FindMyEarbudsCard(
                                isConnected = budsState.isConnected,
                                onClick = { 
                                    onClose()
                                    onFindMyBudsClick() 
                                },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (mDoubleTap) {
                        item(key = "double_tap") {
                            DoubleTapEdgeCard(
                                isConnected = budsState.isConnected,
                                doubleTapEdgeEnabled = budsState.doubleTapEdgeEnabled,
                                onCheckedChange = { budsViewModel.setDoubleTapEdgeEnabled(it) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (mVoiceDetect) {
                        item(key = "voice_detect") {
                            VoiceDetectCard(
                                isConnected = budsState.isConnected,
                                placementL = budsState.placementL,
                                placementR = budsState.placementR,
                                conversationDetectionEnabled = budsState.conversationDetectionEnabled,
                                onCheckedChange = { budsViewModel.setConversationDetection(it) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (mPauseMedia) {
                        item(key = "pause_media") {
                            val pauseMediaEnabled by rulesViewModel.pauseMediaOnConversationEnabled.collectAsState()
                            val prefs = context.getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE)
                            
                            AutoPauseMediaCard(
                                isConnected = budsState.isConnected,
                                pauseMediaOnConversation = pauseMediaEnabled,
                                onCheckedChange = { rulesViewModel.setPauseMediaOnConversation(it, prefs) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // 2. Search Rules
                    val titleActiveRule = context.getString(R.string.rules_active_rule, "").lowercase().replace(":", "").trim()
                    val mActiveRule = titleActiveRule.contains(query) || query.contains("rule")
                    val titleGlobalDefaults = context.getString(R.string.global_defaults).lowercase()
                    val mGlobalDefaults = titleGlobalDefaults.contains(query) || query.contains("default")
                    val matchingRules = rulesState.rules.filter { it.keyword.lowercase().contains(query) }

                    if (mActiveRule || mGlobalDefaults || matchingRules.isNotEmpty()) {
                        item(key = "header_rules") {
                            SearchSectionHeader(
                                appName = context.getString(R.string.app_name),
                                sectionName = context.getString(R.string.tab_rules),
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    if (mActiveRule) {
                        item(key = "active_rule") {
                            ActiveRuleCard(
                                isConnected = budsState.isConnected,
                                lastMatchedRule = rulesState.lastMatchedRule,
                                manualPreset = rulesState.manualPreset,
                                manualNoiseControl = rulesState.manualNoiseControl,
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    
                    if (mGlobalDefaults) {
                        item(key = "global_defaults") {
                            GlobalDefaultsCard(
                                isConnected = budsState.isConnected,
                                effectiveModel = budsState.effectiveModel,
                                manualPreset = rulesState.manualPreset,
                                manualNoiseControl = rulesState.manualNoiseControl,
                                onSetManualPreset = { rulesViewModel.setManualPreset(it) },
                                onSetManualNoiseControl = { rulesViewModel.setManualNoiseControl(it) },
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    items(matchingRules, key = { "rule_${it.id}" }) { rule ->
                        RuleItem(
                            rule = rule,
                            isMatched = false,
                            showDragHandle = false,
                            onToggle = { rulesViewModel.toggleRule(rule, it) },
                            onEdit = { 
                                onClose()
                                onNavigateToTab(1) // 1 is Rules tab
                                rulesViewModel.isEditScreenOpen = true
                                rulesViewModel.editingRule = rule
                            },
                            onDelete = { rulesViewModel.deleteRule(rule.id) },
                            modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // 3. Search Gestures
                    val titleGestures = context.getString(R.string.gestures).lowercase()
                    val mGesturesStatus = gesturesTabEnabled && titleGestures.contains(query)
                    val titleMovementCancelling = context.getString(R.string.movement_cancelling).lowercase()
                    val mMovementCancelling = gesturesTabEnabled && titleMovementCancelling.contains(query)
                    val matchingGestures = if (gesturesTabEnabled) {
                        gesturesState.gestures.filter { gesture ->
                            gesture.name.lowercase().contains(query)
                        }
                    } else emptyList()

                    if (mGesturesStatus || mMovementCancelling || matchingGestures.isNotEmpty()) {
                        item(key = "header_gestures") {
                            SearchSectionHeader(
                                appName = context.getString(R.string.app_name),
                                sectionName = context.getString(R.string.tab_gestures),
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    if (mGesturesStatus) {
                        item(key = "gestures_status") {
                            val effectiveEnabled = gesturesState.headShakeEnabled && !gesturesState.isMissingEarbud
                            GesturesStatusCard(
                                isConnected = budsState.isConnected,
                                isMissingEarbud = gesturesState.isMissingEarbud,
                                headShakeEnabled = gesturesState.headShakeEnabled,
                                effectiveEnabled = effectiveEnabled,
                                gestures = gesturesState.gestures,
                                spatialAudioConflict = gesturesState.spatialAudioConflict,
                                doubleTapEdgeConflict = gesturesState.doubleTapEdgeConflict,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (gesturesState.isMissingEarbud) headShakeViewModel.forceHeadshakeOn()
                                        else headShakeViewModel.toggleHeadShake(true)
                                    } else {
                                        headShakeViewModel.toggleHeadShake(false)
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    if (mMovementCancelling) {
                        item(key = "movement_cancelling") {
                            MovementCancellingCard(
                                isConnected = budsState.isConnected,
                                isUiLocked = gesturesState.isUiLocked,
                                onCardClick = { 
                                    onClose()
                                    onNavigateToTab(2) // 2 is Gestures tab
                                    headShakeViewModel.isMovementCancellingScreenOpen = true 
                                },
                                onInfoClick = { 
                                    onClose()
                                    onNavigateToTab(2)
                                    headShakeViewModel.isMovementCancellingScreenOpen = true
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    items(matchingGestures, key = { "gesture_${it.id}" }) { gesture ->
                        GestureCard(
                            modifier = Modifier.animateItem(),
                            gesture = gesture,
                            canImprove = true,
                            onToggle = { headShakeViewModel.toggleGesture(gesture, it) },
                            onDelete = { headShakeViewModel.deleteGesture(gesture.id) },
                            onImprove = { 
                                onClose()
                                onNavigateToTab(2)
                                headShakeViewModel.improveDetection(gesture) 
                            },
                            onEditFlow = { 
                                onClose()
                                onNavigateToTab(2)
                                headShakeViewModel.editingFlowForGesture = gesture 
                            }
                        )
                    }

                    val hasAnyResults = mNoiseControls || mOneEarbud || mAmbientCall || mInEarCall || mFitTest || mWearState || mSoundBalance || mFindBuds || mDoubleTap || mVoiceDetect || mPauseMedia || mActiveRule || mGlobalDefaults || matchingRules.isNotEmpty() || mGesturesStatus || mMovementCancelling || matchingGestures.isNotEmpty()

                    if (!hasAnyResults) {
                        item(key = "empty_no_results") {
                            Column(
                                modifier = Modifier.fillMaxWidth().animateItem().padding(top = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.no_results_found),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
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
fun SearchSectionHeader(
    appName: String, 
    sectionName: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$appName > $sectionName",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}
