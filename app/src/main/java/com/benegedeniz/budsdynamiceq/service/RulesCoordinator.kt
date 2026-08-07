package com.benegedeniz.budsdynamiceq.service

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.data.RulesRepository
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.media.MediaObserver
import com.benegedeniz.budsdynamiceq.rules.RulesEngine
import com.benegedeniz.budsdynamiceq.ui.widget.NoiseControlWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RulesCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val budsController: BudsController,
    private val mediaObserver: MediaObserver,
    private val rulesRepository: RulesRepository,
    private val transientNotificationFlow: MutableStateFlow<Pair<String, String>?>,
    private val notificationManagerHelper: NotificationManagerHelper
) {
    private val rulesEngine = RulesEngine()

    fun start() {
        scope.launch {
            var lastSongWithDefault: String? = null
            var wasConnected = false
            var wasBothInEar = false
            var lastAppliedManualEq: EqPreset? = null
            var lastAppliedManualNc: NoiseControlMode? = null

            val ruleStateFlow = combine(
                mediaObserver.currentMetadata,
                rulesRepository.rules,
                budsController.manualPreset,
                budsController.manualNoiseControl
            ) { metadata, rulesList, manualEq, manualNc ->
                object {
                    val metadata = metadata
                    val rulesList = rulesList
                    val manualEq = manualEq
                    val manualNc = manualNc
                }
            }
            
            val batteryPlacementFlow = combine(
                budsController.batteryL,
                budsController.batteryR,
                budsController.placementL,
                budsController.placementR
            ) { bL, bR, pL, pR ->
                object {
                    val bL = bL
                    val bR = bR
                    val pL = pL
                    val pR = pR
                }
            }
            
            val deviceStateFlow = combine(
                budsController.isConnected,
                batteryPlacementFlow,
                budsController.oneEarbudNoiseControlEnabled
            ) { connected, bp, oneEarbudEnabled ->
                object {
                    val connected = connected
                    val bL = bp.bL
                    val bR = bp.bR
                    val pL = bp.pL
                    val pR = bp.pR
                    val oneEarbudEnabled = oneEarbudEnabled
                }
            }

            val notificationOnlyFlow = combine(
                deviceStateFlow,
                budsController.activeNoiseControl
            ) { device, activeNc ->
                object {
                    val device = device
                    val activeNc = activeNc
                }
            }
            
            scope.launch {
                notificationOnlyFlow.collectLatest {
                    delay(200L) // Debounce rapid state changes like battery
                    try {
                        val manager = GlanceAppWidgetManager(context)
                        val widget = NoiseControlWidget()
                        manager.getGlanceIds(widget.javaClass).forEach { glanceId ->
                            updateAppWidgetState(context, glanceId) { prefs ->
                                val key = booleanPreferencesKey("force_update")
                                val current = prefs[key] ?: false
                                prefs[key] = !current
                            }
                            widget.update(context, glanceId)
                        }
                    } catch (e: Exception) {
                        Log.e("RulesCoordinator", "Failed to update widget", e)
                    }
                }
            }

            combine(
                transientNotificationFlow,
                ruleStateFlow,
                notificationOnlyFlow
            ) { transient, ruleState, notif ->
                object {
                    val transient = transient
                    val ruleState = ruleState
                    val deviceState = notif.device
                    val activeNc = notif.activeNc
                }
            }.collect { state ->
                val connected = state.deviceState.connected
                
                if (!connected) {
                    wasConnected = false
                    notificationManagerHelper.updateNotification(
                        titleText = "Disconnected", 
                        ruleNcText = context.getString(R.string.waiting_for_buds), 
                        hardwareNcText = "", 
                        lBatteryText = "", 
                        rBatteryText = "", 
                        isLWorn = false,
                        isRWorn = false,
                        isConnected = false,
                        toggleButtonText = ""
                    )
                    return@collect
                }
                
                val justConnected = !wasConnected
                wasConnected = true

                val isLWorn = state.deviceState.pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                val isRWorn = state.deviceState.pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                val bothInEar = isLWorn && isRWorn
                val justPutBothInEar = bothInEar && !wasBothInEar
                wasBothInEar = bothInEar

                val metadata = state.ruleState.metadata
                val manualEq = state.ruleState.manualEq
                val manualNc = state.ruleState.manualNc
                
                var activeRuleTitle = ""
                var activeRuleText = ""

                val matchingRule = rulesEngine.evaluate(metadata, state.ruleState.rulesList)
                if (matchingRule != null) {
                    val activeRuleInheritsEq = matchingRule.preset == EqPreset.DEFAULT
                    val activeRuleInheritsNc = matchingRule.noiseControl == NoiseControlMode.DEFAULT
                    
                    val ruleDefaultChanged = (activeRuleInheritsEq && lastAppliedManualEq != manualEq) || 
                                             (activeRuleInheritsNc && lastAppliedManualNc != manualNc)
                                             
                    val eqToSend = if (activeRuleInheritsEq) manualEq else matchingRule.preset
                    val ncToSend = if (activeRuleInheritsNc) manualNc else matchingRule.noiseControl
                                             
                    if (justConnected || justPutBothInEar || budsController.lastMatchedRule.value != matchingRule || ruleDefaultChanged) {
                        budsController.setLastMatchedRule(matchingRule)
                        if (eqToSend != null) budsController.sendEqualizer(eqToSend)
                        if (ncToSend != null) budsController.sendNoiseControl(ncToSend)
                        
                        lastAppliedManualEq = manualEq
                        lastAppliedManualNc = manualNc
                    }
                    lastSongWithDefault = null
                    
                    activeRuleTitle = context.getString(R.string.active_rule, matchingRule.keyword)
                    activeRuleText = context.getString(R.string.settings_format, eqToSend?.let { context.getString(it.displayNameRes) } ?: context.getString(R.string.none), ncToSend?.let { context.getString(it.displayNameRes) } ?: context.getString(R.string.none))
                } else {
                    val justDroppedOut = budsController.lastMatchedRule.value != null
                    budsController.setLastMatchedRule(null)
                    
                    val songDisplayString = metadata?.displayString ?: ""
                    val defaultChanged = lastAppliedManualEq != manualEq || lastAppliedManualNc != manualNc
                    if (justConnected || justDroppedOut || justPutBothInEar || lastSongWithDefault != songDisplayString || defaultChanged) {
                        if (manualEq != null) budsController.sendEqualizer(manualEq)
                        if (manualNc != null) budsController.sendNoiseControl(manualNc)
                        
                        lastSongWithDefault = songDisplayString
                        lastAppliedManualEq = manualEq
                        lastAppliedManualNc = manualNc
                    }
                    
                    activeRuleTitle = context.getString(R.string.default_settings)
                    activeRuleText = context.getString(R.string.settings_format, manualEq?.let { context.getString(it.displayNameRes) } ?: context.getString(R.string.none), manualNc?.let { context.getString(it.displayNameRes) } ?: context.getString(R.string.none))
                }
                
                val lText = "${state.deviceState.bL}%"
                val rText = "${state.deviceState.bR}%"
                
                val wearingOne = (isLWorn && !isRWorn) || (isRWorn && !isLWorn)
                val toggleText = if (wearingOne && !state.deviceState.oneEarbudEnabled) {
                    context.getString(R.string.toggle_off_ambient)
                } else if (!budsController.effectiveModel.value.supportsTransparencyNC) {
                    context.getString(R.string.toggle_anc_off)
                } else {
                    context.getString(R.string.toggle_anc_ambient)
                }
                val hardwareNcText = context.getString(R.string.active_nc_format, state.activeNc?.let { context.getString(it.displayNameRes) } ?: context.getString(R.string.unknown))

                if (state.transient != null) {
                    notificationManagerHelper.updateNotification(
                        titleText = state.transient.first,
                        ruleNcText = state.transient.second,
                        hardwareNcText = hardwareNcText,
                        lBatteryText = lText,
                        rBatteryText = rText,
                        isLWorn = isLWorn,
                        isRWorn = isRWorn,
                        isConnected = connected,
                        toggleButtonText = toggleText
                    )
                } else {
                    notificationManagerHelper.updateNotification(
                        titleText = activeRuleTitle,
                        ruleNcText = activeRuleText,
                        hardwareNcText = hardwareNcText,
                        lBatteryText = lText,
                        rBatteryText = rText,
                        isLWorn = isLWorn,
                        isRWorn = isRWorn,
                        isConnected = connected,
                        toggleButtonText = toggleText
                    )
                }
            }
        }
    }
}
