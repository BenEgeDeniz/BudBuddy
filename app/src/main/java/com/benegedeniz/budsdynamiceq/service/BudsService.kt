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

    companion object {
        private const val TAG = "BudsService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val rulesEngine = RulesEngine()

    override fun onBind(intent: Intent?): IBinder? = null

    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val transientNotification = MutableStateFlow<Pair<String, String>?>(null)

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
                                 
                val nextMode = if (wearingOne && !oneEarbudEnabled) {
                    if (currentNc == NoiseControlMode.TRANSPARENT) NoiseControlMode.OFF else NoiseControlMode.TRANSPARENT
                } else {
                    if (currentNc == NoiseControlMode.NOISE_CANCELLATION) NoiseControlMode.TRANSPARENT else NoiseControlMode.NOISE_CANCELLATION
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

        createNotificationChannel()
        startForeground(1, buildNotification(
            titleText = "Initializing...", 
            ruleNcText = "Waiting for connection...", 
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
        val gestureDetector = ServiceLocator.provideGestureDetector(this)
        val noiseDetector = ServiceLocator.provideNoiseDetector(this)
        val actionExecutor = GestureActionExecutor(this, budsController)
        val ttsManager = TtsManager(this)

        budsController.startAutoConnect()
        mediaObserver.startObserving()

        // Sync manual defaults from SharedPreferences
        val prefs = getSharedPreferences("BudsPrefs", MODE_PRIVATE)
        val savedPresetName = prefs.getString("default_preset", null)
        if (savedPresetName != null) {
            budsController.setManualPreset(EqPreset.valueOf(savedPresetName))
        } else {
            budsController.setManualPreset(EqPreset.NORMAL)
        }

        val savedNcName = prefs.getString("default_nc", null)
        if (savedNcName != null) {
            budsController.setManualNoiseControl(NoiseControlMode.valueOf(savedNcName))
        } else {
            budsController.setManualNoiseControl(NoiseControlMode.IGNORE)
        }

        val headShakeEnabledFlow = MutableStateFlow(prefs.getBoolean("head_shake_enabled", false))
        val requireBothEarbudsFlow = MutableStateFlow(prefs.getBoolean("require_both_earbuds", false))
        val pauseMediaOnConversationFlow = MutableStateFlow(prefs.getBoolean("pause_media_on_conversation", false))
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "head_shake_enabled") {
                headShakeEnabledFlow.value = sharedPreferences.getBoolean("head_shake_enabled", false)
            } else if (key == "require_both_earbuds") {
                requireBothEarbudsFlow.value = sharedPreferences.getBoolean("require_both_earbuds", false)
            } else if (key == "pause_media_on_conversation") {
                pauseMediaOnConversationFlow.value = sharedPreferences.getBoolean("pause_media_on_conversation", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        scope.launch {
            gestureRepo.loadGestures()
            
            val wearingFlow = combine(
                budsController.placementL,
                budsController.placementR,
                requireBothEarbudsFlow
            ) { pL, pR, requireBoth ->
                if (requireBoth) {
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
            var activeImu: ImuSide = ImuSide.UNKNOWN
            var handoffJob: Job? = null
            var isHandoffInProgress = false
            var expectedPitchSignFlip = false
            var lastPitch = 0f
            var prevLWearing = false
            var prevRWearing = false
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
                    

                    // Calculate the local X projection of the world Z axis (Gravity).
                    // vx = 2 * (x*z + w*y)
                    // This value is mathematically INVARIANT to Yaw (turning around) and Pitch (looking up/down)!
                    // Furthermore, because invertPitch negates BOTH X and Z, (-x)*(-z) = x*z, meaning this value 
                    // is completely unaffected by our software inversion state. It is an absolute hardware truth!
                    // Right Earbud ≈ -0.68
                    // Left Earbud ≈ +0.69
                    val hardwareVx = 2 * (sample.x * sample.z + sample.w * sample.y)
                    smoothedZ = if (smoothedZ == 0f) hardwareVx else (smoothedZ * 0.95f) + (hardwareVx * 0.05f)
                    
                    // Alpha 0.015f gives roughly a 1-second time constant at 15ms sampling rate.
                    // This is slow enough to ignore quick head shakes/nods, but fast enough to auto-correct
                    // a bad initial guess shortly after the user looks straight ahead.
                    longTermVx = if (longTermVx == 0f) hardwareVx else (longTermVx * 0.985f) + (hardwareVx * 0.015f)
                    
                    if (activeImu == ImuSide.UNKNOWN) {
                        // require very strong baseline to avoid false detection if user is looking down when app opens.
                        // Right earbud upright is -0.68. If they look down 15 degrees it's -0.48.
                        if (hardwareVx > 0.55f) {
                            Log.i(TAG, "Auto-detected LEFT earbud as primary IMU from baseline vx=\$hardwareVx")
                            activeImu = ImuSide.LEFT
                            budsController.setActiveImuSide(activeImu, "Auto-detected LEFT earbud as primary IMU")
                            smoothedZ = hardwareVx
                        } else if (hardwareVx < -0.55f) {
                            Log.i(TAG, "Auto-detected RIGHT earbud as primary IMU from baseline vx=\$hardwareVx")
                            activeImu = ImuSide.RIGHT
                            budsController.setActiveImuSide(activeImu, "Auto-detected RIGHT earbud as primary IMU")
                            smoothedZ = hardwareVx
                        }
                    } else if (!isHandoffInProgress && prevLWearing && prevRWearing && !isImuForced) {
                        // A true IMU hijack is a physical swap of sensors that are mounted 180 degrees apart.
                        // This causes an INSTANTANEOUS jump in the quaternion.
                        // A human cannot rotate their head by >90 degrees in 15ms, so if dot product is < 0.5,
                        // it is absolute mathematical proof of a hardware IMU swap, perfectly immune to lying down.
                        val dot = lastRawSample?.let {
                            it.x * sample.x + it.y * sample.y + it.z * sample.z + it.w * sample.w
                        } ?: 1f

                        if (abs(dot) < 0.5f) {
                            if (activeImu == ImuSide.RIGHT && hardwareVx > 0f) {
                                Log.w(TAG, "Hardware spontaneous IMU hijack to LEFT detected! (dot = \$dot). Correcting.")
                                activeImu = ImuSide.LEFT
                                budsController.setActiveImuSide(activeImu, "Hardware spontaneous IMU hijack detected")
                                expectedPitchSignFlip = true
                                longTermVx = hardwareVx // Reset to prevent self-healing from fighting the correction
                            } else if (activeImu == ImuSide.LEFT && hardwareVx < 0f) {
                                Log.w(TAG, "Hardware spontaneous IMU hijack to RIGHT detected! (dot = \$dot). Correcting.")
                                activeImu = ImuSide.RIGHT
                                budsController.setActiveImuSide(activeImu, "Hardware spontaneous IMU hijack detected")
                                expectedPitchSignFlip = true
                                longTermVx = hardwareVx // Reset to prevent self-healing from fighting the correction
                            }
                        }

                        // Subtle drift self-healing
                        if (activeImu == ImuSide.RIGHT && longTermVx > 0.1f) {
                            Log.w(TAG, "Self-healing auto-corrected IMU side to LEFT from longTermVx=\$longTermVx")
                            activeImu = ImuSide.LEFT
                            budsController.setActiveImuSide(activeImu, "Self-healing auto-corrected IMU side")
                        } else if (activeImu == ImuSide.LEFT && longTermVx < -0.1f) {
                            Log.w(TAG, "Self-healing auto-corrected IMU side to RIGHT from longTermVx=\$longTermVx")
                            activeImu = ImuSide.RIGHT
                            budsController.setActiveImuSide(activeImu, "Self-healing auto-corrected IMU side")
                        }
                    }
                    
                    // UNIVERSAL SAFETY NET: Instant absolute correction for impossible hardwareVx
                    // This is disabled when isImuForced is true (e.g. only one bud worn) to prevent false positives when lying down sideways.
                    if (activeImu == ImuSide.RIGHT && hardwareVx > 0.3f && isImuForced == false) {
                        Log.w(TAG, "Instant auto-correction to LEFT from hardwareVx=\$hardwareVx")
                        activeImu = ImuSide.LEFT
                        isImuForced = false
                        budsController.setActiveImuSide(activeImu, "Self-healing auto-corrected IMU side")
                    } else if (activeImu == ImuSide.LEFT && hardwareVx < -0.3f && isImuForced == false) {
                        Log.w(TAG, "Instant auto-correction to RIGHT from hardwareVx=\$hardwareVx")
                        activeImu = ImuSide.RIGHT
                        isImuForced = false
                        budsController.setActiveImuSide(activeImu, "Self-healing auto-corrected IMU side")
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
                    budsController.setActiveImuSide(ImuSide.LEFT, "Only left earbud is worn")
                    if (activeImu != ImuSide.LEFT) {
                        activeImu = ImuSide.LEFT
                        expectedPitchSignFlip = true
                    }
                    handoffJob?.cancel()
                    handoffJob = null
                    isHandoffInProgress = false
                } else if (rWearing && !lWearing) {
                    isImuForced = true
                    budsController.setActiveImuSide(ImuSide.RIGHT, "Only right earbud is worn")
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
                            budsController.setActiveImuSide(ImuSide.RIGHT, "Both worn. Relying on IMU heuristics.")
                        }
                        ImuSide.LEFT -> {
                            budsController.setActiveImuSide(ImuSide.LEFT, "Both worn. Relying on IMU heuristics.")
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
                    transientNotification.value = "Gesture Detected" to "\"${gesture.name}\" → Flow sequence"
                    actionExecutor.execute(gesture.actions, gesture.playChime)
                    transientNotification.value = "Gesture Detected" to "\"${gesture.name}\" → Flow complete"
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
                    transientNotification.value = "Movement Cancelled" to "Filtering noise: \"${noiseProfile.name}\""
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
            ) { ncMode, pauseMedia ->
                Pair(ncMode, pauseMedia)
            }.collect { (ncMode, pauseMedia) ->
                if (pauseMedia && previousNcMode != null && ncMode != previousNcMode) {
                    if (previousNcMode == NoiseControlMode.NOISE_CANCELLATION && ncMode == NoiseControlMode.TRANSPARENT) {
                        actionExecutor.triggerPause()
                    } else if (previousNcMode == NoiseControlMode.TRANSPARENT && ncMode == NoiseControlMode.NOISE_CANCELLATION) {
                        actionExecutor.triggerPlay()
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
            
            val deviceStateFlow = combine(
                budsController.isConnected,
                batteryPlacementFlow,
                budsController.oneEarbudNoiseControlEnabled,
                budsController.activeNoiseControl
            ) { connected, bp, oneEarbudEnabled, activeNc ->
                object {
                    val connected = connected
                    val bL = bp.bL
                    val bR = bp.bR
                    val pL = bp.pL
                    val pR = bp.pR
                    val oneEarbudEnabled = oneEarbudEnabled
                    val activeNc = activeNc
                }
            }
            
            scope.launch {
                deviceStateFlow.collectLatest {
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
                deviceStateFlow
            ) { transient, ruleState, deviceState ->
                object {
                    val transient = transient
                    val ruleState = ruleState
                    val deviceState = deviceState
                }
            }.collect { state ->
                val connected = state.deviceState.connected
                
                if (!connected) {
                    wasConnected = false
                    updateNotification(
                        titleText = "Disconnected", 
                        ruleNcText = "Waiting for Buds...", 
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
                    
                    activeRuleTitle = "Active Rule: ${matchingRule.keyword}"
                    activeRuleText = "Settings: ${eqToSend?.displayName ?: "None"} | ${ncToSend?.displayName ?: "None"}"
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
                    
                    activeRuleTitle = "Default Settings"
                    activeRuleText = "Settings: ${manualEq?.displayName ?: "None"} | ${manualNc?.displayName ?: "None"}"
                }
                
                val lText = "${state.deviceState.bL}%"
                val rText = "${state.deviceState.bR}%"
                
                val wearingOne = (isLWorn && !isRWorn) || (isRWorn && !isLWorn)
                val toggleText = if (wearingOne && !state.deviceState.oneEarbudEnabled) "Toggle Off / Ambient" else "Toggle ANC / Ambient"
                val hardwareNcText = "Active NC: ${state.deviceState.activeNc?.displayName ?: "Unknown"}"

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
                "Bud Buddy Service",
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

        val collapsedTitle = if (isConnected) "Bud Buddy Connected" else "Bud Buddy Disconnected"
        val collapsedText = "Expand for more"

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
