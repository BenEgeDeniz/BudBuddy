package com.benegedeniz.budsdynamiceq.gesture

import com.benegedeniz.budsdynamiceq.data.model.HeadGesture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt
import kotlin.math.min

class GestureDetector(private val scope: CoroutineScope) {

    private val MOTION_START_THRESHOLD = 0.50f
    private val MOTION_END_THRESHOLD = 0.22f
    private val MAX_WINDOW_MS = 2500L
    private val END_DELAY_MS = 200L
    private val COOLDOWN_MS = 200L

    private val buffer = ArrayDeque<QuaternionSample>()
    private var activeGestures = emptyList<HeadGesture>()
    private val mutex = Mutex()

    private var state = State.IDLE
    private var motionStartTime = 0L
    private var lastMotionTime = 0L
    private var lastStillTime = 0L
    private var cooldownUntil = 0L

    private val _detectedGesture = MutableSharedFlow<HeadGesture>(extraBufferCapacity = 5)
    val detectedGesture: SharedFlow<HeadGesture> = _detectedGesture.asSharedFlow()
    
    private val _motionSegment = MutableSharedFlow<List<QuaternionSample>>(extraBufferCapacity = 5)
    val motionSegment: SharedFlow<List<QuaternionSample>> = _motionSegment.asSharedFlow()

    private var evalJob: Job? = null
    
    var isTrainingMode = false
    
    private var lastSmoothedSample: QuaternionSample? = null

    enum class State {
        IDLE, MOTION_DETECTED, COOLDOWN
    }

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

    fun start(gestures: List<HeadGesture>) {
        scope.launch {
            mutex.withLock {
                // Only include regular gestures — noise profiles are handled by NoiseDetector
                activeGestures = gestures.filter { !it.isNoiseProfile }
                buffer.clear()
                lastSmoothedSample = null
                state = State.IDLE
            }
        }
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                activeGestures = emptyList()
                buffer.clear()
                lastSmoothedSample = null
                state = State.IDLE
                evalJob?.cancel()
            }
        }
    }

    fun feedSample(sample: QuaternionSample) {
        sampleChannel.trySend(sample)
    }

    private fun processSample(sample: QuaternionSample) {
        val smoothedSample = if (lastSmoothedSample == null) {
                    sample
                } else {
                    val prev = lastSmoothedSample!!
                    val dt = (sample.timestampMs - prev.timestampMs) / 1000f
                    if (dt > 0.5f || dt <= 0f) {
                        sample
                    } else {
                        // Smooth factor: 45% new sample, 55% previous. Acts as a low-pass filter
                        // to neutralize small walking bobs from the gesture stream, but snappier.
                        val alpha = 0.45f
                        var nx = sample.x
                        var ny = sample.y
                        var nz = sample.z
                        var nw = sample.w
                        
                        // Ensure shortest path for Nlerp
                        val dot = prev.x * nx + prev.y * ny + prev.z * nz + prev.w * nw
                        if (dot < 0) {
                            nx = -nx; ny = -ny; nz = -nz; nw = -nw
                        }
                        
                        val qx = prev.x * (1 - alpha) + nx * alpha
                        val qy = prev.y * (1 - alpha) + ny * alpha
                        val qz = prev.z * (1 - alpha) + nz * alpha
                        val qw = prev.w * (1 - alpha) + nw * alpha
                        
                        val mag = kotlin.math.sqrt(qx*qx + qy*qy + qz*qz + qw*qw)
                        QuaternionSample(
                            timestampMs = sample.timestampMs,
                            x = qx / mag, y = qy / mag, z = qz / mag, w = qw / mag
                        )
                    }
                }
                lastSmoothedSample = smoothedSample
                
                buffer.addLast(smoothedSample)
                // keep approx 3 seconds of data max
                while (buffer.isNotEmpty() && sample.timestampMs - buffer.first().timestampMs > 3000L) {
                    buffer.removeFirst()
                }

                if (sample.timestampMs < cooldownUntil) {
                    state = State.COOLDOWN
                    return
                }

                if (state == State.COOLDOWN && sample.timestampMs >= cooldownUntil) {
                    state = State.IDLE
                }

                if (buffer.size >= 2) {
                    val current = buffer.last()
                    val prev = buffer[buffer.size - 2]
                    val dt = (current.timestampMs - prev.timestampMs) / 1000f
                    if (dt > 0) {
                        // Approximate angular velocity
                        val dqx = (current.x - prev.x) / dt
                        val dqy = (current.y - prev.y) / dt
                        val dqz = (current.z - prev.z) / dt
                        val mag = sqrt(dqx * dqx + dqy * dqy + dqz * dqz)
                        
                        if (mag <= MOTION_END_THRESHOLD) {
                            lastStillTime = current.timestampMs
                        }

                        when (state) {
                            State.IDLE -> {
                                if (mag > MOTION_START_THRESHOLD) {
                                    state = State.MOTION_DETECTED
                                    val actualStart = if (lastStillTime > 0) {
                                        // Start exactly at the moment they broke stillness, max 500ms lookback
                                        kotlin.math.max(lastStillTime, current.timestampMs - 500L)
                                    } else {
                                        current.timestampMs - 200L
                                    }
                                    motionStartTime = actualStart
                                    lastMotionTime = current.timestampMs
                                }
                            }
                            State.MOTION_DETECTED -> {
                                if (mag > MOTION_END_THRESHOLD) {
                                    lastMotionTime = current.timestampMs
                                }
                                
                                val timeSinceMotion = current.timestampMs - lastMotionTime
                                val timeSinceStart = current.timestampMs - motionStartTime

                                if (timeSinceMotion > END_DELAY_MS || timeSinceStart > MAX_WINDOW_MS) {
                                    evaluateBuffer(motionStartTime, current.timestampMs)
                                }
                            }
                            State.COOLDOWN -> {}
                        }
                    }
                }
    }

    private fun evaluateBuffer(startMs: Long, endMs: Long) {
        val actualEndMs = min(endMs, lastMotionTime + 150L)
        state = State.COOLDOWN
        cooldownUntil = actualEndMs + COOLDOWN_MS

        val window = buffer.filter { it.timestampMs in (startMs - 200L)..actualEndMs }
        
        if (window.size >= 10) { // Need enough samples
            _motionSegment.tryEmit(window)
            
            if (activeGestures.isNotEmpty()) {
                val match = DtwEngine.findBestMatch(window, activeGestures)
                if (match != null) {
                    _detectedGesture.tryEmit(match)
                }
            }
        }
    }
    
    fun triggerCooldown(durationMs: Long) {
        scope.launch {
            mutex.withLock {
                if (buffer.isNotEmpty()) {
                    cooldownUntil = buffer.last().timestampMs + durationMs
                    state = State.COOLDOWN
                }
            }
        }
    }
}
