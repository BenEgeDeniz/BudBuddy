package com.benegedeniz.budsdynamiceq.ui.state

import com.benegedeniz.budsdynamiceq.data.model.EqRule
import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.NoiseControlMode
import com.benegedeniz.budsdynamiceq.bluetooth.BudsModel
import com.benegedeniz.budsdynamiceq.media.SongMetadata

data class RulesUiState(
    val rules: List<EqRule> = emptyList(),
    val isConnected: Boolean = false,
    val effectiveModel: BudsModel = BudsModel.UNKNOWN,
    val currentMetadata: SongMetadata? = null,
    val recentHistory: List<SongMetadata> = emptyList(),
    val manualPreset: EqPreset? = null,
    val manualNoiseControl: NoiseControlMode? = null,
    val lastMatchedRule: EqRule? = null
)
