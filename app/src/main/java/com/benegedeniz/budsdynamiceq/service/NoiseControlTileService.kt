package com.benegedeniz.budsdynamiceq.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.benegedeniz.budsdynamiceq.R
import com.benegedeniz.budsdynamiceq.bluetooth.BudsController
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.data.repository.SettingsRepository
import com.benegedeniz.budsdynamiceq.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class NoiseControlTileService : TileService() {

    private lateinit var budsController: BudsController
    private lateinit var settingsRepository: SettingsRepository
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        budsController = ServiceLocator.provideBudsController(this)
        settingsRepository = ServiceLocator.provideSettingsRepository(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        observeJob?.cancel()
        observeJob = scope.launch {
            combine(
                budsController.isConnected,
                budsController.activeNoiseControl
            ) { connected, activeMode ->
                Pair(connected, activeMode)
            }.collect { (connected, activeMode) ->
                updateTileState(connected, activeMode)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        observeJob?.cancel()
    }

    private fun updateTileState(connected: Boolean, activeMode: NoiseControlMode?) {
        val tile = qsTile ?: return

        if (!connected) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = getString(R.string.qs_tile_label)
            tile.updateTile()
            return
        }

        // Active state if ANC or Transparency is on
        tile.state = if (activeMode == NoiseControlMode.NOISE_CANCELLATION || activeMode == NoiseControlMode.TRANSPARENT) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }

        tile.label = when (activeMode) {
            NoiseControlMode.NOISE_CANCELLATION -> getString(R.string.nc_anc)
            NoiseControlMode.TRANSPARENT -> getString(R.string.nc_transparent)
            NoiseControlMode.OFF -> getString(R.string.nc_off)
            NoiseControlMode.ADAPTIVE -> getString(R.string.nc_adaptive)
            else -> getString(R.string.qs_tile_label)
        }

        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val connected = budsController.isConnected.value
        if (!connected) return

        val currentMode = budsController.activeNoiseControl.value
        val behavior = settingsRepository.getTileBehavior()
        val supportsTransparencyNC = budsController.effectiveModel.value.supportsTransparencyNC

        val nextMode = when (behavior) {
            0 -> { // ANC <-> Transparent
                if (currentMode == NoiseControlMode.NOISE_CANCELLATION) {
                    if (supportsTransparencyNC) NoiseControlMode.TRANSPARENT else NoiseControlMode.OFF
                } else {
                    NoiseControlMode.NOISE_CANCELLATION
                }
            }
            1 -> { // ANC <-> Off
                if (currentMode == NoiseControlMode.NOISE_CANCELLATION) NoiseControlMode.OFF else NoiseControlMode.NOISE_CANCELLATION
            }
            2 -> { // Transparent <-> Off
                if (currentMode == NoiseControlMode.TRANSPARENT) NoiseControlMode.OFF else {
                    if (supportsTransparencyNC) NoiseControlMode.TRANSPARENT else NoiseControlMode.NOISE_CANCELLATION
                }
            }
            3 -> { // Cycle All
                when (currentMode) {
                    NoiseControlMode.NOISE_CANCELLATION -> if (supportsTransparencyNC) NoiseControlMode.TRANSPARENT else NoiseControlMode.OFF
                    NoiseControlMode.TRANSPARENT -> NoiseControlMode.OFF
                    NoiseControlMode.OFF -> NoiseControlMode.NOISE_CANCELLATION
                    else -> NoiseControlMode.NOISE_CANCELLATION
                }
            }
            else -> NoiseControlMode.NOISE_CANCELLATION
        }

        budsController.sendNoiseControl(nextMode)
    }
}
