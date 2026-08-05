package com.benegedeniz.budsdynamiceq.ui.headshake
import com.benegedeniz.budsdynamiceq.R

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.data.GestureRepository
import com.benegedeniz.budsdynamiceq.data.model.FlowAction
import com.benegedeniz.budsdynamiceq.data.model.GestureAction
import com.benegedeniz.budsdynamiceq.data.model.HeadGesture
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import com.benegedeniz.budsdynamiceq.gesture.DtwEngine
import com.benegedeniz.budsdynamiceq.gesture.GestureDetector
import com.benegedeniz.budsdynamiceq.gesture.QuaternionSample
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.sqrt

enum class RecordingState {
    IDLE,
    SETUP,
    READY_FOR_SAMPLE,
    COUNTDOWN,
    RECORDING,
    CONTINUOUS_RECORDING,
    SAMPLE_DONE,
    ALL_RECORDED,
    TESTING,
    SAVING
}

class HeadShakeViewModel(application: Application) : AndroidViewModel(application) {

    private val gestureRepo = ServiceLocator.provideGestureRepository(application)
    private val budsController = ServiceLocator.provideBudsController(application)
    private val prefs = application.getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE)

    val gestures: StateFlow<List<HeadGesture>> = gestureRepo.gestures
    val isConnected: StateFlow<Boolean> = budsController.isConnected

    val activeImuSide: StateFlow<BudsController.ImuSide> = budsController.activeImuSide
    val activeImuReason: StateFlow<String> = budsController.activeImuReason
    val invertPitch: StateFlow<Boolean> = budsController.invertPitch
    
    private val globalDetector = ServiceLocator.provideGestureDetector(application)
    private val globalNoiseDetector = ServiceLocator.provideNoiseDetector(application)

    private val _lastDetectedGesture = MutableStateFlow<HeadGesture?>(null)
    val lastDetectedGesture: StateFlow<HeadGesture?> = _lastDetectedGesture.asStateFlow()

    private val _headShakeEnabled = MutableStateFlow(prefs.getBoolean("head_shake_enabled", false))

    private val _requireBothEarbuds = MutableStateFlow(prefs.getBoolean("require_both_earbuds", false))
    val requireBothEarbuds: StateFlow<Boolean> = _requireBothEarbuds.asStateFlow()

    fun toggleRequireBothEarbuds(enabled: Boolean) {
        prefs.edit().putBoolean("require_both_earbuds", enabled).apply()
        _requireBothEarbuds.value = enabled
        ServiceLocator.setRequireBothEarbuds(enabled)
    }
    
    val isMissingEarbudForHeadshake = combine(_requireBothEarbuds, budsController.placementL, budsController.placementR) { req, pL, pR ->
        req && (pL != com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING || pR != com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), false)

    fun forceHeadshakeOn() {
        if (isMissingEarbudForHeadshake.value) {
            toggleRequireBothEarbuds(false)
        }
        if (!_headShakeEnabled.value) {
            toggleHeadShake(true)
        }
    }
    
    val isUiLocked = combine(isMissingEarbudForHeadshake, _headShakeEnabled) { missing, enabled ->
        missing || !enabled
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), false)

    private val ttsManager = com.benegedeniz.budsdynamiceq.gesture.TtsManager(application)

    val headShakeEnabled: StateFlow<Boolean> = _headShakeEnabled.asStateFlow()

    private val _spatialAudioConflict = MutableStateFlow(false)
    val spatialAudioConflict: StateFlow<Boolean> = _spatialAudioConflict.asStateFlow()

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _currentRecordingIndex = MutableStateFlow(0)
    val currentRecordingIndex: StateFlow<Int> = _currentRecordingIndex.asStateFlow()

    private val _recordedTemplates = MutableStateFlow<List<List<QuaternionSample>>>(emptyList())
    val recordedTemplates: StateFlow<List<List<QuaternionSample>>> = _recordedTemplates.asStateFlow()

    private val _editingGesture = MutableStateFlow<HeadGesture?>(null)
    val editingGesture: StateFlow<HeadGesture?> = _editingGesture.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(3)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _recordingProgress = MutableStateFlow(0f)
    val recordingProgress: StateFlow<Float> = _recordingProgress.asStateFlow()

    private val _currentMotionIntensity = MutableStateFlow(0f)
    val currentMotionIntensity: StateFlow<Float> = _currentMotionIntensity.asStateFlow()

    private val detector = GestureDetector(viewModelScope)

    private var recordingJob: Job? = null
    private var testJob: Job? = null
    private val currentSamples = mutableListOf<QuaternionSample>()
    private var lastSample: QuaternionSample? = null

    init {
        viewModelScope.launch {
            gestureRepo.loadGestures()
        }
        viewModelScope.launch {
            globalDetector.detectedGesture.collect { gesture ->
                _lastDetectedGesture.value = gesture
                delay(2000)
                if (_lastDetectedGesture.value == gesture) {
                    _lastDetectedGesture.value = null
                }
            }
        }
        // Show noise matches in the live preview overlay
        viewModelScope.launch {
            globalNoiseDetector.matchedProfile.collect { profile ->
                if (profile != null) {
                    _lastDetectedGesture.value = profile
                } else if (_lastDetectedGesture.value?.isNoiseProfile == true) {
                    _lastDetectedGesture.value = null
                }
            }
        }
    }

    fun toggleHeadShake(enabled: Boolean) {
        prefs.edit().putBoolean("head_shake_enabled", enabled).apply()
        _headShakeEnabled.value = enabled
        ServiceLocator.setHeadShakeEnabled(enabled)
        if (enabled) {
            detector.start(emptyList())
        } else {
            detector.stop()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
        detector.stop()
    }

    fun checkSpatialSensorAvailability() {
        viewModelScope.launch {
            if (!budsController.isConnected.value) {
                _spatialAudioConflict.value = false
                return@launch
            }
            if (budsController.isSpatialActive.value) {
                _spatialAudioConflict.value = false
                return@launch
            }
            budsController.startSpatialSensor("availability_check")
            val sampleReceived = withTimeoutOrNull(6000) {
                budsController.spatialDataFlow.first()
            }
            if (sampleReceived == null) {
                _spatialAudioConflict.value = true
                if (_headShakeEnabled.value) {
                    toggleHeadShake(false)
                }
            } else {
                _spatialAudioConflict.value = false
            }
            budsController.stopSpatialSensor("availability_check")
        }
    }

    fun deleteGesture(id: String) {
        viewModelScope.launch {
            gestureRepo.deleteGesture(id)
        }
    }

    fun toggleGesture(gesture: HeadGesture, enabled: Boolean) {
        viewModelScope.launch {
            gestureRepo.updateGesture(gesture.copy(enabled = enabled))
        }
    }

    fun updateGestureAction(gesture: HeadGesture, newAction: GestureAction) {
        viewModelScope.launch {
            gestureRepo.updateGesture(gesture.copy(actions = listOf(FlowAction.SystemAction(newAction))))
        }
    }

    // --- Recording Flow ---

    private val _isRecordingNoise = MutableStateFlow(false)
    val isRecordingNoise: StateFlow<Boolean> = _isRecordingNoise.asStateFlow()

    private val _blockGesturesOnMatch = MutableStateFlow(true)
    val blockGesturesOnMatch: StateFlow<Boolean> = _blockGesturesOnMatch.asStateFlow()
    private val _gestureName = MutableStateFlow("")
    private val _selectedAction = MutableStateFlow<FlowAction?>(null)

    var isMovementCancellingScreenOpen by androidx.compose.runtime.mutableStateOf(false)

    fun startNewGesture() {
        _editingGesture.value = null
        _isRecordingNoise.value = false
        _recordingState.value = RecordingState.SETUP
        _currentRecordingIndex.value = 0
        _recordedTemplates.value = emptyList()
        globalDetector.isTrainingMode = true
    }

    fun startNewNoiseProfileSetup() {
        _editingGesture.value = null
        _isRecordingNoise.value = true
        _recordingState.value = RecordingState.SETUP
        _currentRecordingIndex.value = 0
        _recordedTemplates.value = emptyList()
        globalDetector.isTrainingMode = true
    }

    fun setupNewNoiseProfile(name: String, blockGestures: Boolean) {
        viewModelScope.launch {
            _gestureName.value = name
            _selectedAction.value = null
            _editingGesture.value = null
            _isRecordingNoise.value = true
            _blockGesturesOnMatch.value = blockGestures
            _recordedTemplates.value = emptyList()
            _currentRecordingIndex.value = 0
            
            val wasActive = budsController.isSpatialActive.value
            if (!wasActive) budsController.startSpatialSensor("recording_setup")

            _recordingState.value = RecordingState.READY_FOR_SAMPLE
        }
    }

    fun improveDetection(gesture: HeadGesture) {
        _editingGesture.value = gesture
        _gestureName.value = gesture.name
        _selectedAction.value = gesture.actions.firstOrNull()
        _isRecordingNoise.value = gesture.isNoiseProfile
        _blockGesturesOnMatch.value = gesture.blockGesturesOnMatch
        _recordedTemplates.value = emptyList()
        _currentRecordingIndex.value = 0
        globalDetector.isTrainingMode = true
        startRecordingSetup()
    }

    fun startRecordingSetup() {
        if (!budsController.isConnected.value) return
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            budsController.startSpatialSensor("training_session")
            _recordingState.value = RecordingState.READY_FOR_SAMPLE
        }
    }

    fun startContinuousRecording() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            consistencyWarning.value = null
            val wasActive = budsController.isSpatialActive.value
            if (!wasActive) budsController.startSpatialSensor("recording_noise")

            _recordingState.value = RecordingState.CONTINUOUS_RECORDING
            _recordingProgress.value = 0f
            currentSamples.clear()

            val collectJob = launch {
                budsController.spatialDataFlow.collect { sample ->
                    currentSamples.add(sample)
                }
            }

            // Record for up to 30 seconds
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 30000L) {
                _recordingProgress.value = (System.currentTimeMillis() - startTime) / 30000f
                kotlinx.coroutines.delay(100)
            }
            
            collectJob.cancel()
            stopContinuousRecording()
        }
    }

    fun stopContinuousRecording() {
        if (_recordingState.value != RecordingState.CONTINUOUS_RECORDING) return
        
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            _recordingState.value = RecordingState.SAVING
            
            // Process the collected samples into 1s overlapping windows
            val samples = currentSamples.toList()
            val templates = mutableListOf<List<com.benegedeniz.budsdynamiceq.gesture.QuaternionSample>>()
            
            if (samples.isNotEmpty()) {
                val durationMs = samples.last().timestampMs - samples.first().timestampMs
                if (durationMs > 1000L) {
                    val windowSizeMs = 1000L
                    val stepSizeMs = 500L
                    var windowStart = samples.first().timestampMs
                    
                    while (windowStart + windowSizeMs <= samples.last().timestampMs) {
                        val window = samples.filter { it.timestampMs in windowStart..(windowStart + windowSizeMs) }
                        
                        // For noise profiles, we want to capture even subtle repeating movements
                        if (window.size >= 10) {
                            val amp = com.benegedeniz.budsdynamiceq.gesture.DtwEngine.getRawAmplitude(window)
                            if (amp > 0.05f) { // Lower threshold for noise
                                templates.add(window)
                            }
                        }
                        windowStart += stepSizeMs
                    }
                }
            }
            
            _recordedTemplates.value = templates
            
            if (templates.isEmpty()) {
                // Not enough movement, just return to ready state with a warning
                _recordingState.value = RecordingState.READY_FOR_SAMPLE
                consistencyWarning.value = getApplication<Application>().getString(R.string.no_significant_movement_detected)
                return@launch
            }
            
            saveNoiseProfile()
        }
    }

    fun startVisualizer() {
        budsController.startSpatialSensor("visualizer")
    }

    fun stopVisualizer() {
        budsController.stopSpatialSensor("visualizer")
    }


    fun startRecording() {
        recordingJob?.cancel()
        conflictWarning.value = null
        recordingJob = viewModelScope.launch {
            // Check if spatial sensor is already active (could be if head shake is enabled globally)
            // If not, we temporarily activate it for recording
            val wasActive = budsController.isSpatialActive.value
            if (!wasActive) budsController.startSpatialSensor("recording")

            _recordingState.value = RecordingState.RECORDING
            _recordingProgress.value = 0f
            currentSamples.clear()
            lastSample = null

            detector.start(emptyList())

            // Start collecting data and feeding to detector
            val collectJob = launch {
                budsController.spatialDataFlow.collect { sample ->
                    detector.feedSample(sample)
                    
                    val s = lastSample
                    if (s != null) {
                        val dt = (sample.timestampMs - s.timestampMs) / 1000f
                        if (dt > 0) {
                            val dqx = (sample.x - s.x) / dt
                            val dqy = (sample.y - s.y) / dt
                            val dqz = (sample.z - s.z) / dt
                            _currentMotionIntensity.value = sqrt(dqx*dqx + dqy*dqy + dqz*dqz)
                        }
                    }
                    lastSample = sample
                }
            }

            // Wait for the first actual motion segment
            val motionSegment = detector.motionSegment.first()

            collectJob.cancel()
            detector.stop()
            _recordingProgress.value = 1f
            _currentMotionIntensity.value = 0f

            val templates = _recordedTemplates.value.toMutableList()
            if (_currentRecordingIndex.value < templates.size) {
                templates[_currentRecordingIndex.value] = motionSegment.toList()
            } else {
                templates.add(motionSegment.toList())
            }
            _recordedTemplates.value = templates

            if (_currentRecordingIndex.value > 0 && _currentRecordingIndex.value < templates.size) {
                val prevIndex = _currentRecordingIndex.value - 1
                val newFeatures = DtwEngine.preprocess(templates[_currentRecordingIndex.value])
                val refFeatures = DtwEngine.preprocess(templates[prevIndex])
                val newDur = templates[_currentRecordingIndex.value].last().timestampMs - templates[_currentRecordingIndex.value].first().timestampMs
                val refDur = templates[prevIndex].last().timestampMs - templates[prevIndex].first().timestampMs
                val dist = DtwEngine.computeDtw(refFeatures, newFeatures, refDur, newDur)
                if (dist > 0.75f) {
                    consistencyWarning.value = getApplication<Application>().getString(R.string.gesture_looks_different)
                } else {
                    consistencyWarning.value = null
                }
            } else {
                consistencyWarning.value = null
            }

            // Check for conflict against other gestures for this sample
            val gesturesToCheck = gestures.value.filter { it.id != _editingGesture.value?.id }
            val conflict = DtwEngine.checkConflict(listOf(motionSegment.toList()), gesturesToCheck)
            if (conflict.hasConflict) {
                conflictWarning.value = conflict
            }

            if (!wasActive && _recordingState.value != RecordingState.TESTING) {
                // Keep it on if we're moving to test phase, but detach if they cancel
                // We'll manage it better in test or cancel.
            }

            val targetCount = if (_editingGesture.value != null) 3 else 5
            _recordingState.value = RecordingState.SAMPLE_DONE
        }
    }

    val consistencyWarning = MutableStateFlow<String?>(null)

    fun redoLastRecording() {
        startRecording()
    }

    fun nextSample() {
        val targetCount = if (_editingGesture.value != null) 3 else 5
        if (_currentRecordingIndex.value < targetCount - 1) {
            _currentRecordingIndex.value++
            startRecording()
        } else {
            _recordingState.value = RecordingState.ALL_RECORDED
        }
    }
    
    fun redoAll() {
        _currentRecordingIndex.value = 0
        _recordedTemplates.value = emptyList()
        _recordingState.value = RecordingState.READY_FOR_SAMPLE
        consistencyWarning.value = null
    }

    val testResult = MutableStateFlow<Int?>(null)

    fun testGesture() {
        _recordingState.value = RecordingState.TESTING
        testResult.value = null
        
        budsController.startSpatialSensor("testing")

        testJob?.cancel()
        testJob = viewModelScope.launch {
            val templatesToTest = if (_editingGesture.value != null) {
                _editingGesture.value!!.templates + _recordedTemplates.value
            } else {
                _recordedTemplates.value
            }
            
            val tempGesture = HeadGesture(name = "Temp", actions = emptyList(), templates = templatesToTest)
            detector.start(listOf(tempGesture))

            val collectJob = launch {
                budsController.spatialDataFlow.collect { sample ->
                    detector.feedSample(sample)
                }
            }

            val matchJob = launch {
                detector.detectedGesture.collect { match ->
                    if (match.name == "Temp") {
                        testResult.value = R.string.detected
                        delay(2000)
                        testResult.value = null
                    }
                }
            }
        }
    }

    fun stopTesting() {
        testJob?.cancel()
        detector.stop()
        budsController.stopSpatialSensor("testing")
        _recordingState.value = RecordingState.ALL_RECORDED
    }

    val conflictWarning = MutableStateFlow<DtwEngine.ConflictResult?>(null)
    
    fun dismissConflictWarning() {
        conflictWarning.value = null
    }

    fun saveGesture(name: String, action: FlowAction, ignoreConflict: Boolean = false) {
        viewModelScope.launch {
            val templatesToTest = if (_editingGesture.value != null) {
                _editingGesture.value!!.templates + _recordedTemplates.value
            } else {
                _recordedTemplates.value
            }

            if (!ignoreConflict) {
                val gesturesToCheck = gestures.value.filter { it.id != _editingGesture.value?.id }
                val conflict = DtwEngine.checkConflict(templatesToTest, gesturesToCheck)
                if (conflict.hasConflict) {
                    conflictWarning.value = conflict
                    return@launch
                }
            }

            val editing = _editingGesture.value
            if (editing != null) {
                val updatedGesture = editing.copy(
                    templates = templatesToTest
                )
                gestureRepo.updateGesture(updatedGesture)
            } else {
                val newGesture = HeadGesture(
                    name = name,
                    actions = listOf(action),
                    templates = templatesToTest
                )
                gestureRepo.addGesture(newGesture)
            }
            cancelRecording()
        }
    }

    private fun saveNoiseProfile() {
        viewModelScope.launch {
            val allSamples = currentSamples.toList()
            val templates = _recordedTemplates.value
            
            // Compute the statistical noise fingerprint from all collected samples
            val fingerprint = com.benegedeniz.budsdynamiceq.gesture.NoiseFingerprint.compute(allSamples)
            
            val editing = _editingGesture.value
            if (editing != null) {
                // Improve Detection: merge new templates and recompute fingerprint
                val mergedTemplates = editing.templates + templates
                // Recompute fingerprint from all raw samples in templates
                val allTemplateSamples = mergedTemplates.flatten()
                val updatedFingerprint = com.benegedeniz.budsdynamiceq.gesture.NoiseFingerprint.compute(allTemplateSamples) ?: fingerprint
                val updatedGesture = editing.copy(
                    templates = mergedTemplates,
                    noiseFingerprint = updatedFingerprint
                )
                gestureRepo.updateGesture(updatedGesture)
            } else {
                val newGesture = HeadGesture(
                    name = _gestureName.value,
                    actions = emptyList(),
                    templates = templates,
                    isNoiseProfile = true,
                    blockGesturesOnMatch = _blockGesturesOnMatch.value,
                    noiseFingerprint = fingerprint
                )
                gestureRepo.addGesture(newGesture)
            }
            cancelRecording()
        }
    }

    fun updateNoiseProfileSettings(name: String, blockGestures: Boolean) {
        _gestureName.value = name
        _blockGesturesOnMatch.value = blockGestures
    }

    fun updateGestureNameAndActions(id: String, name: String, actions: List<FlowAction>, playChime: Boolean) {
        viewModelScope.launch {
            val gesture = gestures.value.find { it.id == id } ?: return@launch
            val updated = gesture.copy(name = name, actions = actions, playChime = playChime)
            gestureRepo.updateGesture(updated)
        }
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        testJob?.cancel()
        detector.stop()
        globalDetector.isTrainingMode = false
        budsController.stopSpatialSensor("recording")
        budsController.stopSpatialSensor("testing")
        budsController.stopSpatialSensor("training_session")
        
        _recordingState.value = RecordingState.IDLE
        _recordedTemplates.value = emptyList()
        _currentRecordingIndex.value = 0
        _recordingProgress.value = 0f
        conflictWarning.value = null
        _editingGesture.value = null
    }

    // Spatial flow proxy for UI visualizer
    val spatialDataFlow = budsController.spatialDataFlow
}
