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
import com.benegedeniz.budsdynamiceq.ui.state.RulesUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.provideRulesRepository(application)
    private val budsController = ServiceLocator.provideBudsController(application)

    var isEditScreenOpen by androidx.compose.runtime.mutableStateOf(false)

    val uiState: StateFlow<RulesUiState> = combine(
        combine(
            repository.rules,
            budsController.isConnected,
            budsController.effectiveModel,
            ServiceLocator.provideMediaObserver(application).currentMetadata
        ) { r, conn, mod, meta ->
            RulesGroup1(r, conn, mod, meta)
        },
        combine(
            ServiceLocator.provideMediaObserver(application).recentHistory,
            budsController.manualPreset,
            budsController.manualNoiseControl,
            budsController.lastMatchedRule
        ) { hist, mPre, mNc, lMatch ->
            RulesGroup2(hist, mPre, mNc, lMatch)
        }
    ) { group1, group2 ->
        RulesUiState(
            rules = group1.rules,
            isConnected = group1.conn,
            effectiveModel = group1.mod,
            currentMetadata = group1.meta,
            recentHistory = group2.hist,
            manualPreset = group2.mPre,
            manualNoiseControl = group2.mNc,
            lastMatchedRule = group2.lMatch
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RulesUiState()
    )

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

        // Keep ServiceLocator in sync in case RulesViewModel is created before BudsService calls initFromPrefs
        val prefs = application.getSharedPreferences("BudsPrefs", android.content.Context.MODE_PRIVATE)
        _pauseMediaOnConversationEnabled.value = prefs.getBoolean("pause_media_on_conversation", false)
        // Keep ServiceLocator in sync in case RulesViewModel is created before BudsService calls initFromPrefs
        ServiceLocator.setPauseMediaOnConversation(_pauseMediaOnConversationEnabled.value)
        
        if (budsController.manualPreset.value == null) {
            val savedPresetName = prefs.getString("default_preset", null)
            val presetToSet = savedPresetName?.let {
                try { EqPreset.valueOf(it) } catch (_: IllegalArgumentException) { null }
            } ?: EqPreset.NORMAL
            budsController.setManualPreset(presetToSet)
            if (savedPresetName == null) prefs.edit().putString("default_preset", EqPreset.NORMAL.name).apply()
        }
        if (budsController.manualNoiseControl.value == null) {
            val savedNcName = prefs.getString("default_nc", null)
            val ncToSet = savedNcName?.let {
                try { NoiseControlMode.valueOf(it) } catch (_: IllegalArgumentException) { null }
            } ?: NoiseControlMode.IGNORE
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
            val currentRules = uiState.value.rules
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
            val currentList = uiState.value.rules.toMutableList()
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

    fun setDoubleTapEdgeEnabled(enabled: Boolean) {
        budsController.setDoubleTapEdgeEnabled(enabled)
    }

    fun setStereoBalance(value: Int) {
        budsController.setStereoBalance(value)
    }

    fun setPauseMediaOnConversation(enabled: Boolean) {
        _pauseMediaOnConversationEnabled.value = enabled
        ServiceLocator.setPauseMediaOnConversation(enabled)
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

}

// Internal data classes for grouped combine
private data class RulesGroup1(
    val rules: List<EqRule>,
    val conn: Boolean,
    val mod: com.benegedeniz.budsdynamiceq.bluetooth.BudsModel,
    val meta: com.benegedeniz.budsdynamiceq.media.SongMetadata?
)
private data class RulesGroup2(
    val hist: List<com.benegedeniz.budsdynamiceq.media.SongMetadata>,
    val mPre: com.benegedeniz.budsdynamiceq.data.model.EqPreset?,
    val mNc: com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode?,
    val lMatch: EqRule?
)
