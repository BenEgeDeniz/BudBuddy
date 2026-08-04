package com.benegedeniz.budsdynamiceq.gesture

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedVolume: Int = -1
    private var focusRequest: AudioFocusRequest? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var speakJob: Job? = null
    private var currentUtteranceId: String? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    private fun updateLanguage() {
        val prefs = context.getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("AppLanguage", "system") ?: "system"
        val locale = if (lang == "system") {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.content.res.Resources.getSystem().configuration.locales.get(0)
            } else {
                @Suppress("DEPRECATION")
                android.content.res.Resources.getSystem().configuration.locale
            }
        } else {
            java.util.Locale(lang)
        }
        tts?.language = locale
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            updateLanguage()

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    handleUtteranceEnd(utteranceId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handleUtteranceEnd(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    handleUtteranceEnd(utteranceId)
                }
            })

            isReady = true
        }
    }

    private fun handleUtteranceEnd(utteranceId: String?) {
        scope.launch {
            if (utteranceId == currentUtteranceId) {
                restoreAudio()
                currentUtteranceId = null
            }
        }
    }

    private fun prepareAudioFocus(resumeMedia: Boolean) {
        // Request audio focus to manage background music
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusHint = if (resumeMedia) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN
        focusRequest = AudioFocusRequest.Builder(focusHint)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener {}
            .build()
            
        audioManager.requestAudioFocus(focusRequest!!)
    }

    private fun boostVolume() {
        if (savedVolume == -1) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            // Set to ~80% of max to avoid being too aggressive
            val targetVolume = (maxVolume * 0.8).toInt().coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        }
    }

    private fun restoreAudio() {
        // 1. Restore original media volume
        if (savedVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
            savedVolume = -1
        }

        // 2. Abandon audio focus — any paused music will resume
        focusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            focusRequest = null
        }
    }

    fun speak(text: String, resumeMedia: Boolean = true, asAnnouncement: Boolean = true) {
        if (!isReady) return
        updateLanguage()

        speakJob?.cancel()
        speakJob = scope.launch {
            val utteranceId = "TTS_ID_${System.currentTimeMillis()}"
            currentUtteranceId = utteranceId

            // Manage audio focus first (pauses media)
            prepareAudioFocus(resumeMedia)
            
            // Wait 300ms for media to fully pause before raising volume
            delay(300)
            if (asAnnouncement) {
                boostVolume()
            }
            
            // Play TTS on the media stream
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            
            if (result != TextToSpeech.SUCCESS) {
                if (currentUtteranceId == utteranceId) {
                    restoreAudio()
                    currentUtteranceId = null
                }
            }
        }
    }
    
    suspend fun speakAndWait(text: String, resumeMedia: Boolean = true, asAnnouncement: Boolean = true) {
        if (!isReady) return
        updateLanguage()
        
        suspendCancellableCoroutine { continuation ->
            speakJob?.cancel()
            speakJob = scope.launch {
                val utteranceId = "TTS_ID_${System.currentTimeMillis()}"
                currentUtteranceId = utteranceId

                // Manage audio focus first (pauses media)
                prepareAudioFocus(resumeMedia)
                
                // Wait 300ms for media to fully pause before raising volume
                delay(300)
                if (asAnnouncement) {
                    boostVolume()
                }
                
                // Play TTS on the media stream
                val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                
                if (result != TextToSpeech.SUCCESS) {
                    if (currentUtteranceId == utteranceId) {
                        restoreAudio()
                        currentUtteranceId = null
                    }
                    continuation.resume(Unit)
                } else {
                    // Start a polling loop to check when currentUtteranceId becomes null (reset by handleUtteranceEnd)
                    launch {
                        while (currentUtteranceId == utteranceId) {
                            delay(50)
                        }
                        continuation.resume(Unit)
                    }
                }
            }
            
            continuation.invokeOnCancellation {
                speakJob?.cancel()
            }
        }
    }

    fun playSilence(durationMs: Long) {
        if (isReady) {
            tts?.playSilentUtterance(durationMs, TextToSpeech.QUEUE_FLUSH, "TTS_SILENCE")
        }
    }

    fun shutdown() {
        restoreAudio()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
