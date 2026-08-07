package com.benegedeniz.budsdynamiceq.service

import android.content.Context
import android.util.Log
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController.ImuSide
import com.benegedeniz.budsdynamiceq.gesture.GestureDetector
import com.benegedeniz.budsdynamiceq.gesture.NoiseDetector
import com.benegedeniz.budsdynamiceq.gesture.QuaternionSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sign
import kotlin.math.withSign

class ImuManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val budsController: BudsController,
    private val gestureDetector: GestureDetector,
    private val noiseDetector: NoiseDetector
) {
    companion object {
        private const val TAG = "ImuManager"
    }

    private var activeImu: ImuSide = ImuSide.UNKNOWN
    private var handoffJob: Job? = null
    private var isHandoffInProgress = false
    private var expectedPitchSignFlip = false
    private var lastPitch = 0f
    private var longTermMetric = 0f
    private var prevLWearing = budsController.placementL.value == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
    private var prevRWearing = budsController.placementR.value == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
    private var isImuForced = false
    private var smoothedZ = 0f
    private var lastRawSample: QuaternionSample? = null

    fun start() {
        startSpatialDataMonitor()
        startPlacementMonitor()
    }

    private fun getPitch(q: QuaternionSample): Float {
        val sinp = 2 * (q.w * q.y - q.z * q.x)
        return when {
            abs(sinp) >= 1f -> (Math.PI.toFloat() / 2).withSign(sinp)
            else -> asin(sinp.toDouble()).toFloat()
        }
    }

    private fun startSpatialDataMonitor() {
        scope.launch {
            budsController.spatialDataFlow.collect { sample ->
                val currentPitch = getPitch(sample)
                
                if (expectedPitchSignFlip && abs(lastPitch) > 0.1f && abs(currentPitch) > 0.1f) {
                    if (currentPitch.sign != lastPitch.sign) {
                        Log.i(TAG, "IMU handoff confirmed by pitch sign flip: \$lastPitch -> \$currentPitch")
                        expectedPitchSignFlip = false
                    }
                }
                lastPitch = currentPitch

                // Calculate the projection metrics.
                val hardwareGx = 2 * (sample.rawX * sample.rawZ - sample.rawW * sample.rawY)

                val isBuds2 = budsController.effectiveModel.value == com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_2 || budsController.effectiveModel.value == com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_2_PRO
                
                val metric = hardwareGx
                val rightSign = if (isBuds2) -1f else 1f
                val detectionMetric = metric * rightSign
                
                val normalizedMetric = detectionMetric

                smoothedZ = if (smoothedZ == 0f) detectionMetric else (smoothedZ * 0.95f) + (detectionMetric * 0.05f)
                
                // Update moving average (alpha=0.002 -> ~8 seconds at 60Hz)
                val expectedSign = if (activeImu == ImuSide.LEFT) 1f else -1f
                val normalizedSign = detectionMetric * expectedSign
                longTermMetric = if (longTermMetric == 0f) normalizedSign else (longTermMetric * 0.998f) + (normalizedSign * 0.002f)

                if (activeImu == ImuSide.UNKNOWN) {
                    // For Buds 2, threshold is 0.3. For Buds 4 Pro, it's 0.2 (since baseline is ~0.3).
                    val threshold = if (isBuds2) 0.3f else 0.2f
                    if (normalizedMetric > threshold) {
                        Log.i(TAG, "Auto-detected LEFT earbud as primary IMU from baseline metric=\$normalizedMetric")
                        activeImu = ImuSide.LEFT
                        budsController.setActiveImuSide(activeImu, context.getString(R.string.auto_detected_left_imu))
                        smoothedZ = detectionMetric
                    } else if (normalizedMetric < -threshold) {
                        Log.i(TAG, "Auto-detected RIGHT earbud as primary IMU from baseline metric=\$normalizedMetric")
                        activeImu = ImuSide.RIGHT
                        budsController.setActiveImuSide(activeImu, context.getString(R.string.auto_detected_right_imu))
                        smoothedZ = detectionMetric
                    }
                } else if (!isHandoffInProgress && prevLWearing && prevRWearing && !isImuForced) {
                    val dot = lastRawSample?.let {
                        it.rawX * sample.rawX + it.rawY * sample.rawY + it.rawZ * sample.rawZ + it.rawW * sample.rawW
                    } ?: 1f

                    if (abs(dot) < 0.92f) {
                        if (activeImu == ImuSide.RIGHT && normalizedMetric > 0f) {
                            Log.w(TAG, "Hardware spontaneous IMU hijack to LEFT detected! (dot = \$dot). Correcting.")
                            activeImu = ImuSide.LEFT
                            budsController.setActiveImuSide(activeImu, context.getString(R.string.hardware_imu_hijack))
                            expectedPitchSignFlip = true
                            longTermMetric = 0f // Reset to prevent self-healing from fighting the correction
                        } else if (activeImu == ImuSide.LEFT && normalizedMetric < 0f) {
                            Log.w(TAG, "Hardware spontaneous IMU hijack to RIGHT detected! (dot = \$dot). Correcting.")
                            activeImu = ImuSide.RIGHT
                            budsController.setActiveImuSide(activeImu, context.getString(R.string.hardware_imu_hijack))
                            expectedPitchSignFlip = true
                            longTermMetric = 0f // Reset to prevent self-healing from fighting the correction
                        }
                    }
                }
                
                // UNIVERSAL SAFETY NET: Instant absolute correction for impossible metrics.
                if (isBuds2 && !isImuForced) {
                    if (activeImu == ImuSide.RIGHT && normalizedMetric > 0.3f) {
                        Log.w(TAG, "Instant auto-correction to LEFT from normalizedMetric=\$normalizedMetric")
                        activeImu = ImuSide.LEFT
                        budsController.setActiveImuSide(activeImu, context.getString(R.string.self_healing_imu))
                        longTermMetric = 0f
                    } else if (activeImu == ImuSide.LEFT && normalizedMetric < -0.3f) {
                        Log.w(TAG, "Instant auto-correction to RIGHT from normalizedMetric=\$normalizedMetric")
                        activeImu = ImuSide.RIGHT
                        budsController.setActiveImuSide(activeImu, context.getString(R.string.self_healing_imu))
                        longTermMetric = 0f
                    }
                }

                // LONG-TERM SELF-HEALING (Enabled for all devices via stable Gx)
                if (!isHandoffInProgress && !isImuForced && longTermMetric != 0f) {
                    if (longTermMetric < -0.25f) {
                        Log.w(TAG, "Self-Healing activated! Metric settled heavily backwards (\$longTermMetric). Flipping active side.")
                        activeImu = if (activeImu == ImuSide.LEFT) ImuSide.RIGHT else ImuSide.LEFT
                        expectedPitchSignFlip = true
                        budsController.setActiveImuSide(activeImu, context.getString(R.string.self_healing_imu) + " (Long-Term)")
                        longTermMetric = 0f
                    }
                }
                
                lastRawSample = sample

                if (!isHandoffInProgress) {
                    gestureDetector.feedSample(sample)
                    noiseDetector.feedSample(sample)
                }
            }
        }
    }

    private fun startPlacementMonitor() {
        scope.launch {
            combine(
                budsController.placementL,
                budsController.placementR,
                budsController.batteryL,
                budsController.batteryR,
                budsController.isConnected
            ) { pL, pR, bL, bR, connected ->
                object {
                    val pL = pL
                    val pR = pR
                    val connected = connected
                }
            }.collect { state ->
                val pL = state.pL
                val pR = state.pR
                val connected = state.connected
                if (!connected) {
                    // Do NOT reset activeImu to UNKNOWN here. If the bluetooth drops for a second,
                    // we want to remember the side so we don't accidentally re-detect wrong if they
                    // happen to be looking down when it reconnects.
                    handoffJob?.cancel()
                    handoffJob = null
                    return@collect
                }

                val lWearing = pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                val rWearing = pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                
                val lDisconnected = pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.DISCONNECTED || pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.CASE || pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.CLOSED_CASE
                val rDisconnected = pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.DISCONNECTED || pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.CASE || pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.CLOSED_CASE

                val wearingChanged = (prevLWearing != lWearing) || (prevRWearing != rWearing)
                if (wearingChanged) {
                    budsController.kickstartSpatialSensor()
                }
                prevLWearing = lWearing
                prevRWearing = rWearing

                if (lWearing && !rWearing) {
                    isImuForced = true
                    budsController.setActiveImuSide(ImuSide.LEFT, context.getString(R.string.only_left_earbud_worn))
                    if (activeImu != ImuSide.LEFT) {
                        activeImu = ImuSide.LEFT
                        expectedPitchSignFlip = true
                    }
                    handoffJob?.cancel()
                    handoffJob = null
                    isHandoffInProgress = false
                } else if (rWearing && !lWearing) {
                    isImuForced = true
                    budsController.setActiveImuSide(ImuSide.RIGHT, context.getString(R.string.only_right_earbud_worn))
                    if (activeImu != ImuSide.RIGHT) {
                        activeImu = ImuSide.RIGHT
                        expectedPitchSignFlip = true
                    }
                    handoffJob?.cancel()
                    handoffJob = null
                    isHandoffInProgress = false
                } else if (lWearing && rWearing) {
                    isImuForced = false
                    when (activeImu) {
                        ImuSide.UNKNOWN -> {
                            // Defer activeImu selection to the spatial data flow heuristic!
                        }
                        ImuSide.RIGHT -> {
                            budsController.setActiveImuSide(ImuSide.RIGHT, context.getString(R.string.both_worn_heuristics))
                        }
                        ImuSide.LEFT -> {
                            budsController.setActiveImuSide(ImuSide.LEFT, context.getString(R.string.both_worn_heuristics))
                        }
                    }
                } else {
                    isImuForced = false
                }
            }
        }
    }
}
