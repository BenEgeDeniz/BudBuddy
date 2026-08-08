package com.benegedeniz.budsdynamiceq.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import com.benegedeniz.budsdynamiceq.gesture.GestureActionExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.benegedeniz.budsdynamiceq.gesture.TtsManager

class BudsService : Service() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.benegedeniz.budsdynamiceq.util.LanguageUtils.setLocale(newBase))
    }

    companion object {
        private const val TAG = "BudsService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val transientNotificationFlow = MutableStateFlow<Pair<String, String>?>(null)
    
    private lateinit var notificationManagerHelper: NotificationManagerHelper

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
                        Log.i(TAG, "Detected Buds connection: \$name (\${device.address})")
                        val budsController = ServiceLocator.provideBudsController(this@BudsService)
                        if (budsController.savedDeviceMac.value != device.address) {
                            Log.i(TAG, "Automatically switching to newly connected Buds: \$name")
                            budsController.connect(device)
                        }
                    }
                }
            }
        }
    }

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "toggleReceiver onReceive action=\${intent?.action}")
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
                
                Log.d(TAG, "Toggle nextMode: \$nextMode, wearingOne: \$wearingOne, oneEarbudEnabled: \$oneEarbudEnabled")
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

        notificationManagerHelper = NotificationManagerHelper(this)
        notificationManagerHelper.createNotificationChannel()
        startForeground(1, notificationManagerHelper.buildNotification(
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

        ServiceLocator.initFromPrefs(this)
        val headShakeEnabledFlow = ServiceLocator.headShakeEnabled
        val requireBothEarbudsFlow = ServiceLocator.requireBothEarbuds
        val pauseMediaOnConversationFlow = ServiceLocator.pauseMediaOnConversation

        scope.launch {
            gestureRepo.loadGestures()
            wearStateRepo.loadActions()
        }

        val gestureCoordinator = GestureCoordinator(
            scope = scope,
            budsController = budsController,
            gestureRepo = gestureRepo,
            gestureDetector = gestureDetector,
            noiseDetector = noiseDetector,
            headShakeEnabledFlow = headShakeEnabledFlow,
            requireBothEarbudsFlow = requireBothEarbudsFlow
        )
        gestureCoordinator.start()

        val wearStateManager = WearStateManager(
            scope = scope,
            budsController = budsController,
            wearStateRepo = wearStateRepo,
            actionExecutor = actionExecutor
        )
        wearStateManager.start()

        val imuManager = ImuManager(
            context = this,
            scope = scope,
            budsController = budsController,
            gestureDetector = gestureDetector,
            noiseDetector = noiseDetector
        )
        imuManager.start()

        val rulesCoordinator = RulesCoordinator(
            context = this,
            scope = scope,
            budsController = budsController,
            mediaObserver = mediaObserver,
            rulesRepository = rulesRepository,
            transientNotificationFlow = transientNotificationFlow,
            notificationManagerHelper = notificationManagerHelper
        )
        rulesCoordinator.start()

        // Handle gesture detection feedback
        scope.launch {
            gestureDetector.detectedGesture.collect { gesture ->
                try {
                    if (gestureDetector.isTrainingMode) return@collect
                    transientNotificationFlow.value = getString(R.string.service_gesture_detected) to getString(R.string.service_flow_sequence, gesture.name)
                    actionExecutor.execute(gesture.actions, gesture.playChime)
                    transientNotificationFlow.value = getString(R.string.service_gesture_detected) to getString(R.string.service_flow_complete, gesture.name)
                    delay(1000)
                    transientNotificationFlow.value = null
                } catch (e: Exception) {
                    e.printStackTrace()
                    transientNotificationFlow.value = null
                }
            }
        }

        // Handle noise detection feedback
        scope.launch {
            noiseDetector.noiseDetected.collect { noiseProfile ->
                try {
                    if (gestureDetector.isTrainingMode) return@collect
                    transientNotificationFlow.value = getString(R.string.service_movement_cancelled) to getString(R.string.service_filtering_noise, noiseProfile.name)
                    delay(1000)
                    transientNotificationFlow.value = null
                } catch (e: Exception) {
                    e.printStackTrace()
                    transientNotificationFlow.value = null
                }
            }
        }

        // Handle conversation mode (pause on transparency)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
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
        
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.cancel(1)
        
        val mediaObserver = ServiceLocator.provideMediaObserver(this)
        val budsController = ServiceLocator.provideBudsController(this)
        mediaObserver.stopObserving()
        budsController.disconnect(forget = false)
    }
}
