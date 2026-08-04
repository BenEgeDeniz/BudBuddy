package com.benegedeniz.budsdynamiceq.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.sin

class HearingTestManager(context: Context) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalVolume: Int = -1
    
    private var focusRequest: AudioFocusRequest? = null
    
    var currentGain = 1.0f
        private set
        
    suspend fun prepareAndMaximizeVolume() {
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
        focusRequest?.let { audioManager.requestAudioFocus(it) }

        delay(1500)

        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
    }
    
    suspend fun restoreVolume() {
        if (originalVolume != -1) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            originalVolume = -1
        }
        
        delay(1500)
        
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        stop()
    }
    
    fun restoreVolumeImmediately() {
        if (originalVolume != -1) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            originalVolume = -1
        }
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        stop()
    }

    fun startTone(isLeftEar: Boolean, onGainChanged: (Float) -> Unit) {
        stop()
        
        val sampleRate = 44100
        val frequency = 1000.0
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        audioTrack?.play()
        isPlaying = true
        currentGain = 1.0f
        
        playJob = scope.launch {
            val buffer = ShortArray(bufferSize)
            var sampleIndex = 0
            val durationMs = 15000L
            val startTime = System.currentTimeMillis()
            
            while (isPlaying && isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > durationMs) {
                    currentGain = 0f
                } else {
                    // currentGain acts as a normalized score from 1.0 down to 0.0
                    currentGain = 1.0f - (elapsed.toFloat() / durationMs.toFloat())
                }
                
                withContext(Dispatchers.Main) {
                    onGainChanged(currentGain)
                }
                
                // Audio math: base volume at 10% so it's not ear-piercing, and cubic decay
                // so it gets extremely quiet at the very end.
                val baseGain = 0.1f
                val pcmMultiplier = baseGain * (currentGain * currentGain * currentGain)
                
                for (i in buffer.indices step 2) {
                    val sample = (sin(2.0 * Math.PI * sampleIndex / (sampleRate / frequency)) * 32767 * pcmMultiplier).toInt().toShort()
                    buffer[i] = if (isLeftEar) sample else 0
                    buffer[i + 1] = if (!isLeftEar) sample else 0
                    sampleIndex++
                }
                audioTrack?.write(buffer, 0, buffer.size)
                
                if (currentGain <= 0f) {
                    isPlaying = false
                }
            }
        }
    }
    
    fun stop() {
        isPlaying = false
        playJob?.cancel()
        playJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
    }
}
