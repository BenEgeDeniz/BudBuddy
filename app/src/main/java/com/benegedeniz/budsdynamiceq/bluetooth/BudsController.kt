package com.benegedeniz.budsdynamiceq.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.EqRule
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.model.FitTestResult
import com.benegedeniz.budsdynamiceq.data.repository.DeviceStateRepository
import com.benegedeniz.budsdynamiceq.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.util.UUID

enum class BudsModel(@androidx.annotation.StringRes val displayNameRes: Int) {
    BUDS_2(com.benegedeniz.budsdynamiceq.R.string.model_buds_2),
    BUDS_2_PRO(com.benegedeniz.budsdynamiceq.R.string.model_buds_2_pro),
    BUDS_3(com.benegedeniz.budsdynamiceq.R.string.model_buds_3),
    BUDS_3_PRO(com.benegedeniz.budsdynamiceq.R.string.model_buds_3_pro),
    BUDS_4_PRO(com.benegedeniz.budsdynamiceq.R.string.model_buds_4_pro),
    BUDS_4(com.benegedeniz.budsdynamiceq.R.string.model_buds_4),
    UNKNOWN(com.benegedeniz.budsdynamiceq.R.string.model_buds_unknown);
    
    val supportsAdaptiveNC: Boolean get() = this != BUDS_2 && this != BUDS_2_PRO && this != BUDS_3 && this != BUDS_4
    val supportsTransparencyNC: Boolean get() = this != BUDS_3 && this != BUDS_4
    val supportsConversationDetection: Boolean get() = this != BUDS_2 && this != BUDS_3 && this != BUDS_4
    val supportsFitTest: Boolean get() = this != BUDS_3 && this != BUDS_4
    val isExperimentalGestures: Boolean get() = this != BUDS_4_PRO && this != BUDS_4 && this != BUDS_2 && this != BUDS_2_PRO
    val supportsFmgRingWhileWearing: Boolean get() = this == BUDS_2 || this == BUDS_2_PRO
    val supportsDoubleTapEdge: Boolean get() = this == BUDS_2 || this == BUDS_2_PRO
}

class BudsController(
    private val context: Context,
    val deviceState: DeviceStateRepository,
    val settingsRepo: SettingsRepository
) {
    companion object {
        private const val TAG = "BudsController"
        val BUDS_SPP_UUID: UUID = UUID.fromString("2e73a4ad-332d-41fc-90e2-16bef06523f2")
    }

    private val bluetoothManager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val packetParser = BudsPacketParser(deviceState, settingsRepo, this)

    val isConnected = deviceState.isConnected.asStateFlow()
    val isConnecting = deviceState.isConnecting.asStateFlow()
    val savedDeviceMac = deviceState.savedDeviceMac.asStateFlow()
    val isSpatialActive = deviceState.isSpatialActive.asStateFlow()
    val isHomePageVisible = deviceState.isHomePageVisible
    val connectedModel = deviceState.connectedModel.asStateFlow()
    val modelOverride = deviceState.modelOverride.asStateFlow()
    
    val effectiveModel = deviceState.modelOverride
        .combine(deviceState.connectedModel) { override, detected -> override ?: detected }
        .stateIn(scope, SharingStarted.Eagerly, deviceState.modelOverride.value ?: BudsModel.UNKNOWN)

    val activeImuSide = deviceState.activeImuSide.asStateFlow()
    val activeImuReason = deviceState.activeImuReason.asStateFlow()

    enum class ImuSide { LEFT, RIGHT, UNKNOWN }

    val invertPitch = combine(deviceState.activeImuSide, effectiveModel) { side, model ->
        if (model == BudsModel.BUDS_2 || model == BudsModel.BUDS_2_PRO) {
            side == ImuSide.RIGHT
        } else {
            side == ImuSide.LEFT
        }
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val spatialDataFlow = deviceState.spatialDataFlow.asSharedFlow()
    val rawSpatialDataFlow = deviceState.rawSpatialDataFlow.asSharedFlow()
    val batteryL = deviceState.batteryL.asStateFlow()
    val batteryR = deviceState.batteryR.asStateFlow()
    val batteryCase = deviceState.batteryCase.asStateFlow()
    val chargingL = deviceState.chargingL.asStateFlow()
    val chargingR = deviceState.chargingR.asStateFlow()
    val chargingCase = deviceState.chargingCase.asStateFlow()
    val temperatureL = deviceState.temperatureL.asStateFlow()
    val temperatureR = deviceState.temperatureR.asStateFlow()
    val placementL = deviceState.placementL.asStateFlow()
    val placementR = deviceState.placementR.asStateFlow()
    val fitTestResultL = deviceState.fitTestResultL.asStateFlow()
    val fitTestResultR = deviceState.fitTestResultR.asStateFlow()
    val isFitTestScreenOpen = deviceState.isFitTestScreenOpen.asStateFlow()
    
    val isSearching = deviceState.isSearching.asStateFlow()
    val isLeftMuted = deviceState.isLeftMuted.asStateFlow()
    val isRightMuted = deviceState.isRightMuted.asStateFlow()

    val conversationDetectionEnabled = deviceState.conversationDetectionEnabled.asStateFlow()
    val oneEarbudNoiseControlEnabled = deviceState.oneEarbudNoiseControlEnabled.asStateFlow()
    val useAmbientSoundDuringCalls = deviceState.useAmbientSoundDuringCalls.asStateFlow()
    val inEarDetectionForCalls = deviceState.inEarDetectionForCalls.asStateFlow()
    val doubleTapEdgeEnabled = deviceState.doubleTapEdgeEnabled.asStateFlow()
    val stereoBalance = deviceState.stereoBalance.asStateFlow()
    val lastMatchedRule = deviceState.lastMatchedRule.asStateFlow()
    val manualPreset = deviceState.manualPreset.asStateFlow()
    val manualNoiseControl = deviceState.manualNoiseControl.asStateFlow()
    val activeNoiseControl = deviceState.activeNoiseControl.asStateFlow()

    private var targetDevice: BluetoothDevice? = null
    class QueuedPacket(val data: ByteArray, val isNc: Boolean = false, val ncMode: NoiseControlMode? = null)
    private val _packetQueue = Channel<QueuedPacket>(Channel.UNLIMITED)
    private val packetQueue = object {
        fun trySend(data: ByteArray) = _packetQueue.trySend(QueuedPacket(data))
    }

    private var keepAliveJob: Job? = null
    private var lastConnectedTime = 0L

    // NC and EQ state
    private var lastEqSendTimestamp: Long = 0
    private var lastSentEq: EqPreset? = null
    private var lastSentNcMode: NoiseControlMode? = null
    private var lastNcSendTimestamp: Long = 0
    @Volatile private var lastConfirmedNcMode: NoiseControlMode? = null
    private var ncRetryJob: Job? = null
    val lastAppNcSendTimestamp: Long get() = lastNcSendTimestamp

    init {
        deviceState.savedDeviceMac.value = settingsRepo.getSavedMacAddress()
        deviceState.connectedModel.value = settingsRepo.getDetectedModel(deviceState.savedDeviceMac.value ?: "")
        deviceState.modelOverride.value = settingsRepo.getModelOverride(deviceState.savedDeviceMac.value ?: "")
        
        scope.launch(Dispatchers.IO) {
            for (packet in _packetQueue) {
                if (packet.isNc && packet.ncMode != lastSentNcMode) {
                    Log.i(TAG, "Skipping obsolete NC packet: ${packet.ncMode}")
                    continue
                }
                try {
                    socket?.outputStream?.write(packet.data)
                    delay(250) // Hardware processing buffer time
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send queued packet: ${e.message}")
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (deviceState.isConnected.value) {
                    if (!deviceState.isSpatialActive.value || deviceState.isHomePageVisible.value) {
                        packetQueue.trySend(SppPacketEncoder.buildPacket(0x26.toByte(), byteArrayOf())) 
                    }
                }
                delay(15000)
            }
        }
    }

    fun startFitTest() {
        val payload = byteArrayOf(1)
        sendSppPacket(157.toByte(), payload)
        deviceState.fitTestResultL.value = FitTestResult.UNKNOWN
        deviceState.fitTestResultR.value = FitTestResult.UNKNOWN
    }

    fun stopFitTest() {
        val payload = byteArrayOf(0)
        sendSppPacket(157.toByte(), payload)
    }

    fun startFindMyEarbuds(ringWhileWearing: Boolean) {
        val msgId = if (ringWhileWearing) 166.toByte() else 160.toByte()
        sendSppPacket(msgId, byteArrayOf())
        deviceState.isSearching.value = true
        deviceState.isLeftMuted.value = false
        deviceState.isRightMuted.value = false
    }

    fun stopFindMyEarbuds() {
        sendSppPacket(161.toByte(), byteArrayOf())
        deviceState.isSearching.value = false
    }

    fun muteEarbud(leftMuted: Boolean, rightMuted: Boolean) {
        val payload = byteArrayOf(
            (if (leftMuted) 1 else 0).toByte(),
            (if (rightMuted) 1 else 0).toByte()
        )
        sendSppPacket(162.toByte(), payload)
        deviceState.isLeftMuted.value = leftMuted
        deviceState.isRightMuted.value = rightMuted
    }

    private fun sendSppPacket(msgId: Byte, payload: ByteArray) {
        val packet = SppPacketEncoder.buildPacket(msgId, payload)
        packetQueue.trySend(packet)
    }

    private fun disableHardwareAutoPause() {
        sendSppPacket(SppPacketEncoder.MSG_ID_PAUSE_MEDIA_WHEN_ONE_BUD_REMOVED, byteArrayOf(0))
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun setModelOverride(model: BudsModel?) {
        deviceState.modelOverride.value = model
        val mac = deviceState.savedDeviceMac.value
        if (mac != null) {
            settingsRepo.saveModelOverride(mac, model)
        }
    }

    fun setActiveImuSide(side: ImuSide, reason: String? = null) {
        deviceState.activeImuSide.value = side
        if (reason != null) {
            deviceState.activeImuReason.value = reason
        }
    }

    fun setFitTestScreenOpen(isOpen: Boolean) {
        deviceState.isFitTestScreenOpen.value = isOpen
        if (!isOpen) {
            deviceState.fitTestResultL.value = FitTestResult.UNKNOWN
            deviceState.fitTestResultR.value = FitTestResult.UNKNOWN
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return emptyList()
        return bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun startAutoConnect() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or available.")
            return
        }

        if (deviceState.isConnected.value || deviceState.isConnecting.value || connectionJob?.isActive == true) {
            Log.i(TAG, "Already connected or connecting, skipping auto-connect.")
            return
        }

        val savedMac = settingsRepo.getSavedMacAddress()
        if (savedMac != null) {
            try {
                val target = bluetoothAdapter.getRemoteDevice(savedMac)
                Log.i(TAG, "Auto-connecting to: ${target.address}")
                connect(target)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get remote device: ${e.message}")
            }
        } else {
            Log.w(TAG, "No saved device found. Waiting for user selection.")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        settingsRepo.saveMacAddress(device.address)
        deviceState.savedDeviceMac.value = device.address
        targetDevice = device

        var savedModel = settingsRepo.getDetectedModel(device.address)
        if (savedModel == BudsModel.UNKNOWN) {
            @SuppressLint("MissingPermission")
            val name = device.name ?: ""
            savedModel = when {
                name.contains("Buds4 Pro", ignoreCase = true) -> BudsModel.BUDS_4_PRO
                name.contains("Buds4", ignoreCase = true) -> BudsModel.BUDS_4
                name.contains("Buds3 Pro", ignoreCase = true) -> BudsModel.BUDS_3_PRO
                name.contains("Buds3", ignoreCase = true) -> BudsModel.BUDS_3
                name.contains("Buds2 Pro", ignoreCase = true) -> BudsModel.BUDS_2_PRO
                name.contains("Buds2", ignoreCase = true) -> BudsModel.BUDS_2
                else -> BudsModel.UNKNOWN
            }
            if (savedModel != BudsModel.UNKNOWN) {
                settingsRepo.saveDetectedModel(device.address, savedModel)
            }
        }
        
        deviceState.connectedModel.value = savedModel
        deviceState.modelOverride.value = settingsRepo.getModelOverride(device.address)

        connectionJob?.cancel()
        connectionJob = scope.launch {
            while (true) {
                deviceState.isConnecting.value = true
                deviceState.isConnected.value = false
                try {
                    Log.d(TAG, "Attempting connection to ${device.address}")
                    socket = device.createRfcommSocketToServiceRecord(BUDS_SPP_UUID)
                    socket?.connect()
                    
                    deviceState.isConnected.value = true
                    deviceState.isConnecting.value = false
                    lastConnectedTime = System.currentTimeMillis()
                    Log.i(TAG, "Connected to Galaxy Buds.")

                    packetQueue.trySend(SppPacketEncoder.buildPacket(0x26.toByte(), byteArrayOf()))
                    disableHardwareAutoPause()

                    val buffer = ByteArray(4096)
                    var index = 0
                    while (true) {
                        val bytes = socket?.inputStream?.read(buffer, index, buffer.size - index) ?: -1
                        if (bytes == -1) break
                        index += bytes

                        var processed = 0
                        while (processed < index) {
                            if (buffer[processed] != 0xFD.toByte()) {
                                processed++
                                continue
                            }

                            if (index - processed < 3) break

                            val header = (buffer[processed + 1].toInt() and 0xFF) or ((buffer[processed + 2].toInt() and 0xFF) shl 8)
                            val size = header and 0x3FF
                            val payloadSize = maxOf(0, size - 3)
                            val packetSize = 4 + size

                            if (packetSize > 1024) {
                                processed++
                                continue
                            }

                            if (index - processed < packetSize) break

                            if (buffer[processed + packetSize - 1] != 0xDD.toByte()) {
                                processed++
                                continue
                            }

                            val msgId = buffer[processed + 3]
                            val payload = buffer.copyOfRange(processed + 4, processed + 4 + payloadSize)
                            
                            packetParser.parsePacket(msgId, payload, payloadSize)

                            processed += packetSize
                        }

                        if (processed > 0) {
                            val remaining = index - processed
                            System.arraycopy(buffer, processed, buffer, 0, remaining)
                            index = remaining
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Connection error: ${e.message}")
                } finally {
                    closeSocket()
                    deviceState.isConnected.value = false
                    deviceState.isSpatialActive.value = false
                    deviceState.reset()
                    keepAliveJob?.cancel()
                }
                delay(500)
            }
        }
    }

    fun disconnect(forget: Boolean = false) {
        if (forget) {
            val mac = deviceState.savedDeviceMac.value
            if (mac != null) {
                settingsRepo.forgetDevice(mac)
            } else {
                settingsRepo.clearMacAddress()
            }
            deviceState.savedDeviceMac.value = null
            deviceState.connectedModel.value = BudsModel.UNKNOWN
            deviceState.modelOverride.value = null
        }
        synchronized(spatialConsumers) { spatialConsumers.clear() }
        stopSpatialSensor()
        connectionJob?.cancel()
        closeSocket()
        deviceState.isConnected.value = false
        deviceState.isConnecting.value = false
        deviceState.isSpatialActive.value = false
        deviceState.reset()
        lastSentEq = null
        lastSentNcMode = null
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
    }

    fun sendEqualizer(preset: EqPreset?) {
        if (preset == lastSentEq && preset != null) return
        lastSentEq = preset
        
        val payloadByte = preset?.payloadByte ?: 0x00.toByte()
        val payload = byteArrayOf(payloadByte)

        val packet = SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_EQUALIZER, payload)
        packetQueue.trySend(packet)
        Log.i(TAG, "Queued EQ preset: ${preset?.name ?: "OFF"} (byte: 0x%02X)".format(payloadByte))
    }

    fun sendNoiseControl(mode: NoiseControlMode?) {
        if (mode == null || mode == NoiseControlMode.IGNORE) return
        
        deviceState.activeNoiseControl.value = mode

        if (mode == lastSentNcMode) return
        lastSentNcMode = mode
        lastConfirmedNcMode = null
        lastNcSendTimestamp = System.currentTimeMillis()

        val payload = byteArrayOf(mode.payloadByte)
        val packet = SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_NOISE_CONTROLS, payload)
        
        ncRetryJob?.cancel()
        ncRetryJob = CoroutineScope(Dispatchers.IO).launch {
            for (attempt in 1..5) {
                if (attempt > 1 && lastConfirmedNcMode == mode) {
                    Log.i(TAG, "Noise Control ${mode.name} confirmed, stopping retries.")
                    break
                }
                
                try {
                    if (attempt == 1) {
                        socket?.outputStream?.write(packet)
                        socket?.outputStream?.flush()
                        Log.i(TAG, "Sent Noise Control directly without throttle: ${mode.name} (attempt 1)")
                    } else {
                        _packetQueue.trySend(QueuedPacket(packet, isNc = true, ncMode = mode))
                        Log.i(TAG, "Sent Noise Control to queue: ${mode.name} (attempt $attempt/5)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Send failed: ${e.message}")
                }
                
                delay(3000) // Wait for ANC chime to finish before retrying
                if (mode != lastSentNcMode) break
            }
        }
    }

    fun confirmNcMode(mode: NoiseControlMode) {
        lastConfirmedNcMode = mode
        if (deviceState.activeNoiseControl.value != mode) {
            if (System.currentTimeMillis() - lastNcSendTimestamp > 1500L) {
                deviceState.activeNoiseControl.value = mode
                lastSentNcMode = mode
            }
        }
    }

    fun setLastMatchedRule(rule: EqRule?) {
        deviceState.lastMatchedRule.value = rule
    }

    fun setConversationDetection(enabled: Boolean) {
        if (!deviceState.isConnected.value) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val byteValue = if (enabled) 1.toByte() else 0.toByte()
                val encoded = SppPacketEncoder.buildPacket(0x7A.toByte(), byteArrayOf(byteValue))
                packetQueue.trySend(encoded)
                deviceState.conversationDetectionEnabled.value = enabled
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setOneEarbudNoiseControl(enabled: Boolean) {
        if (!deviceState.isConnected.value) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val byteValue = if (enabled) 1.toByte() else 0.toByte()
                val encoded = SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_ANC_WITH_ONE_EARBUD, byteArrayOf(byteValue))
                packetQueue.trySend(encoded)
                deviceState.oneEarbudNoiseControlEnabled.value = enabled
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setUseAmbientSoundDuringCalls(enabled: Boolean) {
        if (!deviceState.isConnected.value) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val byteValue = if (enabled) 1.toByte() else 0.toByte()
                val encoded = SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_SIDETONE, byteArrayOf(byteValue))
                packetQueue.trySend(encoded)
                deviceState.useAmbientSoundDuringCalls.value = enabled
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setInEarDetectionForCalls(enabled: Boolean) {
        deviceState.inEarDetectionForCalls.value = enabled
        val packet = SppPacketEncoder.buildPacket(
            SppPacketEncoder.MSG_ID_SET_CALL_PATH_CONTROL,
            byteArrayOf(if (enabled) 0x00 else 0x01)
        )
        packetQueue.trySend(packet)
    }

    fun setDoubleTapEdgeEnabled(enabled: Boolean) {
        if (!deviceState.isConnected.value) return
        deviceState.doubleTapEdgeEnabled.value = enabled
        val packet = SppPacketEncoder.buildPacket(
            149.toByte(),
            byteArrayOf(if (enabled) 1 else 0)
        )
        packetQueue.trySend(packet)
    }

    fun setStereoBalance(value: Int) {
        val clamped = value.coerceIn(0, 32)
        deviceState.stereoBalance.value = clamped
        val packet = SppPacketEncoder.buildPacket(
            SppPacketEncoder.MSG_ID_HEARING_ENHANCEMENTS,
            byteArrayOf(clamped.toByte())
        )
        packetQueue.trySend(packet)
    }

    fun applyEqPreset(preset: EqPreset) {
        deviceState.manualPreset.value = preset
    }

    fun setManualPreset(preset: EqPreset?) {
        deviceState.manualPreset.value = preset
    }

    fun setManualNoiseControl(mode: NoiseControlMode?) {
        deviceState.manualNoiseControl.value = mode
    }

    private val spatialConsumers = mutableSetOf<String>()
    private var stopSpatialJob: Job? = null
    private var kickstartJob: Job? = null

    fun startSpatialSensor(consumer: String = "default") {
        synchronized(spatialConsumers) {
            spatialConsumers.add(consumer)
            stopSpatialJob?.cancel()
        }
        
        scope.launch {
            var attempts = 0
            while (effectiveModel.value == BudsModel.UNKNOWN && attempts < 15) {
                delay(200)
                attempts++
            }
            
            val timeSinceConnect = System.currentTimeMillis() - lastConnectedTime
            if (timeSinceConnect < 1500) {
                delay(1500 - timeSinceConnect)
            }
            
            val wasActive = deviceState.isSpatialActive.value
            deviceState.isSpatialActive.value = true
            Log.i(TAG, "Starting spatial sensor for consumer: $consumer (wasActive=$wasActive)")
            
            packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_SPATIAL_AUDIO, byteArrayOf(1)))
            packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL, byteArrayOf(0)))
            
            if (!wasActive || keepAliveJob?.isActive != true) {
                keepAliveJob?.cancel()
                keepAliveJob = scope.launch {
                    while (true) {
                        delay(2000)
                        if (deviceState.isSpatialActive.value) {
                            packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL, byteArrayOf(4)))
                        }
                    }
                }
            }
        }
    }

    fun kickstartSpatialSensor() {
        Log.i(TAG, "Kickstarting spatial sensor (Hard reset)")
        deviceState.isSpatialActive.value = false
        keepAliveJob?.cancel()
        kickstartJob?.cancel()
        packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_SPATIAL_AUDIO, byteArrayOf(0)))
        packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL, byteArrayOf(1)))
        
        kickstartJob = scope.launch {
            delay(1500) 
            
            if (spatialConsumers.isNotEmpty()) {
                deviceState.isSpatialActive.value = true
                packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_SPATIAL_AUDIO, byteArrayOf(1)))
                packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL, byteArrayOf(0)))
                
                keepAliveJob?.cancel()
                keepAliveJob = scope.launch {
                    while (true) {
                        delay(2000)
                        if (deviceState.isSpatialActive.value) {
                            packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL, byteArrayOf(4)))
                        }
                    }
                }
            }
        }
    }

    fun stopSpatialSensor(consumer: String = "default") {
        synchronized(spatialConsumers) {
            spatialConsumers.remove(consumer)
            if (spatialConsumers.isNotEmpty()) {
                Log.i(TAG, "Spatial sensor still needed by: $spatialConsumers, not stopping.")
                return
            }
        }

        stopSpatialJob?.cancel()
        stopSpatialJob = scope.launch {
            delay(1000)
            synchronized(spatialConsumers) {
                if (spatialConsumers.isNotEmpty()) return@launch
            }
            Log.i(TAG, "Stopping spatial sensor (no more consumers)")
            packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL, byteArrayOf(1)))
            packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_SPATIAL_AUDIO, byteArrayOf(0)))
            deviceState.isSpatialActive.value = false
            keepAliveJob?.cancel()
        }
    }

    fun toggleNoiseControl() {
        val current = lastSentNcMode ?: deviceState.manualNoiseControl.value
        val next = when (current) {
            NoiseControlMode.NOISE_CANCELLATION -> if (effectiveModel.value.supportsTransparencyNC) NoiseControlMode.TRANSPARENT else NoiseControlMode.OFF
            NoiseControlMode.TRANSPARENT -> NoiseControlMode.NOISE_CANCELLATION
            else -> NoiseControlMode.NOISE_CANCELLATION
        }
        sendNoiseControl(next)
    }
}
