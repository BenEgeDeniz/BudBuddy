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
    private var gestureDetector: GestureDetector? = null

    @Volatile
    private var noiseDetector: NoiseDetector? = null

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun provideBudsController(context: Context): BudsController {
        return budsController ?: synchronized(this) {
            budsController ?: BudsController(context.applicationContext).also { budsController = it }
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
