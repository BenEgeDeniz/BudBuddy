package com.benegedeniz.budsdynamiceq.bluetooth
import com.benegedeniz.budsdynamiceq.R

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
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import com.benegedeniz.budsdynamiceq.data.model.FitTestResult
import com.benegedeniz.budsdynamiceq.gesture.QuaternionSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

enum class BudsModel(@androidx.annotation.StringRes val displayNameRes: Int) {
    BUDS_3_PRO(R.string.model_buds_3_pro),
    BUDS_4_PRO(R.string.model_buds_4_pro),
    UNKNOWN(R.string.model_buds_unknown)
}

class BudsController(private val context: Context) {

    companion object {
        private const val TAG = "BudsController"
        // Standard SPP UUID used by Galaxy Buds manager
        val BUDS_SPP_UUID: UUID = UUID.fromString("2e73a4ad-332d-41fc-90e2-16bef06523f2")
        private const val PREFS_NAME = "BudsPrefs"
        private const val KEY_MAC_ADDRESS = "saved_mac_address"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val bluetoothManager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    fun startFitTest() {
        val payload = byteArrayOf(1)
        sendSppPacket(157.toByte(), payload)
        // Reset results on start
        _fitTestResultL.value = FitTestResult.UNKNOWN
        _fitTestResultR.value = FitTestResult.UNKNOWN
    }

    fun stopFitTest() {
        val payload = byteArrayOf(0)
        sendSppPacket(157.toByte(), payload)
    }

    private fun sendSppPacket(msgId: Byte, payload: ByteArray) {
        val packet = SppPacketEncoder.buildPacket(msgId, payload)
        packetQueue.trySend(packet)
    }

    private fun disableHardwareAutoPause() {
        // payload = 0x00 disables the built-in pause behavior
        sendSppPacket(SppPacketEncoder.MSG_ID_PAUSE_MEDIA_WHEN_ONE_BUD_REMOVED, byteArrayOf(0))
    }

    private var socket: BluetoothSocket? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _savedDeviceMac = MutableStateFlow<String?>(prefs.getString(KEY_MAC_ADDRESS, null))
    val savedDeviceMac: StateFlow<String?> = _savedDeviceMac.asStateFlow()

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    private val _isSpatialActive = MutableStateFlow(false)
    val isSpatialActive: StateFlow<Boolean> = _isSpatialActive.asStateFlow()

    val isHomePageVisible = MutableStateFlow(false)

    private val _connectedModel = MutableStateFlow(
        prefs.getString("detected_model_${prefs.getString(KEY_MAC_ADDRESS, "")}", null)?.let {
            try { BudsModel.valueOf(it) } catch (_: Exception) { null }
        } ?: BudsModel.UNKNOWN
    )
    val connectedModel: StateFlow<BudsModel> = _connectedModel.asStateFlow()

    private val _modelOverride = MutableStateFlow<BudsModel?>(prefs.getString("model_override", null)?.let {
        try { BudsModel.valueOf(it) } catch (_: Exception) { null }
    })
    val modelOverride: StateFlow<BudsModel?> = _modelOverride.asStateFlow()

    /** The effective model: override if set, otherwise auto-detected */
    val effectiveModel: StateFlow<BudsModel> = _modelOverride
        .combine(_connectedModel) { override, detected -> override ?: detected }
        .stateIn(scope, SharingStarted.Eagerly, _modelOverride.value ?: BudsModel.UNKNOWN)

    fun setModelOverride(model: BudsModel?) {
        _modelOverride.value = model
        if (model != null) {
            prefs.edit().putString("model_override", model.name).apply()
        } else {
            prefs.edit().remove("model_override").apply()
        }
    }

    enum class ImuSide { LEFT, RIGHT, UNKNOWN }

    private val _activeImuSide = MutableStateFlow(ImuSide.UNKNOWN)
    val activeImuSide: StateFlow<ImuSide> = _activeImuSide.asStateFlow()

    private val _activeImuReason = MutableStateFlow("Initializing...")
    val activeImuReason: StateFlow<String> = _activeImuReason.asStateFlow()

    val invertPitch: StateFlow<Boolean> = _activeImuSide
        .map { it == ImuSide.LEFT }
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun setActiveImuSide(side: ImuSide, reason: String? = null) {
        _activeImuSide.value = side
        if (reason != null) {
            _activeImuReason.value = reason
        }
    }

    private val _spatialDataFlow = MutableSharedFlow<QuaternionSample>(
        extraBufferCapacity = 100,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val spatialDataFlow: SharedFlow<QuaternionSample> = _spatialDataFlow.asSharedFlow()

    private val _batteryL = MutableStateFlow(-1)
    val batteryL: StateFlow<Int> = _batteryL.asStateFlow()

    private val _batteryR = MutableStateFlow(-1)
    val batteryR: StateFlow<Int> = _batteryR.asStateFlow()

    private val _batteryCase = MutableStateFlow(-1)
    val batteryCase: StateFlow<Int> = _batteryCase.asStateFlow()

    private val _chargingL = MutableStateFlow(false)
    val chargingL: StateFlow<Boolean> = _chargingL.asStateFlow()

    private val _chargingR = MutableStateFlow(false)
    val chargingR: StateFlow<Boolean> = _chargingR.asStateFlow()

    private val _chargingCase = MutableStateFlow(false)
    val chargingCase: StateFlow<Boolean> = _chargingCase.asStateFlow()

    private val _temperatureL = MutableStateFlow<Double?>(null)
    val temperatureL: StateFlow<Double?> = _temperatureL.asStateFlow()

    private val _temperatureR = MutableStateFlow<Double?>(null)
    val temperatureR: StateFlow<Double?> = _temperatureR.asStateFlow()


    private val _placementL = MutableStateFlow(PlacementState.UNKNOWN)
    val placementL: StateFlow<PlacementState> = _placementL.asStateFlow()

    private val _placementR = MutableStateFlow(PlacementState.UNKNOWN)
    val placementR: StateFlow<PlacementState> = _placementR.asStateFlow()

    private val _fitTestResultL = MutableStateFlow(FitTestResult.UNKNOWN)
    val fitTestResultL: StateFlow<FitTestResult> = _fitTestResultL.asStateFlow()

    private val _fitTestResultR = MutableStateFlow(FitTestResult.UNKNOWN)
    val fitTestResultR: StateFlow<FitTestResult> = _fitTestResultR.asStateFlow()

    private val _isFitTestScreenOpen = MutableStateFlow(false)
    val isFitTestScreenOpen: StateFlow<Boolean> = _isFitTestScreenOpen.asStateFlow()

    fun setFitTestScreenOpen(isOpen: Boolean) {
        _isFitTestScreenOpen.value = isOpen
        if (!isOpen) {
            _fitTestResultL.value = FitTestResult.UNKNOWN
            _fitTestResultR.value = FitTestResult.UNKNOWN
        }
    }

    private val _conversationDetectionEnabled = MutableStateFlow(false)
    val conversationDetectionEnabled: StateFlow<Boolean> = _conversationDetectionEnabled.asStateFlow()

    private val _oneEarbudNoiseControlEnabled = MutableStateFlow(false)
    val oneEarbudNoiseControlEnabled: StateFlow<Boolean> = _oneEarbudNoiseControlEnabled.asStateFlow()

    private val _useAmbientSoundDuringCalls = MutableStateFlow(false)
    val useAmbientSoundDuringCalls: StateFlow<Boolean> = _useAmbientSoundDuringCalls.asStateFlow()

    private val _inEarDetectionForCalls = MutableStateFlow(true)
    val inEarDetectionForCalls: StateFlow<Boolean> = _inEarDetectionForCalls.asStateFlow()

    private val _stereoBalance = MutableStateFlow(16) // Default to center
    val stereoBalance: StateFlow<Int> = _stereoBalance.asStateFlow()

    private var keepAliveJob: Job? = null

    private val _lastMatchedRule = MutableStateFlow<EqRule?>(null)
    val lastMatchedRule: StateFlow<EqRule?> = _lastMatchedRule.asStateFlow()

    private val _manualPreset = MutableStateFlow<EqPreset?>(null)
    val manualPreset: StateFlow<EqPreset?> = _manualPreset.asStateFlow()

    private val _manualNoiseControl = MutableStateFlow<NoiseControlMode?>(null)
    val manualNoiseControl: StateFlow<NoiseControlMode?> = _manualNoiseControl.asStateFlow()

    private val _activeNoiseControl = MutableStateFlow<NoiseControlMode?>(null)
    val activeNoiseControl: StateFlow<NoiseControlMode?> = _activeNoiseControl.asStateFlow()

    private var targetDevice: BluetoothDevice? = null
    private val packetQueue = Channel<ByteArray>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            for (packet in packetQueue) {
                try {
                    socket?.outputStream?.write(packet)
                    delay(250) // Hardware processing buffer time
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send queued packet: ${e.message}")
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (_isConnected.value) {
                    if (!_isSpatialActive.value || isHomePageVisible.value) {
                        // Only request DEBUG_GET_ALL_DATA if spatial audio is NOT active,
                        // OR if the home page is currently visible (so the user can see temp).
                        // The earbuds pause the IMU stream for ~500ms to gather and construct this massive packet,
                        // which causes the live preview and gesture detection to lag significantly.
                        packetQueue.trySend(SppPacketEncoder.buildPacket(0x26.toByte(), byteArrayOf())) 
                    }
                }
                delay(15000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return emptyList()
        return bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Attempts to find the previously saved device and connect to it.
     */
    @SuppressLint("MissingPermission")
    fun startAutoConnect() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or available.")
            return
        }

        if (_isConnected.value || _isConnecting.value || connectionJob?.isActive == true) {
            Log.i(TAG, "Already connected or connecting, skipping auto-connect.")
            return
        }

        val savedMac = prefs.getString(KEY_MAC_ADDRESS, null)
        
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
        prefs.edit().putString(KEY_MAC_ADDRESS, device.address).apply()
        _savedDeviceMac.value = device.address
        targetDevice = device

        val savedModel = prefs.getString("detected_model_${device.address}", null)?.let {
            try { BudsModel.valueOf(it) } catch (_: Exception) { null }
        } ?: BudsModel.UNKNOWN
        _connectedModel.value = savedModel

        connectionJob?.cancel()
        connectionJob = scope.launch {
            while (true) {
                _isConnecting.value = true
                _isConnected.value = false
                try {
                    Log.d(TAG, "Attempting connection to ${device.address}")
                    socket = device.createRfcommSocketToServiceRecord(BUDS_SPP_UUID)
                    socket?.connect()
                    
                    _isConnected.value = true
                    _isConnecting.value = false
                    Log.i(TAG, "Connected to Galaxy Buds.")

                    // Immediately poll debug info upon connection to determine model and temperature
                    packetQueue.trySend(SppPacketEncoder.buildPacket(0x26.toByte(), byteArrayOf()))

                    // Disable hardware-level auto-pause so Wear State Actions work correctly
                    disableHardwareAutoPause()

                    // Keep connection alive, listen for incoming packets
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
                            val packetSize = 4 + size // SOM (1) + Header (2) + size + EOM (1)

                            if (packetSize > 1024) { // Unreasonable size, fake SOM
                                processed++
                                continue
                            }

                            if (index - processed < packetSize) break

                            if (buffer[processed + packetSize - 1] != 0xDD.toByte()) {
                                // Invalid EOM byte, this was a fake SOM (0xFD) inside another payload
                                processed++
                                continue
                            }

                            val msgId = buffer[processed + 3]
                            val payload = buffer.copyOfRange(processed + 4, processed + 4 + payloadSize)
                            
                            if (msgId == 0x61.toByte() || msgId == 0x26.toByte()) {
                                Log.d(TAG, "Received msgId: 0x${msgId.toUByte().toString(16)}, size: $payloadSize, payload: ${payload.joinToString("") { "%02X".format(it) }}")
                            }

                            if (msgId == 0x60.toByte()) {
                                if (payloadSize > 6) {
                                    _batteryL.value = payload[1].toInt() and 0xFF
                                    _batteryR.value = payload[2].toInt() and 0xFF
                                    val placementByte = payload[5].toInt() and 0xFF
                                    val pL = PlacementState.fromId((placementByte and 0xF0) shr 4)
                                    val pR = PlacementState.fromId(placementByte and 0x0F)
                                    _placementL.value = pL
                                    _placementR.value = pR
                                    
                                    val batCase = payload[6].toInt() and 0xFF
                                    val lInCase = pL == PlacementState.CASE || pL == PlacementState.CLOSED_CASE
                                    val rInCase = pR == PlacementState.CASE || pR == PlacementState.CLOSED_CASE
                                    _batteryCase.value = if (!lInCase && !rInCase) -1 else batCase
                                    
                                    if (payloadSize > 7) {
                                        val chargingStatus = payload[7].toInt() and 0xFF
                                        _chargingL.value = lInCase && ((chargingStatus and 16) == 16 || (chargingStatus and 1) == 1)
                                        _chargingR.value = rInCase && ((chargingStatus and 4) == 4 || (chargingStatus and 2) == 2)
                                        _chargingCase.value = (lInCase || rInCase || _batteryCase.value > 0) && ((chargingStatus and 1) == 1 || (chargingStatus and 2) == 2)
                                    }
                                }
                            } else if (msgId == 0x61.toByte()) {
                                if (payloadSize > 7) {
                                    _batteryL.value = payload[2].toInt() and 0xFF
                                    _batteryR.value = payload[3].toInt() and 0xFF
                                    val placementByte = payload[6].toInt() and 0xFF
                                    val pL = PlacementState.fromId((placementByte and 0xF0) shr 4)
                                    val pR = PlacementState.fromId(placementByte and 0x0F)
                                    _placementL.value = pL
                                    _placementR.value = pR
                                    
                                    val batCase = payload[7].toInt() and 0xFF
                                    val lInCase = pL == PlacementState.CASE || pL == PlacementState.CLOSED_CASE
                                    val rInCase = pR == PlacementState.CASE || pR == PlacementState.CLOSED_CASE
                                    _batteryCase.value = if (!lInCase && !rInCase) -1 else batCase
                                    
                                    var chargingIndex = -1
                                    if (payloadSize == 62) {
                                        chargingIndex = 42 // Buds2 Pro
                                    } else if (payloadSize == 64 || payloadSize == 44) {
                                        chargingIndex = 43 // Buds Pro
                                    } else if (payloadSize == 41 || payloadSize == 37) {
                                        chargingIndex = 36 // Buds2
                                    } else if (payloadSize >= 44) {
                                        chargingIndex = 43 // Fallback
                                    }
                                    
                                    if (chargingIndex != -1 && payloadSize > chargingIndex) {
                                        val chargingStatus = payload[chargingIndex].toInt() and 0xFF
                                        _chargingL.value = lInCase && ((chargingStatus and 16) == 16 || (chargingStatus and 1) == 1)
                                        _chargingR.value = rInCase && ((chargingStatus and 4) == 4 || (chargingStatus and 2) == 2)
                                        _chargingCase.value = (lInCase || rInCase || _batteryCase.value > 0) && ((chargingStatus and 1) == 1 || (chargingStatus and 2) == 2)
                                    }
                                }
                                if (payloadSize > 12) {
                                    val ncModeVal = payload[12].toInt() and 0xFF
                                    val ncMode = NoiseControlMode.entries.find { it.payloadByte.toInt() == ncModeVal }
                                    if (ncMode != null && _activeNoiseControl.value != ncMode) {
                                        if (System.currentTimeMillis() - lastNcSendTimestamp > 1500L) {
                                            _activeNoiseControl.value = ncMode
                                            lastSentNcMode = ncMode // keep in sync
                                        }
                                    }
                                }
                                if (payloadSize > 26) {
                                    _conversationDetectionEnabled.value = payload[26].toInt() == 1
                                }
                                if (payloadSize > 28) {
                                    _oneEarbudNoiseControlEnabled.value = payload[28].toInt() == 1
                                }
                                if (payloadSize > 33) {
                                    _useAmbientSoundDuringCalls.value = payload[33].toInt() == 1
                                }
                                if (payloadSize > 34) {
                                    _inEarDetectionForCalls.value = payload[34].toInt() == 0
                                }
                                
                                var hearingEnhancementIndex = -1
                                if (payloadSize == 62) {
                                    hearingEnhancementIndex = 25
                                } else if (payloadSize == 64 || payloadSize == 44) {
                                    hearingEnhancementIndex = 22
                                } else if (payloadSize == 41 || payloadSize == 37) {
                                    hearingEnhancementIndex = 25
                                } else if (payloadSize >= 44) {
                                    hearingEnhancementIndex = 25
                                }
                                
                                if (hearingEnhancementIndex != -1 && payloadSize > hearingEnhancementIndex) {
                                    _stereoBalance.value = payload[hearingEnhancementIndex].toInt() and 0xFF
                                }
                            } else if (msgId == 0x26.toByte()) { // DEBUG_GET_ALL_DATA
                                // swLength offset calculation based on VersionDataToString from GalaxyBudsClient
                                var swLength = 3
                                if (payloadSize >= 22) {
                                    val isBuds3 = payload[2] == 0x52.toByte() && payload[3] == 0x36.toByte() && (payload[4] == 0x34.toByte() || payload[4] == 0x33.toByte()) // "R64" or "R63"
                                    if (isBuds3) {
                                        swLength = 20
                                    }
                                    
                                    // Auto-detect model from firmware version prefix
                                    val ch2 = payload[2].toInt().toChar() // 'R'
                                    val ch3 = payload[3].toInt().toChar() // '6'
                                    val ch4 = payload[4].toInt().toChar() // '3' or '4'
                                    val prefix = "$ch2$ch3$ch4"
                                    val detected = when {
                                        prefix.startsWith("R63") -> BudsModel.BUDS_3_PRO
                                        prefix.startsWith("R64") -> BudsModel.BUDS_4_PRO
                                        else -> BudsModel.UNKNOWN
                                    }
                                    if (detected != BudsModel.UNKNOWN && detected != _connectedModel.value) {
                                        _connectedModel.value = detected
                                        val mac = _savedDeviceMac.value ?: ""
                                        prefs.edit().putString("detected_model_$mac", detected.name).apply()
                                        Log.i(TAG, "Auto-detected model: ${context.getString(detected.displayNameRes)} (prefix: $prefix)")
                                    }
                                }
                                
                                if (payloadSize > swLength + 38) {
                                    // Parse Little Endian Int16 at swLength + 35 and swLength + 37, multiplied by 0.1
                                    val leftTempRaw = (payload[swLength + 35].toInt() and 0xFF) or (payload[swLength + 36].toInt() shl 8)
                                    val rightTempRaw = (payload[swLength + 37].toInt() and 0xFF) or (payload[swLength + 38].toInt() shl 8)
                                    val leftTemp = leftTempRaw.toShort() * 0.1
                                    val rightTemp = rightTempRaw.toShort() * 0.1
                                    
                                    // 0x4006 (1639.0) means earbud is likely disconnected or sensor is off
                                    if (leftTempRaw != 0x4006 && leftTemp > 0.0 && leftTemp < 100.0) {
                                        _temperatureL.value = leftTemp
                                    } else if (leftTempRaw == 0x4006 || leftTempRaw == 0) {
                                        // Keep last known valid temperature instead of nulling out immediately to avoid flickering
                                    }
                                    
                                    if (rightTempRaw != 0x4006 && rightTemp > 0.0 && rightTemp < 100.0) {
                                        _temperatureR.value = rightTemp
                                    } else if (rightTempRaw == 0x4006 || rightTempRaw == 0) {
                                        // Keep last known
                                    }
                                }
                            } else if (msgId == 158.toByte()) {
                                if (payloadSize >= 2) {
                                    _fitTestResultL.value = FitTestResult.fromId(payload[0].toInt() and 0xFF)
                                    _fitTestResultR.value = FitTestResult.fromId(payload[1].toInt() and 0xFF)
                                }
                            } else if (msgId == 0x77.toByte()) {
                                if (payloadSize > 0) {
                                    val ncModeVal = payload[0].toInt() and 0xFF
                                    val ncMode = NoiseControlMode.entries.find { it.payloadByte.toInt() == ncModeVal }
                                    if (ncMode != null && _activeNoiseControl.value != ncMode) {
                                        if (System.currentTimeMillis() - lastNcSendTimestamp > 1500L) {
                                            _activeNoiseControl.value = ncMode
                                            lastSentNcMode = ncMode // keep in sync
                                        }
                                    }
                                }
                            } else if (msgId == SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL) {
                                if (payload.isNotEmpty()) {
                                    val status = payload[0].toInt()
                                    if (status == 2) { // AttachSuccess
                                        _isSpatialActive.value = true
                                        Log.i(TAG, "Spatial Audio Sensor Attached")
                                    } else if (status == 3) { // DetachSuccess
                                        _isSpatialActive.value = false
                                        Log.i(TAG, "Spatial Audio Sensor Detached")
                                    }
                                }
                            } else if (msgId == SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_DATA) {
                                if (payload.isNotEmpty() && payload[0].toInt() == 32) { // BudGrv event
                                    if (payload.size >= 9) {
                                        val buffer = ByteBuffer.wrap(payload, 1, 8).order(ByteOrder.LITTLE_ENDIAN)
                                        var x = buffer.short / 10000.0f
                                        val y = buffer.short / 10000.0f
                                        var z = buffer.short / 10000.0f
                                        val w = buffer.short / 10000.0f
                                        
                                        var outX = x
                                        var outY = y
                                        var outZ = z
                                        var outW = w

                                        if (invertPitch.value) {
                                            // Left earbud is physically rotated 180 degrees around the Y axis
                                            // Negating X (Pitch) and Z (Roll) mirrors the local frame properly.
                                            outX = -x
                                            outZ = -z
                                        }
                                        
                                        _spatialDataFlow.tryEmit(QuaternionSample(System.currentTimeMillis(), outX, outY, outZ, outW))
                                    }
                                }
                            }

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
                    _isConnected.value = false
                    _isSpatialActive.value = false
                    resetDeviceState()
                    keepAliveJob?.cancel()
                }

                // Rapidly reconnect delay
                delay(500)
            }
        }
    }

    fun disconnect(forget: Boolean = false) {
        if (forget) {
            prefs.edit().remove(KEY_MAC_ADDRESS).apply()
            _savedDeviceMac.value = null
        }
        synchronized(spatialConsumers) { spatialConsumers.clear() }
        stopSpatialSensor()
        connectionJob?.cancel()
        closeSocket()
        _isConnected.value = false
        _isConnecting.value = false
        _isSpatialActive.value = false
        resetDeviceState()
        lastSentEq = null
        lastSentNcMode = null
    }

    private fun resetDeviceState() {
        _batteryL.value = -1
        _batteryR.value = -1
        _batteryCase.value = -1
        _chargingL.value = false
        _chargingR.value = false
        _chargingCase.value = false
        _placementL.value = PlacementState.UNKNOWN
        _placementR.value = PlacementState.UNKNOWN
        _fitTestResultL.value = FitTestResult.UNKNOWN
        _fitTestResultR.value = FitTestResult.UNKNOWN
        _activeImuSide.value = ImuSide.UNKNOWN
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
    }


    private var lastSentEq: EqPreset? = null
    private var lastSentNcMode: com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode? = null
    private var lastNcSendTimestamp: Long = 0L
    /** Milliseconds since epoch of the last app-sent NC command. Used externally to distinguish app vs hardware NC changes. */
    val lastAppNcSendTimestamp: Long get() = lastNcSendTimestamp

    /**
     * Sends the equalizer command to the connected buds.
     * Payload for newer models: 0x00 for off, preset + 1 for on.
     */
    fun sendEqualizer(preset: EqPreset?) {
        if (preset == lastSentEq && preset != null) return
        lastSentEq = preset
        
        val payloadByte = preset?.payloadByte ?: 0x00.toByte()
        val payload = byteArrayOf(payloadByte)

        val packet = SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_EQUALIZER, payload)
        packetQueue.trySend(packet)
        Log.i(TAG, "Queued EQ preset: ${preset?.name ?: "OFF"} (byte: 0x%02X)".format(payloadByte))
    }

    fun sendNoiseControl(mode: com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode?) {
        if (mode == null || mode == com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode.IGNORE) return
        if (mode == lastSentNcMode && System.currentTimeMillis() - lastNcSendTimestamp < 2000L) return
        lastSentNcMode = mode
        lastNcSendTimestamp = System.currentTimeMillis()
        _activeNoiseControl.value = mode

        val payload = byteArrayOf(mode.payloadByte)
        val packet = SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_NOISE_CONTROLS, payload)
        packetQueue.trySend(packet)
        Log.i(TAG, "Queued Noise Control: ${mode.name} (byte: 0x%02X)".format(mode.payloadByte))
    }

    fun setLastMatchedRule(rule: EqRule?) {
        _lastMatchedRule.value = rule
    }

    fun setConversationDetection(enabled: Boolean) {
        if (!_isConnected.value) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val byteValue = if (enabled) 1.toByte() else 0.toByte()
                val encoded = SppPacketEncoder.buildPacket(0x7A.toByte(), byteArrayOf(byteValue))
                packetQueue.trySend(encoded)
                _conversationDetectionEnabled.value = enabled
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setOneEarbudNoiseControl(enabled: Boolean) {
        if (!_isConnected.value) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val byteValue = if (enabled) 1.toByte() else 0.toByte()
                val encoded = SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_ANC_WITH_ONE_EARBUD, byteArrayOf(byteValue))
                packetQueue.trySend(encoded)
                _oneEarbudNoiseControlEnabled.value = enabled
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setUseAmbientSoundDuringCalls(enabled: Boolean) {
        if (!_isConnected.value) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val byteValue = if (enabled) 1.toByte() else 0.toByte()
                val encoded = SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_SIDETONE, byteArrayOf(byteValue))
                packetQueue.trySend(encoded)
                _useAmbientSoundDuringCalls.value = enabled
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setInEarDetectionForCalls(enabled: Boolean) {
        _inEarDetectionForCalls.value = enabled
        val packet = SppPacketEncoder.buildPacket(
            SppPacketEncoder.MSG_ID_SET_CALL_PATH_CONTROL,
            byteArrayOf(if (enabled) 0x00 else 0x01)
        )
        packetQueue.trySend(packet)
    }

    fun setStereoBalance(value: Int) {
        val clamped = value.coerceIn(0, 32)
        _stereoBalance.value = clamped
        val packet = SppPacketEncoder.buildPacket(
            SppPacketEncoder.MSG_ID_HEARING_ENHANCEMENTS,
            byteArrayOf(clamped.toByte())
        )
        packetQueue.trySend(packet)
    }

    fun applyEqPreset(preset: EqPreset) {
        _manualPreset.value = preset
    }

    fun setManualPreset(preset: EqPreset?) {
        _manualPreset.value = preset
    }

    fun setManualNoiseControl(mode: NoiseControlMode?) {
        _manualNoiseControl.value = mode
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
            // If model is UNKNOWN, wait a bit for the 0x26 debug packet to return
            // before bombarding the earbuds with spatial audio commands that could interrupt it.
            var attempts = 0
            while (effectiveModel.value == BudsModel.UNKNOWN && attempts < 15) {
                delay(200)
                attempts++
            }
            
            if (effectiveModel.value == BudsModel.BUDS_3_PRO) {
                Log.i(TAG, "Spatial sensor (Gestures) not supported on Buds 3 Pro yet.")
                return@launch
            }
            
            val wasActive = _isSpatialActive.value
            _isSpatialActive.value = true
            Log.i(TAG, "Starting spatial sensor for consumer: $consumer (wasActive=$wasActive)")
            
            // Always send setup packets as a failsafe against dropped packets or silent detaches
            packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_SPATIAL_AUDIO, byteArrayOf(1)))
            packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL, byteArrayOf(0)))
            
            if (!wasActive || keepAliveJob?.isActive != true) {
                keepAliveJob?.cancel()
                keepAliveJob = scope.launch {
                    while (true) {
                        delay(2000)
                        if (_isSpatialActive.value) {
                            packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL, byteArrayOf(4)))
                        }
                    }
                }
            }
        }
    }

    fun kickstartSpatialSensor() {
        if (effectiveModel.value == BudsModel.BUDS_3_PRO) return
        
        Log.i(TAG, "Kickstarting spatial sensor (Hard reset)")
        // Stop it fully
        _isSpatialActive.value = false
        keepAliveJob?.cancel()
        kickstartJob?.cancel()
        packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_SPATIAL_AUDIO, byteArrayOf(0)))
        packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL, byteArrayOf(1)))
        
        kickstartJob = scope.launch {
                delay(1500) // Wait for earbud role-sync to finish
                
                if (spatialConsumers.isNotEmpty()) {
                    _isSpatialActive.value = true
                    packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SET_SPATIAL_AUDIO, byteArrayOf(1)))
                    packetQueue.trySend(SppPacketEncoder.buildPacket(SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL, byteArrayOf(0)))
                    
                    keepAliveJob?.cancel()
                    keepAliveJob = scope.launch {
                        while (true) {
                            delay(2000)
                            if (_isSpatialActive.value) {
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
            _isSpatialActive.value = false
            keepAliveJob?.cancel()
        }
    }

    fun toggleNoiseControl() {
        val current = lastSentNcMode ?: _manualNoiseControl.value
        val next = when (current) {
            NoiseControlMode.NOISE_CANCELLATION -> NoiseControlMode.TRANSPARENT
            else -> NoiseControlMode.NOISE_CANCELLATION
        }
        sendNoiseControl(next)
    }
}
