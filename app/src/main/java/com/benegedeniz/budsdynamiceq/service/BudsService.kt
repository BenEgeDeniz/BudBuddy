package com.benegedeniz.budsdynamiceq.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import android.os.Build
import android.widget.RemoteViews
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.benegedeniz.budsdynamiceq.MainActivity
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import com.benegedeniz.budsdynamiceq.rules.RulesEngine
import com.benegedeniz.budsdynamiceq.gesture.GestureActionExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.benegedeniz.budsdynamiceq.gesture.TtsManager
import kotlinx.coroutines.cancel
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController.ImuSide
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.withSign
import kotlin.math.sign
import kotlinx.coroutines.flow.map
import com.benegedeniz.budsdynamiceq.gesture.QuaternionSample
import androidx.glance.appwidget.updateAll

class BudsService : Service() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.benegedeniz.budsdynamiceq.util.LanguageUtils.setLocale(newBase))
    }

    companion object {
        private const val TAG = "BudsService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val rulesEngine = RulesEngine()

    override fun onBind(intent: Intent?): IBinder? = null


    private val transientNotification = MutableStateFlow<Pair<String, String>?>(null)

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<android.bluetooth.BluetoothDevice>(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                }
                if (device != null) {
                    val name = try { device.name } catch (e: SecurityException) { null }
                    if (name != null && name.contains("Buds", ignoreCase = true)) {
                        Log.i(TAG, "Detected Buds connection: $name (${device.address})")
                        val budsController = ServiceLocator.provideBudsController(this@BudsService)
                        if (budsController.savedDeviceMac.value != device.address) {
                            Log.i(TAG, "Automatically switching to newly connected Buds: $name")
                            budsController.connect(device)
                        }
                    }
                }
            }
        }
    }

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "toggleReceiver onReceive action=${intent?.action}")
            if (intent?.action == "com.benegedeniz.budsdynamiceq.TOGGLE_NC") {
                val budsController = ServiceLocator.provideBudsController(this@BudsService)
                val currentNc = budsController.activeNoiseControl.value
                val pL = budsController.placementL.value
                val pR = budsController.placementR.value
                val oneEarbudEnabled = budsController.oneEarbudNoiseControlEnabled.value
                
                val wearingOne = (pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING && pR != com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING) || 
                                 (pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING && pL != com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING)
                                 
                val effectiveModel = budsController.effectiveModel.value
                val nextMode = if (wearingOne && !oneEarbudEnabled) {
                    // ANC is hardware disabled when wearing one earbud unless the setting is on.
                    // Cycle between Transparency and OFF (or just OFF if transparency not supported).
                    if (currentNc == NoiseControlMode.TRANSPARENT) NoiseControlMode.OFF else if (effectiveModel.supportsTransparencyNC) NoiseControlMode.TRANSPARENT else NoiseControlMode.OFF
                } else {
                    if (currentNc == NoiseControlMode.NOISE_CANCELLATION) {
                        if (effectiveModel.supportsTransparencyNC) NoiseControlMode.TRANSPARENT else NoiseControlMode.OFF
                    } else if (currentNc == NoiseControlMode.TRANSPARENT) {
                        NoiseControlMode.NOISE_CANCELLATION
                    } else {
                        NoiseControlMode.NOISE_CANCELLATION
                    }
                }
                
                Log.d(TAG, "Toggle nextMode: $nextMode, wearingOne: $wearingOne, oneEarbudEnabled: $oneEarbudEnabled")
                budsController.sendNoiseControl(nextMode)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        val filter = IntentFilter("com.benegedeniz.budsdynamiceq.TOGGLE_NC")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(toggleReceiver, filter)
        }

        val btFilter = IntentFilter(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
        registerReceiver(bluetoothReceiver, btFilter)

        createNotificationChannel()
        startForeground(1, buildNotification(
            titleText = "Initializing...", 
            ruleNcText = getString(R.string.waiting_for_connection), 
            hardwareNcText = "", 
            lBatteryText = "", 
            rBatteryText = "", 
            isLWorn = false,
            isRWorn = false,
            isConnected = false,
            toggleButtonText = ""
        ))
        
        val budsController = ServiceLocator.provideBudsController(this)
        val rulesRepository = ServiceLocator.provideRulesRepository(this)
        val mediaObserver = ServiceLocator.provideMediaObserver(this)
        val gestureRepo = ServiceLocator.provideGestureRepository(this)
        val wearStateRepo = ServiceLocator.provideWearStateRepository(this)
        val gestureDetector = ServiceLocator.provideGestureDetector(this)
        val noiseDetector = ServiceLocator.provideNoiseDetector(this)
        val actionExecutor = GestureActionExecutor(this, budsController)
        val ttsManager = TtsManager(this)

        budsController.startAutoConnect()
        mediaObserver.startObserving()

        // Sync manual defaults from SharedPreferences
        val prefs = getSharedPreferences("BudsPrefs", MODE_PRIVATE)
        val savedPresetName = prefs.getString("default_preset", null)
        val resolvedPreset = savedPresetName?.let {
            try { EqPreset.valueOf(it) } catch (_: IllegalArgumentException) { null }
        }
        budsController.setManualPreset(resolvedPreset ?: EqPreset.NORMAL)

        val savedNcName = prefs.getString("default_nc", null)
        val resolvedNc = savedNcName?.let {
            try { NoiseControlMode.valueOf(it) } catch (_: IllegalArgumentException) { null }
        }
        budsController.setManualNoiseControl(resolvedNc ?: NoiseControlMode.IGNORE)

        // All runtime-togglable settings are shared via ServiceLocator flows,
        // so BudsService sees changes immediately without any prefs listener.
        ServiceLocator.initFromPrefs(this)
        val headShakeEnabledFlow = ServiceLocator.headShakeEnabled
        val requireBothEarbudsFlow = ServiceLocator.requireBothEarbuds
        val pauseMediaOnConversationFlow = ServiceLocator.pauseMediaOnConversation

        scope.launch {
            gestureRepo.loadGestures()
            wearStateRepo.loadActions()
            
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

        scope.launch {
            var prevL = com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN
            var prevR = com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN
            var initialized = false

            combine(
                budsController.placementL,
                budsController.placementR,
                budsController.isConnected,
                wearStateRepo.actions
            ) { pL, pR, connected, actions ->
                if (!connected) {
                    initialized = false
                    prevL = com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN
                    prevR = com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN
                    return@combine
                }
                
                if (pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN && pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.UNKNOWN) {
                    return@combine
                }

                if (!initialized) {
                    initialized = true
                    prevL = pL
                    prevR = pR
                    return@combine
                }

                val lWearing = pL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                val rWearing = pR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                val prevLWearing = prevL == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
                val prevRWearing = prevR == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING

                val earbudRemoved = (prevLWearing && !lWearing) || (prevRWearing && !rWearing)
                val bothWearing = lWearing && rWearing && (!prevLWearing || !prevRWearing)

                val activeActions = actions.filter { it.enabled }
                
                if (earbudRemoved) {
                    activeActions.filter { it.trigger == com.benegedeniz.budsdynamiceq.data.model.WearStateTrigger.EARBUD_REMOVED }.forEach { action ->
                        scope.launch {
                            delay(300) // Small debounce for hardware settle
                            actionExecutor.execute(action.actions, false)
                        }
                    }
                }
                
                if (bothWearing) {
                    activeActions.filter { it.trigger == com.benegedeniz.budsdynamiceq.data.model.WearStateTrigger.BOTH_WEARING }.forEach { action ->
                        scope.launch {
                            delay(300) // Small debounce for hardware settle
                            actionExecutor.execute(action.actions, false)
                        }
                    }
                }

                prevL = pL
                prevR = pR
            }.collect { }
        }

        scope.launch {
            var activeImu: ImuSide = ImuSide.UNKNOWN
            var handoffJob: Job? = null
            var isHandoffInProgress = false
            var expectedPitchSignFlip = false
            var lastPitch = 0f
            var prevLWearing = budsController.placementL.value == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
            var prevRWearing = budsController.placementR.value == com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING
            var isImuForced = false
            var smoothedZ = 0f
            var longTermVx = 0f
            var lastRawSample: QuaternionSample? = null

            fun getPitch(q: QuaternionSample): Float {
                val sinp = 2 * (q.w * q.y - q.z * q.x)
                return when {
                    abs(sinp) >= 1f -> (Math.PI.toFloat() / 2).withSign(sinp)
                    else -> asin(sinp.toDouble()).toFloat()
                }
            }

            // Monitor pitch signs
            launch {
                budsController.spatialDataFlow.collect { sample ->
                    val currentPitch = getPitch(sample)
                    
                    if (expectedPitchSignFlip && abs(lastPitch) > 0.1f && abs(currentPitch) > 0.1f) {
                        if (currentPitch.sign != lastPitch.sign) {
                            Log.i(TAG, "IMU handoff confirmed by pitch sign flip: $lastPitch -> $currentPitch")
                            expectedPitchSignFlip = false
                        }
                    }
                    lastPitch = currentPitch
                    

                    // Calculate the projection metrics.
                    // hardwareVx = 2*(xz + wy) is the Z projection of World X (Yaw dependent).
                    // hardwareGx = 2*(xz - wy) is the X projection of World Z (Pitch invariant for Buds 2).
                    val hardwareVx = 2 * (sample.rawX * sample.rawZ + sample.rawW * sample.rawY)
                    val hardwareGx = 2 * (sample.rawX * sample.rawZ - sample.rawW * sample.rawY)

                    val isBuds2 = budsController.effectiveModel.value == com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_2 || budsController.effectiveModel.value == com.benegedeniz.budsdynamiceq.bluetooth.BudsModel.BUDS_2_PRO
                    
                    // For Buds 2, Gx is perfectly mirrored (<-0.4 for Left, >0.4 for Right).
                    // For Buds 4 Pro, Gx crosses 0 during Pitch, but Vx is decent when looking forward (~0.4 for Left, ~-0.3 for Right).
                    val detectionMetric = if (isBuds2) hardwareGx else hardwareVx
                    val rightSign = if (isBuds2) -1f else 1f
                    
                    val normalizedMetric = detectionMetric * rightSign

                    smoothedZ = if (smoothedZ == 0f) detectionMetric else (smoothedZ * 0.95f) + (detectionMetric * 0.05f)
                    longTermVx = if (longTermVx == 0f) detectionMetric else (longTermVx * 0.985f) + (detectionMetric * 0.015f)
                    
                    val normalizedLongTermMetric = longTermVx * rightSign

                    if (activeImu == ImuSide.UNKNOWN) {
                        // For Buds 2, threshold is 0.3. For Buds 4 Pro, it's 0.2 (since baseline is ~0.3).
                        val threshold = if (isBuds2) 0.3f else 0.2f
                        if (normalizedMetric > threshold) {
                            Log.i(TAG, "Auto-detected LEFT earbud as primary IMU from baseline metric=\$normalizedMetric")
                            activeImu = ImuSide.LEFT
                            budsController.setActiveImuSide(activeImu, getString(R.string.auto_detected_left_imu))
                            smoothedZ = detectionMetric
                        } else if (normalizedMetric < -threshold) {
                            Log.i(TAG, "Auto-detected RIGHT earbud as primary IMU from baseline metric=\$normalizedMetric")
                            activeImu = ImuSide.RIGHT
                            budsController.setActiveImuSide(activeImu, getString(R.string.auto_detected_right_imu))
                            smoothedZ = detectionMetric
                        }
                    } else if (!isHandoffInProgress && prevLWearing && prevRWearing && !isImuForced) {
                        // A true IMU hijack is a physical swap of sensors that are mounted 180 degrees apart.
                        // This causes an INSTANTANEOUS jump in the quaternion.
                        // A human cannot rotate their head by >90 degrees in 15ms, so if dot product is < 0.5,
                        // it is absolute mathematical proof of a hardware IMU swap, perfectly immune to lying down.
                        val dot = lastRawSample?.let {
                            it.rawX * sample.rawX + it.rawY * sample.rawY + it.rawZ * sample.rawZ + it.rawW * sample.rawW
                        } ?: 1f

                        if (abs(dot) < 0.5f) {
                            if (activeImu == ImuSide.RIGHT && normalizedMetric > 0f) {
                                Log.w(TAG, "Hardware spontaneous IMU hijack to LEFT detected! (dot = \$dot). Correcting.")
                                activeImu = ImuSide.LEFT
                                budsController.setActiveImuSide(activeImu, getString(R.string.hardware_imu_hijack))
                                expectedPitchSignFlip = true
                                longTermVx = detectionMetric // Reset to prevent self-healing from fighting the correction
                            } else if (activeImu == ImuSide.LEFT && normalizedMetric < 0f) {
                                Log.w(TAG, "Hardware spontaneous IMU hijack to RIGHT detected! (dot = \$dot). Correcting.")
                                activeImu = ImuSide.RIGHT
                                budsController.setActiveImuSide(activeImu, getString(R.string.hardware_imu_hijack))
                                expectedPitchSignFlip = true
                                longTermVx = detectionMetric // Reset to prevent self-healing from fighting the correction
                            }
                        }

                        // Subtle drift self-healing. ONLY for Buds 2 because Gx is truly invariant.
                        // For Buds 4 Pro, Vx can cross 0 during extreme Yaw, making this dangerous.
                        if (isBuds2) {
                            if (activeImu == ImuSide.RIGHT && normalizedLongTermMetric > 0.1f) {
                                Log.w(TAG, "Self-healing auto-corrected IMU side to LEFT from normalizedLongTermMetric=\$normalizedLongTermMetric")
                                activeImu = ImuSide.LEFT
                                budsController.setActiveImuSide(activeImu, getString(R.string.self_healing_imu))
                            } else if (activeImu == ImuSide.LEFT && normalizedLongTermMetric < -0.1f) {
                                Log.w(TAG, "Self-healing auto-corrected IMU side to RIGHT from normalizedLongTermMetric=\$normalizedLongTermMetric")
                                activeImu = ImuSide.RIGHT
                                budsController.setActiveImuSide(activeImu, getString(R.string.self_healing_imu))
                            }
                        }
                    }
                    
                    // UNIVERSAL SAFETY NET: Instant absolute correction for impossible metrics.
                    // ONLY safe for Buds 2 because Gx is mathematically rock solid.
                    if (isBuds2 && isImuForced == false) {
                        if (activeImu == ImuSide.RIGHT && normalizedMetric > 0.3f) {
                            Log.w(TAG, "Instant auto-correction to LEFT from normalizedMetric=\$normalizedMetric")
                            activeImu = ImuSide.LEFT
                            isImuForced = false
                            budsController.setActiveImuSide(activeImu, getString(R.string.self_healing_imu))
                        } else if (activeImu == ImuSide.LEFT && normalizedMetric < -0.3f) {
                            Log.w(TAG, "Instant auto-correction to RIGHT from normalizedMetric=\$normalizedMetric")
                            activeImu = ImuSide.RIGHT
                            isImuForced = false
                            budsController.setActiveImuSide(activeImu, getString(R.string.self_healing_imu))
                        }
                    }
                    
                    lastRawSample = sample

                    if (!isHandoffInProgress) {
                        gestureDetector.feedSample(sample)
                        noiseDetector.feedSample(sample)
                    }
                }
            }

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
                    val bL = bL
                    val bR = bR
                    val connected = connected
                }
            }.collect { state ->
                val pL = state.pL
                val pR = state.pR
                val bL = state.bL
                val bR = state.bR
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

                // When an earbud is inserted, the hardware spatial audio engine may pause/reset.
                // The hardware needs a kickstart to re-route the spatial stream if the active IMU is taken out of the ear.
                val wearingChanged = (prevLWearing != lWearing) || (prevRWearing != rWearing)
                if (wearingChanged) {
                    budsController.kickstartSpatialSensor()
                }
                prevLWearing = lWearing
                prevRWearing = rWearing

                if (lWearing && !rWearing) {
                    isImuForced = true
                    budsController.setActiveImuSide(ImuSide.LEFT, getString(R.string.only_left_earbud_worn))
                    if (activeImu != ImuSide.LEFT) {
                        activeImu = ImuSide.LEFT
                        expectedPitchSignFlip = true
                    }
                    handoffJob?.cancel()
                    handoffJob = null
                    isHandoffInProgress = false
                } else if (rWearing && !lWearing) {
                    isImuForced = true
                    budsController.setActiveImuSide(ImuSide.RIGHT, getString(R.string.only_right_earbud_worn))
                    if (activeImu != ImuSide.RIGHT) {
                        activeImu = ImuSide.RIGHT
                        expectedPitchSignFlip = true
                    }
                    handoffJob?.cancel()
                    handoffJob = null
                    isHandoffInProgress = false
                } else if (lWearing && rWearing) {
                    isImuForced = false
                    // Both are worn. Samsung's hardware rules for switching are complex (battery, fresh out of case, etc).
                    // We must rely strictly on the IMU data stream's physical signature to track what Samsung chose.
                    when (activeImu) {
                        ImuSide.UNKNOWN -> {
                            // Defer activeImu selection to the spatial data flow heuristic!
                        }
                        ImuSide.RIGHT -> {
                            budsController.setActiveImuSide(ImuSide.RIGHT, getString(R.string.both_worn_heuristics))
                        }
                        ImuSide.LEFT -> {
                            budsController.setActiveImuSide(ImuSide.LEFT, getString(R.string.both_worn_heuristics))
                        }
                    }
                } else {
                    isImuForced = false
                    // Neither are worn. Continue as normal.
                    when (activeImu) {
                        ImuSide.UNKNOWN -> {
                            // Do nothing
                        }
                        ImuSide.RIGHT -> {
                            // Already RIGHT, none worn. Do nothing.
                        }
                        ImuSide.LEFT -> {
                            // Already LEFT, none worn. Do nothing.
                        }
                    }
                }
            }
        }



        scope.launch {
            gestureDetector.detectedGesture.collect { gesture ->
                try {
                    if (gestureDetector.isTrainingMode) return@collect
                    transientNotification.value = getString(R.string.service_gesture_detected) to getString(R.string.service_flow_sequence, gesture.name)
                    actionExecutor.execute(gesture.actions, gesture.playChime)
                    transientNotification.value = getString(R.string.service_gesture_detected) to getString(R.string.service_flow_complete, gesture.name)
                    delay(1000)
                    transientNotification.value = null
                } catch (e: Exception) {
                    e.printStackTrace()
                    transientNotification.value = null
                }
            }
        }

        // Handle noise detection notifications from NoiseDetector
        scope.launch {
            noiseDetector.noiseDetected.collect { noiseProfile ->
                try {
                    if (gestureDetector.isTrainingMode) return@collect
                    transientNotification.value = getString(R.string.service_movement_cancelled) to getString(R.string.service_filtering_noise, noiseProfile.name)
                    delay(1000)
                    transientNotification.value = null
                } catch (e: Exception) {
                    e.printStackTrace()
                    transientNotification.value = null
                }
            }
        }

        scope.launch {
            var previousNcMode: NoiseControlMode? = null
            combine(
                budsController.activeNoiseControl,
                pauseMediaOnConversationFlow
            ) { ncMode, pauseOnTransparency ->
                Pair(ncMode, pauseOnTransparency)
            }.collect { (ncMode, pauseOnTransparency) ->
                if (pauseOnTransparency && previousNcMode != null && ncMode != previousNcMode) {
                    if (ncMode == NoiseControlMode.TRANSPARENT) {
                        actionExecutor.triggerPause()
                    }
                }
                previousNcMode = ncMode
            }
        }

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
            
            // activeNoiseControl is intentionally NOT in deviceStateFlow.
            // Including it caused a feedback loop: every sendNoiseControl() call set
            // _activeNoiseControl, which re-triggered this combine, which re-evaluated
            // defaultChanged and re-sent the manual NC mode — fighting the user's tap.
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

            // Notification display only — activeNc is read here but does NOT drive rule logic.
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
                    kotlinx.coroutines.delay(200L) // Debounce rapid state changes like battery
                    try {
                        val manager = androidx.glance.appwidget.GlanceAppWidgetManager(this@BudsService)
                        val widget = com.benegedeniz.budsdynamiceq.ui.widget.NoiseControlWidget()
                        manager.getGlanceIds(widget.javaClass).forEach { glanceId ->
                            androidx.glance.appwidget.state.updateAppWidgetState(this@BudsService, glanceId) { prefs ->
                                val key = androidx.datastore.preferences.core.booleanPreferencesKey("force_update")
                                val current = prefs[key] ?: false
                                prefs[key] = !current
                            }
                            widget.update(this@BudsService, glanceId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update widget", e)
                    }
                }
            }

            combine(
                transientNotification,
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
                    updateNotification(
                        titleText = "Disconnected", 
                        ruleNcText = getString(R.string.waiting_for_buds), 
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
                    
                    activeRuleTitle = getString(R.string.active_rule, matchingRule.keyword)
                    activeRuleText = getString(R.string.settings_format, eqToSend?.let { getString(it.displayNameRes) } ?: getString(R.string.none), ncToSend?.let { getString(it.displayNameRes) } ?: getString(R.string.none))
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
                    
                    activeRuleTitle = getString(R.string.default_settings)
                    activeRuleText = getString(R.string.settings_format, manualEq?.let { getString(it.displayNameRes) } ?: getString(R.string.none), manualNc?.let { getString(it.displayNameRes) } ?: getString(R.string.none))
                }
                
                val lText = "${state.deviceState.bL}%"
                val rText = "${state.deviceState.bR}%"
                
                val wearingOne = (isLWorn && !isRWorn) || (isRWorn && !isLWorn)
                val toggleText = if (wearingOne && !state.deviceState.oneEarbudEnabled) {
                    getString(R.string.toggle_off_ambient)
                } else if (!budsController.effectiveModel.value.supportsTransparencyNC) {
                    getString(R.string.toggle_anc_off)
                } else {
                    getString(R.string.toggle_anc_ambient)
                }
                val hardwareNcText = getString(R.string.active_nc_format, state.activeNc?.let { getString(it.displayNameRes) } ?: getString(R.string.unknown))

                if (state.transient != null) {
                    updateNotification(
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
                    updateNotification(
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "buds_service_channel",
                getString(R.string.app_name_service),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        titleText: String, 
        ruleNcText: String,
        hardwareNcText: String,
        lBatteryText: String,
        rBatteryText: String,
        isLWorn: Boolean,
        isRWorn: Boolean,
        isConnected: Boolean,
        toggleButtonText: String
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent("com.benegedeniz.budsdynamiceq.TOGGLE_NC").apply {
            setPackage(packageName)
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val expandedView = RemoteViews(packageName, R.layout.notification_expanded).apply {
            setTextViewText(R.id.notification_title, titleText)
            setTextViewText(R.id.notification_rule_state, ruleNcText)
            
            if (hardwareNcText.isNotEmpty()) {
                setViewVisibility(R.id.notification_hardware_state, android.view.View.VISIBLE)
                setTextViewText(R.id.notification_hardware_state, hardwareNcText)
            } else {
                setViewVisibility(R.id.notification_hardware_state, android.view.View.GONE)
            }
            
            if (isLWorn) {
                setViewVisibility(R.id.notification_l_container, android.view.View.VISIBLE)
                setTextViewText(R.id.notification_l_status, lBatteryText)
            } else {
                setViewVisibility(R.id.notification_l_container, android.view.View.GONE)
            }
            
            if (isRWorn) {
                setViewVisibility(R.id.notification_r_container, android.view.View.VISIBLE)
                setTextViewText(R.id.notification_r_status, rBatteryText)
            } else {
                setViewVisibility(R.id.notification_r_container, android.view.View.GONE)
            }
            
            setTextViewText(R.id.notification_toggle_button, toggleButtonText)
            setOnClickPendingIntent(R.id.notification_toggle_button, togglePendingIntent)
        }

        val collapsedTitle = if (isConnected) getString(R.string.connected) else getString(R.string.disconnected)
        val collapsedText = getString(R.string.expand_for_more)

        return NotificationCompat.Builder(this, "buds_service_channel")
            .setSmallIcon(R.mipmap.ic_launcher) 
            .setContentTitle(collapsedTitle)
            .setContentText(collapsedText)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(expandedView)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(
        titleText: String, 
        ruleNcText: String,
        hardwareNcText: String,
        lBatteryText: String,
        rBatteryText: String,
        isLWorn: Boolean,
        isRWorn: Boolean,
        isConnected: Boolean,
        toggleButtonText: String
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, buildNotification(titleText, ruleNcText, hardwareNcText, lBatteryText, rBatteryText, isLWorn, isRWorn, isConnected, toggleButtonText))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // If the user swipes the app away from recents, the UI is killed but the service stays alive.
        // We MUST reset training mode, otherwise it gets permanently stuck if they swiped away while recording.
        val gestureDetector = ServiceLocator.provideGestureDetector(this)
        gestureDetector.isTrainingMode = false
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(toggleReceiver)
        unregisterReceiver(bluetoothReceiver)
        scope.cancel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(1)
        
        val mediaObserver = ServiceLocator.provideMediaObserver(this)
        val budsController = ServiceLocator.provideBudsController(this)
        mediaObserver.stopObserving()
        budsController.disconnect(forget = false)
    }
}
