package com.benegedeniz.budsdynamiceq.di

import android.content.Context
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.data.GestureRepository
import com.benegedeniz.budsdynamiceq.data.RulesRepository
import com.benegedeniz.budsdynamiceq.gesture.GestureDetector
import com.benegedeniz.budsdynamiceq.gesture.NoiseDetector
import com.benegedeniz.budsdynamiceq.media.MediaObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.benegedeniz.budsdynamiceq.data.WearStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceLocator {
    
    @Volatile
    private var budsController: BudsController? = null
    
    @Volatile
    private var rulesRepository: RulesRepository? = null

    @Volatile
    private var mediaObserver: MediaObserver? = null

    @Volatile
    private var gestureRepository: GestureRepository? = null

    @Volatile
    private var wearStateRepository: WearStateRepository? = null

    @Volatile
    private var gestureDetector: GestureDetector? = null

    @Volatile
    private var noiseDetector: NoiseDetector? = null

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Shared settings flows — written by ViewModel, read by BudsService.
    // Using a shared singleton eliminates the fragile SharedPreferences listener bridge.
    private val _headShakeEnabled = MutableStateFlow(false)
    val headShakeEnabled: StateFlow<Boolean> = _headShakeEnabled.asStateFlow()

    private val _requireBothEarbuds = MutableStateFlow(false)
    val requireBothEarbuds: StateFlow<Boolean> = _requireBothEarbuds.asStateFlow()

    fun initFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE)
        _headShakeEnabled.value = prefs.getBoolean("head_shake_enabled", false)
        _requireBothEarbuds.value = prefs.getBoolean("require_both_earbuds", false)
        _pauseMediaOnConversation.value = prefs.getBoolean("pause_media_on_conversation", false)
    }

    fun setHeadShakeEnabled(enabled: Boolean) {
        _headShakeEnabled.value = enabled
    }

    fun setRequireBothEarbuds(enabled: Boolean) {
        _requireBothEarbuds.value = enabled
    }

    private val _pauseMediaOnConversation = MutableStateFlow(false)
    val pauseMediaOnConversation: StateFlow<Boolean> = _pauseMediaOnConversation.asStateFlow()

    fun setPauseMediaOnConversation(enabled: Boolean) {
        _pauseMediaOnConversation.value = enabled
    }

    @Volatile
    private var settingsRepository: com.benegedeniz.budsdynamiceq.data.repository.SettingsRepository? = null

    @Volatile
    private var deviceStateRepository: com.benegedeniz.budsdynamiceq.data.repository.DeviceStateRepository? = null

    fun provideSettingsRepository(context: Context): com.benegedeniz.budsdynamiceq.data.repository.SettingsRepository {
        return settingsRepository ?: synchronized(this) {
            settingsRepository ?: com.benegedeniz.budsdynamiceq.data.repository.SettingsRepository(context.applicationContext).also { settingsRepository = it }
        }
    }

    fun provideDeviceStateRepository(context: Context): com.benegedeniz.budsdynamiceq.data.repository.DeviceStateRepository {
        return deviceStateRepository ?: synchronized(this) {
            deviceStateRepository ?: com.benegedeniz.budsdynamiceq.data.repository.DeviceStateRepository().also { deviceStateRepository = it }
        }
    }

    fun provideBudsController(context: Context): BudsController {
        return budsController ?: synchronized(this) {
            budsController ?: BudsController(
                context.applicationContext,
                provideDeviceStateRepository(context),
                provideSettingsRepository(context)
            ).also { budsController = it }
        }
    }

    fun provideRulesRepository(context: Context): RulesRepository {
        return rulesRepository ?: synchronized(this) {
            rulesRepository ?: RulesRepository(context.applicationContext).also { rulesRepository = it }
        }
    }

    fun provideMediaObserver(context: Context): MediaObserver {
        return mediaObserver ?: synchronized(this) {
            mediaObserver ?: MediaObserver(context.applicationContext).also { mediaObserver = it }
        }
    }

    fun provideGestureRepository(context: Context): GestureRepository {
        return gestureRepository ?: synchronized(this) {
            gestureRepository ?: GestureRepository(context.applicationContext).also { gestureRepository = it }
        }
    }

    fun provideWearStateRepository(context: Context): WearStateRepository {
        return wearStateRepository ?: synchronized(this) {
            wearStateRepository ?: WearStateRepository(context.applicationContext).also { wearStateRepository = it }
        }
    }

    fun provideGestureDetector(context: Context): GestureDetector {
        return gestureDetector ?: synchronized(this) {
            gestureDetector ?: GestureDetector(applicationScope).also { gestureDetector = it }
        }
    }

    fun provideNoiseDetector(context: Context): NoiseDetector {
        return noiseDetector ?: synchronized(this) {
            val detector = provideGestureDetector(context)
            noiseDetector ?: NoiseDetector(applicationScope, detector).also { noiseDetector = it }
        }
    }
}
