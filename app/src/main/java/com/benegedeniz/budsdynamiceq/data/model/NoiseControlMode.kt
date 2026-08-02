package com.benegedeniz.budsdynamiceq.data.model

enum class NoiseControlMode(val displayName: String, val payloadByte: Byte) {
    DEFAULT("Default", -2),
    IGNORE("Don't Change", -1),
    OFF("Off", 0x00),
    NOISE_CANCELLATION("ANC", 0x01),
    TRANSPARENT("Transparent", 0x02),
    ADAPTIVE("Adaptive", 0x03)
}
