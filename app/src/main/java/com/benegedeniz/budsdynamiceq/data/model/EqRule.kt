package com.benegedeniz.budsdynamiceq.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class EqRule(
    val id: String = UUID.randomUUID().toString(),
    val keyword: String,
    val preset: EqPreset,
    val noiseControl: NoiseControlMode = NoiseControlMode.DEFAULT,
    val enabled: Boolean = true,
    val priority: Int
)
