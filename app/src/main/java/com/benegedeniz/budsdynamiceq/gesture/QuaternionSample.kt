package com.benegedeniz.budsdynamiceq.gesture

import kotlinx.serialization.Serializable

@Serializable
data class QuaternionSample(
    val timestampMs: Long,  // monotonic ms since recording start
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
    val rawX: Float = 0f,
    val rawY: Float = 0f,
    val rawZ: Float = 0f,
    val rawW: Float = 0f
)
