package com.benegedeniz.budsdynamiceq.bluetooth

import android.util.Log
import com.benegedeniz.budsdynamiceq.data.model.FitTestResult
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import com.benegedeniz.budsdynamiceq.data.repository.DeviceStateRepository
import com.benegedeniz.budsdynamiceq.data.repository.SettingsRepository
import com.benegedeniz.budsdynamiceq.gesture.QuaternionSample
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BudsPacketParser(
    private val deviceState: DeviceStateRepository,
    private val settingsRepo: SettingsRepository,
    private val controller: BudsController
) {
    companion object {
        private const val TAG = "BudsPacketParser"
    }

    fun parsePacket(msgId: Byte, payload: ByteArray, payloadSize: Int) {
        if (msgId == 0x61.toByte() || msgId == 0x26.toByte()) {
            Log.d(TAG, "Received msgId: 0x${msgId.toUByte().toString(16)}, size: $payloadSize, payload: ${payload.joinToString("") { "%02X".format(it) }}")
        }

        if (msgId == 0x60.toByte()) {
            if (payloadSize > 6) {
                deviceState.batteryL.value = payload[1].toInt() and 0xFF
                deviceState.batteryR.value = payload[2].toInt() and 0xFF
                val placementByte = payload[5].toInt() and 0xFF
                val pL = PlacementState.fromId((placementByte and 0xF0) shr 4)
                val pR = PlacementState.fromId(placementByte and 0x0F)
                deviceState.placementL.value = pL
                deviceState.placementR.value = pR
                
                val batCase = payload[6].toInt() and 0xFF
                val lInCase = pL == PlacementState.CASE || pL == PlacementState.CLOSED_CASE
                val rInCase = pR == PlacementState.CASE || pR == PlacementState.CLOSED_CASE
                deviceState.batteryCase.value = if (!lInCase && !rInCase) -1 else batCase
                
                if (payloadSize > 7) {
                    val chargingStatus = payload[7].toInt() and 0xFF
                    deviceState.chargingL.value = lInCase && ((chargingStatus and 16) == 16 || (chargingStatus and 1) == 1)
                    deviceState.chargingR.value = rInCase && ((chargingStatus and 4) == 4 || (chargingStatus and 2) == 2)
                    deviceState.chargingCase.value = (lInCase || rInCase || deviceState.batteryCase.value > 0) && ((chargingStatus and 1) == 1 || (chargingStatus and 2) == 2)
                }
            }
        } else if (msgId == 0x61.toByte()) {
            if (payloadSize > 7) {
                deviceState.batteryL.value = payload[2].toInt() and 0xFF
                deviceState.batteryR.value = payload[3].toInt() and 0xFF
                val placementByte = payload[6].toInt() and 0xFF
                val pL = PlacementState.fromId((placementByte and 0xF0) shr 4)
                val pR = PlacementState.fromId(placementByte and 0x0F)
                deviceState.placementL.value = pL
                deviceState.placementR.value = pR
                
                val batCase = payload[7].toInt() and 0xFF
                val lInCase = pL == PlacementState.CASE || pL == PlacementState.CLOSED_CASE
                val rInCase = pR == PlacementState.CASE || pR == PlacementState.CLOSED_CASE
                deviceState.batteryCase.value = if (!lInCase && !rInCase) -1 else batCase
                
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
                    deviceState.chargingL.value = lInCase && ((chargingStatus and 16) == 16 || (chargingStatus and 1) == 1)
                    deviceState.chargingR.value = rInCase && ((chargingStatus and 4) == 4 || (chargingStatus and 2) == 2)
                    deviceState.chargingCase.value = (lInCase || rInCase || deviceState.batteryCase.value > 0) && ((chargingStatus and 1) == 1 || (chargingStatus and 2) == 2)
                }
            }
            if (payloadSize > 12) {
                val ncModeVal = payload[12].toInt() and 0xFF
                val ncMode = NoiseControlMode.entries.find { it.payloadByte.toInt() == ncModeVal }
                if (ncMode != null) {
                    controller.confirmNcMode(ncMode)
                }
            }
            if (payloadSize > 26) {
                deviceState.conversationDetectionEnabled.value = payload[26].toInt() == 1
            }
            if (payloadSize > 28) {
                deviceState.oneEarbudNoiseControlEnabled.value = payload[28].toInt() == 1
            }
            if (payloadSize > 33) {
                deviceState.useAmbientSoundDuringCalls.value = payload[33].toInt() == 1
            }
            if (payloadSize > 34) {
                deviceState.inEarDetectionForCalls.value = payload[34].toInt() == 0
            }
            if (payloadSize > 32) {
                val currentModel = deviceState.connectedModel.value
                if (currentModel == BudsModel.BUDS_2 || currentModel == BudsModel.BUDS_2_PRO) {
                    deviceState.doubleTapEdgeEnabled.value = payload[32].toInt() == 1
                }
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
                deviceState.stereoBalance.value = payload[hearingEnhancementIndex].toInt() and 0xFF
            }
        } else if (msgId == 0x26.toByte()) { // DEBUG_GET_ALL_DATA
            var swLength = 3
            if (payloadSize >= 22) {
                val isBuds3 = payload[2] == 0x52.toByte() && payload[3] == 0x36.toByte() && (payload[4] == 0x34.toByte() || payload[4] == 0x33.toByte()) // "R64" or "R63"
                if (isBuds3) {
                    swLength = 20
                }
                
                val ch2 = payload[2].toInt().toChar()
                val ch3 = payload[3].toInt().toChar()
                val ch4 = payload[4].toInt().toChar()
                val prefix = "$ch2$ch3$ch4"
                val detected = when {
                    prefix.startsWith("R64") -> BudsModel.BUDS_4_PRO
                    prefix.startsWith("R63") -> BudsModel.BUDS_3_PRO
                    prefix.startsWith("R53") -> BudsModel.BUDS_3
                    prefix.startsWith("R51") -> BudsModel.BUDS_2_PRO
                    prefix.startsWith("R17") -> BudsModel.BUDS_2
                    else -> BudsModel.UNKNOWN
                }
                if (detected != BudsModel.UNKNOWN && detected != deviceState.connectedModel.value) {
                    deviceState.connectedModel.value = detected
                    val mac = deviceState.savedDeviceMac.value ?: ""
                    settingsRepo.saveDetectedModel(mac, detected)
                    Log.i(TAG, "Auto-detected model: ${detected.name} (prefix: $prefix)")
                }
            }
            
            if (payloadSize > swLength + 38) {
                val leftTempRaw = (payload[swLength + 35].toInt() and 0xFF) or (payload[swLength + 36].toInt() shl 8)
                val rightTempRaw = (payload[swLength + 37].toInt() and 0xFF) or (payload[swLength + 38].toInt() shl 8)
                val leftTemp = leftTempRaw.toShort() * 0.1
                val rightTemp = rightTempRaw.toShort() * 0.1
                
                if (leftTempRaw != 0x4006 && leftTemp > 0.0 && leftTemp < 100.0) {
                    deviceState.temperatureL.value = leftTemp
                }
                
                if (rightTempRaw != 0x4006 && rightTemp > 0.0 && rightTemp < 100.0) {
                    deviceState.temperatureR.value = rightTemp
                }
            }
        } else if (msgId == 158.toByte()) {
            if (payloadSize >= 2) {
                deviceState.fitTestResultL.value = FitTestResult.fromId(payload[0].toInt() and 0xFF)
                deviceState.fitTestResultR.value = FitTestResult.fromId(payload[1].toInt() and 0xFF)
            }
        } else if (msgId == 161.toByte()) {
            deviceState.isSearching.value = false
        } else if (msgId == 163.toByte()) {
            if (payloadSize >= 2) {
                deviceState.isLeftMuted.value = payload[0].toInt() == 1
                deviceState.isRightMuted.value = payload[1].toInt() == 1
            }
        } else if (msgId == 0x77.toByte()) {
            if (payloadSize > 0) {
                val ncModeVal = payload[0].toInt() and 0xFF
                val ncMode = NoiseControlMode.entries.find { it.payloadByte.toInt() == ncModeVal }
                if (ncMode != null) {
                    controller.confirmNcMode(ncMode)
                }
            }
        } else if (msgId == SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_CONTROL) {
            if (payload.isNotEmpty()) {
                val status = payload[0].toInt()
                if (status == 2) { // AttachSuccess
                    deviceState.isSpatialActive.value = true
                    Log.i(TAG, "Spatial Audio Sensor Attached")
                } else if (status == 3) { // DetachSuccess
                    deviceState.isSpatialActive.value = false
                    Log.i(TAG, "Spatial Audio Sensor Detached")
                }
            }
        } else if (msgId == SppPacketEncoder.MSG_ID_SPATIAL_AUDIO_DATA) {
            if (payload.isNotEmpty() && payload[0].toInt() == 32) { // BudGrv event
                if (payload.size >= 9) {
                    val buffer = ByteBuffer.wrap(payload, 1, 8).order(ByteOrder.LITTLE_ENDIAN)
                    val x = buffer.short / 10000.0f
                    val y = buffer.short / 10000.0f
                    val z = buffer.short / 10000.0f
                    val w = buffer.short / 10000.0f
                    
                    var outX = x
                    var outY = y
                    var outZ = z
                    val outW = w

                    deviceState.rawSpatialDataFlow.tryEmit(QuaternionSample(System.currentTimeMillis(), x, y, z, w))

                    val currentModel = controller.effectiveModel.value
                    if (currentModel == BudsModel.BUDS_2 || currentModel == BudsModel.BUDS_2_PRO) {
                        var tempY = -z
                        var tempZ = y
                        
                        if (!controller.invertPitch.value) {
                            tempY = z
                            tempZ = -y
                        }

                        outY = tempY
                        outZ = tempZ
                    }

                    if (controller.invertPitch.value) {
                        outX = -outX
                        outZ = -outZ
                    }
                    deviceState.spatialDataFlow.tryEmit(QuaternionSample(System.currentTimeMillis(), outX, outY, outZ, outW, x, y, z, w))
                }
            }
        }
    }
}
