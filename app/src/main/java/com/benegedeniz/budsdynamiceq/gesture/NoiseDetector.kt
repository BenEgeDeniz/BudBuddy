package com.benegedeniz.budsdynamiceq.gesture

import android.util.Log
import com.benegedeniz.budsdynamiceq.data.model.HeadGesture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Continuous noise pattern detector that runs in parallel to [GestureDetector].
 *
 * Instead of waiting for a still→motion transition like gesture detection,
 * this maintains a sliding window over the live IMU stream and continuously
 * compares it against registered noise fingerprints using statistical features
 * (angular velocity, frequency, axis distribution, variance).
 *
 * When a noise match is detected, it triggers a cooldown on [GestureDetector]
 * to suppress false gesture triggers caused by background movement.
 */
class NoiseDetector(
    private val scope: CoroutineScope,
    private val gestureDetector: GestureDetector
) {
    companion object {
        private const val TAG = "NoiseDetector"
        
        /** Size of the sliding window in milliseconds */
        private const val WINDOW_SIZE_MS = 1000L
        
        /** How often we evaluate the window (ms) */
        private const val EVAL_INTERVAL_MS = 150L
        
        /** Distance threshold — below this we consider it a noise match */
        private const val MATCH_THRESHOLD = 2.5f
        
        // If no match is found, hold the last matched state for this duration to bridge small gaps
        private const val TAIL_COOLDOWN_MS = 1000L
    }

    private val slidingBuffer = ArrayDeque<QuaternionSample>()
    private var noiseProfiles = emptyList<HeadGesture>()
    private val mutex = Mutex()
    private var lastEvalTime = 0L
    private var lastMatchTime = 0L
    
    private var potentialMatchId: String? = null
    private var potentialMatchStartTime = 0L
    private var lastPotentialMatchTime = 0L

    private val sampleChannel = kotlinx.coroutines.channels.Channel<QuaternionSample>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.Default) {
            for (sample in sampleChannel) {
                mutex.withLock {
                    processSample(sample)
                }
            }
        }
    }

    /** The currently matched noise profile, or null if no noise is detected */
    private val _matchedProfile = MutableStateFlow<HeadGesture?>(null)
    val matchedProfile: StateFlow<HeadGesture?> = _matchedProfile.asStateFlow()

    /** Emitted each time a noise match is first detected (for notifications) */
    private val _noiseDetected = MutableSharedFlow<HeadGesture>(extraBufferCapacity = 5)
    val noiseDetected: SharedFlow<HeadGesture> = _noiseDetected.asSharedFlow()

    /**
     * Start or update the detector with the current set of noise profiles.
     * Only profiles with a computed [NoiseFingerprint] will be used.
     */
    fun start(profiles: List<HeadGesture>) {
        scope.launch {
            mutex.withLock {
                noiseProfiles = profiles.filter {
                    it.isNoiseProfile && it.enabled && it.noiseFingerprint != null
                }
                slidingBuffer.clear()
                _matchedProfile.value = null
                lastEvalTime = 0L
                lastMatchTime = 0L
                potentialMatchId = null
                potentialMatchStartTime = 0L
                lastPotentialMatchTime = 0L
                Log.d(TAG, "Started with ${noiseProfiles.size} noise profiles")
            }
        }
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                noiseProfiles = emptyList()
                slidingBuffer.clear()
                _matchedProfile.value = null
                potentialMatchId = null
            }
        }
    }

    /**
     * Feed a sample into the sliding window. Called from the same spatial data flow
     * that feeds [GestureDetector].
     */
    fun feedSample(sample: QuaternionSample) {
        sampleChannel.trySend(sample)
    }

    private fun processSample(sample: QuaternionSample) {
        if (noiseProfiles.isEmpty()) return

        slidingBuffer.addLast(sample)

        // Trim buffer to window size
        while (slidingBuffer.isNotEmpty() &&
            sample.timestampMs - slidingBuffer.first().timestampMs > WINDOW_SIZE_MS + 200L
        ) {
            slidingBuffer.removeFirst()
        }

        // Only evaluate every EVAL_INTERVAL_MS
        if (sample.timestampMs - lastEvalTime < EVAL_INTERVAL_MS) return
        lastEvalTime = sample.timestampMs

        // Need at least a decent window to evaluate
        val windowDuration = if (slidingBuffer.size >= 2) {
            slidingBuffer.last().timestampMs - slidingBuffer.first().timestampMs
        } else 0L

        if (windowDuration < WINDOW_SIZE_MS * 0.7) return

        evaluateWindow(sample.timestampMs)
    }

    private fun evaluateWindow(currentTimeMs: Long) {
        val windowSamples = slidingBuffer.toList()
        val liveFingerprint = NoiseFingerprint.computeFromWindow(windowSamples) ?: return

        var bestMatch: HeadGesture? = null
        var bestDistance = Float.MAX_VALUE

        for (profile in noiseProfiles) {
            val fingerprint = profile.noiseFingerprint ?: continue
            val distance = fingerprint.distanceTo(liveFingerprint)
            Log.d(TAG, "  ${profile.name}: distance=$distance (threshold=$MATCH_THRESHOLD)")
            if (distance < bestDistance) {
                bestDistance = distance
                bestMatch = profile
            }
        }

        if (bestMatch != null && bestDistance < MATCH_THRESHOLD) {
            // We have a match in the current window
            if (potentialMatchId != bestMatch.id) {
                // New potential match started
                potentialMatchId = bestMatch.id
                potentialMatchStartTime = currentTimeMs
                lastPotentialMatchTime = currentTimeMs
            } else {
                // Sustained potential match
                lastPotentialMatchTime = currentTimeMs
            }

            // Only deem it an official match if it has been sustained for at least 500ms
            if (currentTimeMs - potentialMatchStartTime >= 500L) {
                val previousMatch = _matchedProfile.value
                _matchedProfile.value = bestMatch
                lastMatchTime = currentTimeMs

                // Apply cooldown to gesture detector to suppress false triggers
                gestureDetector.triggerCooldown(EVAL_INTERVAL_MS + TAIL_COOLDOWN_MS)

                // Only emit notification on new match (not on sustained match of same profile)
                if (previousMatch?.id != bestMatch.id) {
                    _noiseDetected.tryEmit(bestMatch)
                    Log.i(TAG, "Noise matched officially: ${bestMatch.name} (dist=$bestDistance)")
                }
            } else {
                Log.d(TAG, "Noise potential match: ${bestMatch.name} (dist=$bestDistance), waiting for 500ms sustain (current: ${currentTimeMs - potentialMatchStartTime}ms)")
            }
        } else {
            // No match this window
            // If it's been more than the tail cooldown since our last potential match, reset it
            if (potentialMatchId != null && currentTimeMs - lastPotentialMatchTime > TAIL_COOLDOWN_MS) {
                Log.d(TAG, "Noise potential match cleared (tail expired)")
                potentialMatchId = null
                potentialMatchStartTime = 0L
            }
            
            // Check if we should clear the official matched state
            if (_matchedProfile.value != null && currentTimeMs - lastMatchTime > TAIL_COOLDOWN_MS) {
                Log.d(TAG, "Noise official match cleared (tail expired)")
                _matchedProfile.value = null
            }
        }
    }
}
