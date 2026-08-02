package com.benegedeniz.budsdynamiceq.gesture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.data.model.FlowAction
import com.benegedeniz.budsdynamiceq.data.model.GestureAction
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import kotlinx.coroutines.delay

class GestureActionExecutor(
    private val context: Context,
    private val budsController: BudsController
) {
    private val ttsManager = TtsManager(context)

    suspend fun execute(actions: List<FlowAction>, playChime: Boolean = true) {
        if (playChime) {
            try {
                android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 50).startTone(android.media.ToneGenerator.TONE_PROP_BEEP)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        var lastWasSystemAction = false
        
        for (flowAction in actions) {
            when (flowAction) {
                is FlowAction.DelayAction -> {
                    delay(maxOf(100L, flowAction.ms))
                    lastWasSystemAction = false
                }
                is FlowAction.SystemAction -> {
                    if (lastWasSystemAction) {
                        delay(100L)
                    }
                    
                    when (flowAction.action) {
                        GestureAction.PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                        GestureAction.PLAY -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                        GestureAction.PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                        GestureAction.NEXT_TRACK -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                        GestureAction.PREVIOUS_TRACK -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                        GestureAction.ANNOUNCE_TRACK -> announceTrack()
                        GestureAction.NC_TOGGLE -> budsController.toggleNoiseControl()
                        GestureAction.NC_ACTIVE -> budsController.sendNoiseControl(NoiseControlMode.NOISE_CANCELLATION)
                        GestureAction.NC_OFF -> budsController.sendNoiseControl(NoiseControlMode.OFF)
                        GestureAction.NC_TRANSPARENT -> budsController.sendNoiseControl(NoiseControlMode.TRANSPARENT)
                        GestureAction.NC_ADAPTIVE -> budsController.sendNoiseControl(NoiseControlMode.ADAPTIVE)
                        GestureAction.VOICE_ASSISTANT -> triggerVoiceAssistant()
                        GestureAction.ACCEPT_CALL -> acceptCall()
                        GestureAction.REJECT_CALL -> rejectCall()
                        GestureAction.READ_NOTIFICATIONS -> readNotifications()
                        GestureAction.FIT_TEST -> performFitTest()
                        GestureAction.LAUNCH_APP -> {} // Handled by AppAction usually, but in case it's a SystemAction, do nothing
                        GestureAction.SET_VOLUME -> {} // Handled by VolumeAction usually
                        GestureAction.MODIFY_VOLUME_INCREASE -> {} // Handled by ModifyVolumeAction
                        GestureAction.MODIFY_VOLUME_DECREASE -> {} // Handled by ModifyVolumeAction
                        GestureAction.SPEAK_TEXT -> {} // Handled by TtsAction
                        GestureAction.NO_ACTION -> {} // Literally do nothing
                    }
                    
                    lastWasSystemAction = true
                }
                is FlowAction.AppAction -> {
                    lastWasSystemAction = false
                    if (flowAction.packageName.isNotEmpty()) {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage(flowAction.packageName)
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                is FlowAction.VolumeAction -> {
                    lastWasSystemAction = false
                    try {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val targetVol = (flowAction.percentage * maxVol) / 100
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                is FlowAction.ModifyVolumeAction -> {
                    lastWasSystemAction = false
                    try {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val delta = ((flowAction.percentage / 100f) * maxVol).toInt().coerceAtLeast(1)
                        
                        val targetVol = if (flowAction.increase) {
                            (currentVol + delta).coerceAtMost(maxVol)
                        } else {
                            (currentVol - delta).coerceAtLeast(0)
                        }
                        
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                is FlowAction.TtsAction -> {
                    lastWasSystemAction = false
                    if (flowAction.text.isNotBlank()) {
                        ttsManager.speakAndWait(flowAction.text, resumeMedia = true, asAnnouncement = flowAction.asAnnouncement)
                    }
                }
            }
        }
    }

    private fun triggerVoiceAssistant() {
        try {
            val intent = Intent(Intent.ACTION_VOICE_COMMAND)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acceptCall() {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
            try {
                telecomManager.acceptRingingCall()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun rejectCall() {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    telecomManager.endCall()
                } else {
                    @Suppress("DEPRECATION")
                    telecomManager.endCall()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun readNotifications() {
        val service = com.benegedeniz.budsdynamiceq.media.MediaListenerService.instance
        if (service == null) {
            ttsManager.speak("Notification access is not enabled.")
            return
        }

        try {
            val activeNotifs = service.activeNotifications
                ?.filter { !it.isOngoing && (it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0 }
                ?.filter { !it.notification.extras.getString(android.app.Notification.EXTRA_TEXT).isNullOrBlank() }
                ?.take(3)

            if (activeNotifs.isNullOrEmpty()) {
                ttsManager.speak("No new notifications.")
                return
            }

            val pm = context.packageManager
            val sb = java.lang.StringBuilder()
            sb.append("You have ${activeNotifs.size} notification${if (activeNotifs.size > 1) "s" else ""}. ")
            for (sbn in activeNotifs) {
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                } catch (e: Exception) {
                    sbn.packageName.split(".").last().replaceFirstChar { it.uppercase() }
                }
                
                val title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE) ?: appName
                val text = sbn.notification.extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
                sb.append("From $title: $text. ")
            }
            ttsManager.speak(sb.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            ttsManager.speak("Could not read notifications.")
        }
    }

    fun triggerPlay() {
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    fun triggerPause() {
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    private fun sendMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
    
    private suspend fun performFitTest() {
        val placementL = budsController.placementL.value
        val placementR = budsController.placementR.value
        if (placementL != com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING || 
            placementR != com.benegedeniz.budsdynamiceq.data.model.PlacementState.WEARING) {
            ttsManager.speak("Please wear both earbuds to test the fit.")
            return
        }

        budsController.startFitTest()
        
        delay(3500) // Wait for the sound to finish and final results to be ready, ignoring early echo messages
        
        var timeout = 25 // 2.5 seconds maximum (100ms * 25)
        while (timeout > 0) {
            val resL = budsController.fitTestResultL.value
            val resR = budsController.fitTestResultR.value
            if (resL != com.benegedeniz.budsdynamiceq.data.model.FitTestResult.UNKNOWN && 
                resR != com.benegedeniz.budsdynamiceq.data.model.FitTestResult.UNKNOWN) {
                break
            }
            delay(100)
            timeout--
        }
        
        val finalL = budsController.fitTestResultL.value
        val finalR = budsController.fitTestResultR.value
        
        val goodL = finalL == com.benegedeniz.budsdynamiceq.data.model.FitTestResult.GOOD
        val goodR = finalR == com.benegedeniz.budsdynamiceq.data.model.FitTestResult.GOOD
        
        if (goodL && goodR) {
            ttsManager.speakAndWait("You have got a good fit.")
        } else if (!goodL && !goodR) {
            ttsManager.speakAndWait("Try adjusting both earbuds for a better fit.")
        } else if (!goodL) {
            ttsManager.speakAndWait("Try adjusting your left earbud.")
        } else {
            ttsManager.speakAndWait("Try adjusting your right earbud.")
        }
        
        budsController.stopFitTest()
    }
    
    private suspend fun announceTrack() {
        val metadata = com.benegedeniz.budsdynamiceq.di.ServiceLocator.provideMediaObserver(context).currentMetadata.value
        if (metadata != null && (!metadata.title.isNullOrBlank() || !metadata.artist.isNullOrBlank())) {
            val title = metadata.title ?: "Unknown Song"
            val artist = metadata.artist ?: "Unknown Artist"
            ttsManager.speakAndWait("Playing $title by $artist.", resumeMedia = true, asAnnouncement = false)
        } else {
            ttsManager.speakAndWait("Nothing is currently playing.", resumeMedia = true, asAnnouncement = false)
        }
    }
}
