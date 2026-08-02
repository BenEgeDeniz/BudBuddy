package com.benegedeniz.budsdynamiceq.ui.rules

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.data.RulesRepository
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.EqRule
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.model.PlacementState
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import com.benegedeniz.budsdynamiceq.media.MediaObserver
import com.benegedeniz.budsdynamiceq.rules.RulesEngine
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.benegedeniz.budsdynamiceq.data.model.FitTestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RulesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.provideRulesRepository(application)
    private val budsController = ServiceLocator.provideBudsController(application)

    val rules: StateFlow<List<EqRule>> = repository.rules
    var isEditScreenOpen by mutableStateOf(false)
    val currentMetadata: StateFlow<com.benegedeniz.budsdynamiceq.media.SongMetadata?> = ServiceLocator.provideMediaObserver(application).currentMetadata
    val recentHistory: StateFlow<List<com.benegedeniz.budsdynamiceq.media.SongMetadata>> = ServiceLocator.provideMediaObserver(application).recentHistory
    val isConnected: StateFlow<Boolean> = budsController.isConnected
    val isConnecting: StateFlow<Boolean> = budsController.isConnecting
    val savedDeviceMac: StateFlow<String?> = budsController.savedDeviceMac

    private val _pairedDevices = MutableStateFlow<List<android.bluetooth.BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<android.bluetooth.BluetoothDevice>> = _pairedDevices.asStateFlow()

    val lastMatchedRule: StateFlow<EqRule?> = budsController.lastMatchedRule
    val manualPreset: StateFlow<EqPreset?> = budsController.manualPreset
    val manualNoiseControl: StateFlow<NoiseControlMode?> = budsController.manualNoiseControl
    val activeNoiseControl: StateFlow<NoiseControlMode?> = budsController.activeNoiseControl

    val batteryL: StateFlow<Int> = budsController.batteryL
    val batteryR: StateFlow<Int> = budsController.batteryR
    val batteryCase: StateFlow<Int> = budsController.batteryCase
    val placementL: StateFlow<PlacementState> = budsController.placementL
    val placementR: StateFlow<PlacementState> = budsController.placementR

    val chargingL: StateFlow<Boolean> = budsController.chargingL
    val chargingR: StateFlow<Boolean> = budsController.chargingR
    val chargingCase: StateFlow<Boolean> = budsController.chargingCase
    val temperatureL: StateFlow<Double?> = budsController.temperatureL
    val temperatureR: StateFlow<Double?> = budsController.temperatureR

    val conversationDetectionEnabled: StateFlow<Boolean> = budsController.conversationDetectionEnabled
    val oneEarbudNoiseControlEnabled: StateFlow<Boolean> = budsController.oneEarbudNoiseControlEnabled
    val useAmbientSoundDuringCalls: StateFlow<Boolean> = budsController.useAmbientSoundDuringCalls
    val inEarDetectionForCalls: StateFlow<Boolean> = budsController.inEarDetectionForCalls
    val fitTestResultL: StateFlow<FitTestResult> = budsController.fitTestResultL
    val fitTestResultR: StateFlow<FitTestResult> = budsController.fitTestResultR
    
    val connectedModel: StateFlow<com.benegedeniz.budsdynamiceq.bluetooth.BudsModel> = budsController.connectedModel
    val modelOverride: StateFlow<com.benegedeniz.budsdynamiceq.bluetooth.BudsModel?> = budsController.modelOverride
    val effectiveModel: StateFlow<com.benegedeniz.budsdynamiceq.bluetooth.BudsModel> = budsController.effectiveModel

    fun setModelOverride(model: com.benegedeniz.budsdynamiceq.bluetooth.BudsModel?) {
        budsController.setModelOverride(model)
    }
    
    fun setHomePageVisible(visible: Boolean) {
        budsController.isHomePageVisible.value = visible
    }

    private val _pauseMediaOnConversationEnabled = MutableStateFlow(false)
    val pauseMediaOnConversationEnabled: StateFlow<Boolean> = _pauseMediaOnConversationEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            repository.loadRules()
        }

        // Just fetch paired devices for the UI
        _pairedDevices.value = budsController.getPairedDevices()

        // Load defaults for the UI
        val prefs = application.getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE)
        _pauseMediaOnConversationEnabled.value = prefs.getBoolean("pause_media_on_conversation", false)
        
        if (budsController.manualPreset.value == null) {
            val savedPresetName = prefs.getString("default_preset", null)
            val presetToSet = if (savedPresetName != null) EqPreset.valueOf(savedPresetName) else EqPreset.NORMAL
            budsController.setManualPreset(presetToSet)
            if (savedPresetName == null) prefs.edit().putString("default_preset", EqPreset.NORMAL.name).apply()
        }
        if (budsController.manualNoiseControl.value == null) {
            val savedNcName = prefs.getString("default_nc", null)
            val ncToSet = if (savedNcName != null) NoiseControlMode.valueOf(savedNcName) else NoiseControlMode.IGNORE
            budsController.setManualNoiseControl(ncToSet)
            if (savedNcName == null) prefs.edit().putString("default_nc", NoiseControlMode.IGNORE.name).apply()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // No longer disconnects on cleared. The service manages connection lifecycle.
    }

    fun addRule(keyword: String, preset: EqPreset, ncMode: NoiseControlMode) {
        viewModelScope.launch {
            val currentRules = rules.value
            val nextPriority = (currentRules.maxOfOrNull { it.priority } ?: 0) + 1
            repository.addRule(EqRule(keyword = keyword, preset = preset, noiseControl = ncMode, priority = nextPriority))
        }
    }

    fun updateRule(rule: EqRule) {
        viewModelScope.launch {
            repository.updateRule(rule)
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch {
            repository.deleteRule(id)
        }
    }

    fun toggleRule(rule: EqRule, enabled: Boolean) {
        updateRule(rule.copy(enabled = enabled))
    }

    fun reorderRules(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val currentList = rules.value.toMutableList()
            if (fromIndex in currentList.indices && toIndex in currentList.indices) {
                val item = currentList.removeAt(fromIndex)
                currentList.add(toIndex, item)
                
                // Reassign priorities based on new order
                val updatedList = currentList.mapIndexed { index, rule ->
                    rule.copy(priority = index + 1)
                }
                repository.saveRules(updatedList)
            }
        }
    }

    fun updateRulesOrder(newRulesOrder: List<EqRule>) {
        viewModelScope.launch {
            val updatedList = newRulesOrder.mapIndexed { index, rule ->
                rule.copy(priority = index + 1)
            }
            repository.saveRules(updatedList)
        }
    }

    fun setManualPreset(preset: EqPreset) {
        budsController.setManualPreset(preset)
        
        // Save to SharedPreferences
        val prefs = getApplication<Application>().getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("default_preset", preset.name).apply()

        // Only apply it to Buds if there's no active rule matching right now
        if (budsController.lastMatchedRule.value == null) {
            budsController.sendEqualizer(preset)
        }
    }

    fun setManualNoiseControl(ncMode: NoiseControlMode) {
        budsController.setManualNoiseControl(ncMode)
        
        // Save to SharedPreferences
        val prefs = getApplication<Application>().getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("default_nc", ncMode.name).apply()

        // Only apply it to Buds if there's no active rule matching right now
        if (budsController.lastMatchedRule.value == null) {
            budsController.sendNoiseControl(ncMode)
        }
    }

    fun applyImmediateNoiseControl(ncMode: NoiseControlMode) {
        budsController.sendNoiseControl(ncMode)
    }

    fun setConversationDetection(enabled: Boolean) {
        budsController.setConversationDetection(enabled)
    }

    fun setOneEarbudNoiseControl(enabled: Boolean) {
        budsController.setOneEarbudNoiseControl(enabled)
    }

    fun setUseAmbientSoundDuringCalls(enabled: Boolean) {
        budsController.setUseAmbientSoundDuringCalls(enabled)
    }

    fun setInEarDetectionForCalls(enabled: Boolean) {
        budsController.setInEarDetectionForCalls(enabled)
    }

    fun setPauseMediaOnConversation(enabled: Boolean) {
        _pauseMediaOnConversationEnabled.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pause_media_on_conversation", enabled).apply()
    }

    fun startFitTest() {
        budsController.startFitTest()
    }

    fun stopFitTest() {
        budsController.stopFitTest()
    }

    fun setFitTestScreenOpen(isOpen: Boolean) {
        budsController.setFitTestScreenOpen(isOpen)
    }

    fun connectToDevice(device: android.bluetooth.BluetoothDevice) {
        budsController.connect(device)
        
        val app = getApplication<Application>()
        val serviceIntent = android.content.Intent(app, com.benegedeniz.budsdynamiceq.service.BudsService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            app.startForegroundService(serviceIntent)
        } else {
            app.startService(serviceIntent)
        }
    }

    fun disconnect(forget: Boolean = false) {
        budsController.disconnect(forget = forget)
        if (forget) {
            val app = getApplication<Application>()
            val serviceIntent = android.content.Intent(app, com.benegedeniz.budsdynamiceq.service.BudsService::class.java)
            app.stopService(serviceIntent)
        }
    }

    fun isBluetoothEnabled(): Boolean = budsController.isBluetoothEnabled()

    fun startAutoConnect() {
        budsController.startAutoConnect()
    }

    fun refreshPairedDevices() {
        _pairedDevices.value = budsController.getPairedDevices()
    }
}
