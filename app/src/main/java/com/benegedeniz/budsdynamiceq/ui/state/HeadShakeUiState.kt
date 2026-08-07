package com.benegedeniz.budsdynamiceq.ui.state

import com.benegedeniz.budsdynamiceq.data.model.HeadGesture
import com.benegedeniz.budsdynamiceq.ui.headshake.RecordingState
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController.ImuSide

data class HeadShakeUiState(
    val gestures: List<HeadGesture> = emptyList(),
    val headShakeEnabled: Boolean = false,
    val isMissingEarbud: Boolean = false,
    val isConnected: Boolean = false,
    val recordingState: RecordingState = RecordingState.IDLE,
    val spatialAudioConflict: Boolean = false,
    val doubleTapEdgeConflict: Boolean = false,
    val lastDetectedGesture: HeadGesture? = null,
    val isUiLocked: Boolean = false,
    val requireBothEarbuds: Boolean = false,
    val activeImuSide: ImuSide = ImuSide.LEFT,
    val activeImuReason: String = "",
    val invertPitch: Boolean = false
)
