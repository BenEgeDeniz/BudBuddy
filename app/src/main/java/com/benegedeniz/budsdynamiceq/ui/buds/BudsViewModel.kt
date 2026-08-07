package com.benegedeniz.budsdynamiceq.ui.buds

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.EqRule
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import com.benegedeniz.budsdynamiceq.bluetooth.BudsModel
import com.benegedeniz.budsdynamiceq.ui.state.BudsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudsViewModel(application: Application) : AndroidViewModel(application) {

    private val budsController: BudsController = ServiceLocator.provideBudsController(application)
    private val mediaObserver = ServiceLocator.provideMediaObserver(application)

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())

    // We can't combine 30 flows in a single `combine` call (limit is 5 in standard library without custom wrappers).
    // So we group them logically and then combine the groups.
    private val connectionState = combine(
        budsController.isConnected,
        budsController.isConnecting,
        budsController.savedDeviceMac,
        _pairedDevices
    ) { isConnected, isConnecting, savedMac, paired ->
        ConnectionGroup(isConnected, isConnecting, savedMac, paired)
    }

    private val mediaState = combine(
        mediaObserver.currentMetadata,
        budsController.lastMatchedRule,
        budsController.manualPreset,
        budsController.manualNoiseControl,
        budsController.activeNoiseControl
    ) { metadata, lastRule, manualPreset, manualNc, activeNc ->
        MediaGroup(metadata, lastRule, manualPreset, manualNc, activeNc)
    }

    private val batteryState = combine(
        combine(
            budsController.batteryL,
            budsController.batteryR,
            budsController.batteryCase
        ) { bL, bR, bC -> Triple(bL, bR, bC) },
        combine(
            budsController.placementL,
            budsController.placementR,
            budsController.chargingL
        ) { pL, pR, cL -> Triple(pL, pR, cL) }
    ) { t1, t2 ->
        BatteryGroup(t1.first, t1.second, t1.third, t2.first, t2.second, t2.third)
    }
    
    private val hardwareState = combine(
        combine(
            budsController.chargingR,
            budsController.chargingCase,
            budsController.temperatureL
        ) { cR, cC, tL -> Triple(cR, cC, tL) },
        combine(
            budsController.temperatureR,
            budsController.fitTestResultL,
            budsController.fitTestResultR
        ) { tR, fL, fR -> Triple(tR, fL, fR) }
    ) { t1, t2 ->
        HardwareGroup(t1.first, t1.second, t1.third, t2.first, t2.second, t2.third)
    }

    private val featuresState = combine(
        combine(
            budsController.conversationDetectionEnabled,
            budsController.oneEarbudNoiseControlEnabled,
            budsController.useAmbientSoundDuringCalls
        ) { c, o, u -> Triple(c, o, u) },
        combine(
            budsController.inEarDetectionForCalls,
            budsController.doubleTapEdgeEnabled,
            budsController.stereoBalance
        ) { i, d, s -> Triple(i, d, s) }
    ) { t1, t2 ->
        FeaturesGroup(t1.first, t1.second, t1.third, t2.first, t2.second, t2.third)
    }

    private val modelState = combine(
        budsController.connectedModel,
        budsController.modelOverride,
        budsController.effectiveModel
    ) { connected, override, effective ->
        ModelGroup(connected, override, effective)
    }

    val uiState: StateFlow<BudsUiState> = combine(
        combine(connectionState, mediaState, batteryState) { conn, med, bat ->
            Triple(conn, med, bat)
        },
        combine(hardwareState, featuresState, modelState) { hw, feat, mod ->
            Triple(hw, feat, mod)
        }
    ) { group1, group2 ->
        val conn = group1.first
        val med = group1.second
        val bat = group1.third
        val hw = group2.first
        val feat = group2.second
        val mod = group2.third
        
        BudsUiState(
            isConnected = conn.isConnected,
            isConnecting = conn.isConnecting,
            savedDeviceMac = conn.savedMac,
            pairedDevices = conn.paired,
            
            currentMetadata = med.metadata,
            lastMatchedRule = med.lastRule,
            manualPreset = med.manualPreset,
            manualNoiseControl = med.manualNc,
            activeNoiseControl = med.activeNc,
            
            batteryL = bat.bL,
            batteryR = bat.bR,
            batteryCase = bat.bC,
            placementL = bat.pL,
            placementR = bat.pR,
            
            chargingL = bat.cL,
            chargingR = hw.cR,
            chargingCase = hw.cCase,
            temperatureL = hw.tL,
            temperatureR = hw.tR,
            
            conversationDetectionEnabled = feat.convDet,
            oneEarbudNoiseControlEnabled = feat.oneEarNC,
            useAmbientSoundDuringCalls = feat.ambCall,
            inEarDetectionForCalls = feat.inEarCall,
            doubleTapEdgeEnabled = feat.doubleTap,
            stereoBalance = feat.balance,
            
            fitTestResultL = hw.fitL,
            fitTestResultR = hw.fitR,
            
            connectedModel = mod.connected,
            modelOverride = mod.override,
            effectiveModel = mod.effective
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudsUiState()
    )

    init {
        refreshPairedDevices()
    }

    // --- Actions ---

    fun refreshPairedDevices() {
        _pairedDevices.value = budsController.getPairedDevices()
    }

    fun connectToDevice(device: BluetoothDevice) {
        budsController.connect(device)
        startService()
    }

    fun startAutoConnect() {
        budsController.startAutoConnect()
    }

    fun disconnect(forget: Boolean = false) {
        budsController.disconnect(forget = forget)
        if (forget) {
            stopService()
        }
    }

    fun setModelOverride(model: com.benegedeniz.budsdynamiceq.bluetooth.BudsModel?) {
        budsController.setModelOverride(model)
    }

    fun setConversationDetection(enabled: Boolean) {
        budsController.setConversationDetection(enabled)
    }

    fun setOneEarbudNoiseControl(enabled: Boolean) {
        budsController.setOneEarbudNoiseControl(enabled)
    }

    fun setUseAmbientSoundDuringCalls(enabled: Boolean) {
        budsController.setUseAmbientSoundDuringCalls(enabled)
    }

    fun setInEarDetectionForCalls(enabled: Boolean) {
        budsController.setInEarDetectionForCalls(enabled)
    }

    fun setDoubleTapEdgeEnabled(enabled: Boolean) {
        budsController.setDoubleTapEdgeEnabled(enabled)
    }

    fun setStereoBalance(value: Int) {
        budsController.setStereoBalance(value)
    }
    
    fun applyImmediateNoiseControl(ncMode: NoiseControlMode) {
        budsController.sendNoiseControl(ncMode)
    }

    fun setHomePageVisible(visible: Boolean) {
        budsController.isHomePageVisible.value = visible
    }

    fun startFitTest() {
        budsController.startFitTest()
    }

    fun stopFitTest() {
        budsController.stopFitTest()
    }

    fun setFitTestScreenOpen(isOpen: Boolean) {
        budsController.setFitTestScreenOpen(isOpen)
    }

    fun isBluetoothEnabled(): Boolean {
        return budsController.isBluetoothEnabled()
    }

    private fun startService() {
        val app = getApplication<Application>()
        val serviceIntent = android.content.Intent(app, com.benegedeniz.budsdynamiceq.service.BudsService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            app.startForegroundService(serviceIntent)
        } else {
            app.startService(serviceIntent)
        }
    }

    private fun stopService() {
        val app = getApplication<Application>()
        val serviceIntent = android.content.Intent(app, com.benegedeniz.budsdynamiceq.service.BudsService::class.java)
        app.stopService(serviceIntent)
    }
}

// Internal data classes for grouped combine
private data class ConnectionGroup(
    val isConnected: Boolean, val isConnecting: Boolean, val savedMac: String?, val paired: List<BluetoothDevice>
)
private data class MediaGroup(
    val metadata: com.benegedeniz.budsdynamiceq.media.SongMetadata?,
    val lastRule: EqRule?,
    val manualPreset: EqPreset?,
    val manualNc: NoiseControlMode?,
    val activeNc: NoiseControlMode?
)
private data class BatteryGroup(
    val bL: Int, val bR: Int, val bC: Int,
    val pL: com.benegedeniz.budsdynamiceq.data.model.PlacementState,
    val pR: com.benegedeniz.budsdynamiceq.data.model.PlacementState,
    val cL: Boolean
)
private data class HardwareGroup(
    val cR: Boolean, val cCase: Boolean, val tL: Double?, val tR: Double?,
    val fitL: com.benegedeniz.budsdynamiceq.data.model.FitTestResult,
    val fitR: com.benegedeniz.budsdynamiceq.data.model.FitTestResult
)
private data class FeaturesGroup(
    val convDet: Boolean, val oneEarNC: Boolean, val ambCall: Boolean, val inEarCall: Boolean, val doubleTap: Boolean, val balance: Int
)
private data class ModelGroup(
    val connected: BudsModel, val override: BudsModel?, val effective: BudsModel
)
