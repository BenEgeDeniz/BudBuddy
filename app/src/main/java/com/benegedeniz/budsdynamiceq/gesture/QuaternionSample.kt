package com.benegedeniz.budsdynamiceq.gesture

import kotlinx.serialization.Serializable

@Serializable
data class QuaternionSample(
    val timestampMs: Long,  // monotonic ms since recording start
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float
)
