package com.benegedeniz.budsdynamiceq.data.model

import com.benegedeniz.budsdynamiceq.gesture.NoiseFingerprint
import com.benegedeniz.budsdynamiceq.gesture.QuaternionSample
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class HeadGesture(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val actions: List<FlowAction> = emptyList(),
    val templates: List<List<QuaternionSample>>,
    val enabled: Boolean = true,
    val playChime: Boolean = true,
    val isNoiseProfile: Boolean = false,
    val blockGesturesOnMatch: Boolean = true,
    val noiseFingerprint: NoiseFingerprint? = null,
    val createdAt: Long = System.currentTimeMillis()
)

