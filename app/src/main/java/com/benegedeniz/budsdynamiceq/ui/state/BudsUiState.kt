package com.benegedeniz.budsdynamiceq.ui.state

import android.bluetooth.BluetoothDevice
import com.benegedeniz.budsdynamiceq.bluetooth.BudsModel
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.EqRule
import com.benegedeniz.budsdynamiceq.data.model.FitTestResult
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import com.benegedeniz.budsdynamiceq.media.SongMetadata

data class BudsUiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val savedDeviceMac: String? = null,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    
    val currentMetadata: SongMetadata? = null,
    val lastMatchedRule: EqRule? = null,
    val manualPreset: EqPreset? = null,
    val manualNoiseControl: NoiseControlMode? = null,
    val activeNoiseControl: NoiseControlMode? = null,
    
    val batteryL: Int = -1,
    val batteryR: Int = -1,
    val batteryCase: Int = -1,
    
    val placementL: PlacementState = PlacementState.UNKNOWN,
    val placementR: PlacementState = PlacementState.UNKNOWN,
    
    val chargingL: Boolean = false,
    val chargingR: Boolean = false,
    val chargingCase: Boolean = false,
    
    val temperatureL: Double? = null,
    val temperatureR: Double? = null,
    
    val conversationDetectionEnabled: Boolean = false,
    val oneEarbudNoiseControlEnabled: Boolean = false,
    val useAmbientSoundDuringCalls: Boolean = false,
    val inEarDetectionForCalls: Boolean = false,
    val doubleTapEdgeEnabled: Boolean = false,
    val stereoBalance: Int = 0,
    
    val fitTestResultL: FitTestResult = FitTestResult.UNKNOWN,
    val fitTestResultR: FitTestResult = FitTestResult.UNKNOWN,
    
    val connectedModel: BudsModel = BudsModel.UNKNOWN,
    val modelOverride: BudsModel? = null,
    val effectiveModel: BudsModel = BudsModel.UNKNOWN
)
