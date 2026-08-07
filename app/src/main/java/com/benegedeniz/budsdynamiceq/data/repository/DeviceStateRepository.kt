package com.benegedeniz.budsdynamiceq.data.repository

import com.benegedeniz.budsdynamiceq.bluetooth.BudsController.ImuSide
import com.benegedeniz.budsdynamiceq.bluetooth.BudsModel
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.EqRule
import com.benegedeniz.budsdynamiceq.data.model.FitTestResult
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import com.benegedeniz.budsdynamiceq.gesture.QuaternionSample
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class DeviceStateRepository {
    val isConnected = MutableStateFlow(false)
    val isConnecting = MutableStateFlow(false)
    val savedDeviceMac = MutableStateFlow<String?>(null)
    
    val isSpatialActive = MutableStateFlow(false)
    val isHomePageVisible = MutableStateFlow(false)
    
    val connectedModel = MutableStateFlow(BudsModel.UNKNOWN)
    val modelOverride = MutableStateFlow<BudsModel?>(null)
    
    val activeImuSide = MutableStateFlow(ImuSide.UNKNOWN)
    val activeImuReason = MutableStateFlow("Initializing...")
    
    val spatialDataFlow = MutableSharedFlow<QuaternionSample>(
        extraBufferCapacity = 100,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val rawSpatialDataFlow = MutableSharedFlow<QuaternionSample>(extraBufferCapacity = 64)
    
    val batteryL = MutableStateFlow(-1)
    val batteryR = MutableStateFlow(-1)
    val batteryCase = MutableStateFlow(-1)
    
    val chargingL = MutableStateFlow(false)
    val chargingR = MutableStateFlow(false)
    val chargingCase = MutableStateFlow(false)
    
    val temperatureL = MutableStateFlow<Double?>(null)
    val temperatureR = MutableStateFlow<Double?>(null)
    
    val placementL = MutableStateFlow(PlacementState.UNKNOWN)
    val placementR = MutableStateFlow(PlacementState.UNKNOWN)
    
    val fitTestResultL = MutableStateFlow(FitTestResult.UNKNOWN)
    val fitTestResultR = MutableStateFlow(FitTestResult.UNKNOWN)
    val isFitTestScreenOpen = MutableStateFlow(false)
    
    val conversationDetectionEnabled = MutableStateFlow(false)
    val oneEarbudNoiseControlEnabled = MutableStateFlow(false)
    val useAmbientSoundDuringCalls = MutableStateFlow(false)
    val inEarDetectionForCalls = MutableStateFlow(true)
    val doubleTapEdgeEnabled = MutableStateFlow(false)
    
    val stereoBalance = MutableStateFlow(16) // Default to center
    
    val lastMatchedRule = MutableStateFlow<EqRule?>(null)
    val manualPreset = MutableStateFlow<EqPreset?>(null)
    val manualNoiseControl = MutableStateFlow<NoiseControlMode?>(null)
    val activeNoiseControl = MutableStateFlow<NoiseControlMode?>(null)

    fun reset() {
        batteryL.value = -1
        batteryR.value = -1
        batteryCase.value = -1
        chargingL.value = false
        chargingR.value = false
        chargingCase.value = false
        placementL.value = PlacementState.UNKNOWN
        placementR.value = PlacementState.UNKNOWN
        fitTestResultL.value = FitTestResult.UNKNOWN
        fitTestResultR.value = FitTestResult.UNKNOWN
        activeImuSide.value = ImuSide.UNKNOWN
    }
}
