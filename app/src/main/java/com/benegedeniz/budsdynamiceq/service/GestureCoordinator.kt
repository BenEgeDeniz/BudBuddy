package com.benegedeniz.budsdynamiceq.service

import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.data.GestureRepository
import com.benegedeniz.budsdynamiceq.gesture.GestureDetector
import com.benegedeniz.budsdynamiceq.gesture.NoiseDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GestureCoordinator(
    private val scope: CoroutineScope,
    private val budsController: BudsController,
    private val gestureRepo: GestureRepository,
    private val gestureDetector: GestureDetector,
    private val noiseDetector: NoiseDetector,
    private val headShakeEnabledFlow: StateFlow<Boolean>,
    private val requireBothEarbudsFlow: StateFlow<Boolean>
) {
    fun start() {
        scope.launch {
            val wearingFlow = combine(
                budsController.placementL,
                budsController.placementR,
                requireBothEarbudsFlow
            ) { pL, pR, requireBoth ->
                val unknownL = pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN
                val unknownR = pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN
                // If placement hasn't been determined yet (right after connection), assume wearing
                // so we don't prematurely kill the IMU stream before the first status packet arrives.
                if (unknownL && unknownR) {
                    true
                } else if (requireBoth) {
                    pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING && pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                } else {
                    pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING || pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                }
            }

            combine(
                headShakeEnabledFlow,
                budsController.isConnected,
                gestureRepo.gestures,
                budsController.isFitTestScreenOpen,
                wearingFlow
            ) { enabled, connected, gestures, fitTestOpen, isWearing ->
                if (fitTestOpen || !isWearing) Triple(false, connected, gestures)
                else Triple(enabled, connected, gestures)
            }.collect { (enabled, connected, gestures) ->
                val activeGestures = gestures.filter { it.enabled }
                if (enabled && connected && activeGestures.isNotEmpty()) {
                    gestureDetector.start(activeGestures)
                    noiseDetector.start(activeGestures)
                    budsController.startSpatialSensor("gesture_detection")
                } else {
                    gestureDetector.stop()
                    noiseDetector.stop()
                    budsController.stopSpatialSensor("gesture_detection")
                }
            }
        }
    }
}
