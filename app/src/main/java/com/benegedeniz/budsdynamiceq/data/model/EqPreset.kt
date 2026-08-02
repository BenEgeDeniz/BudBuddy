package com.benegedeniz.budsdynamiceq.data.model

enum class EqPreset(val displayName: String, val payloadByte: Byte) {
    DEFAULT("Default", -2),
    IGNORE("Don't Change", -1),
    NORMAL("Balanced", 0x00),
    BASS_BOOST("Bass boost", 0x01),
    SOFT("Smooth", 0x02),
    DYNAMIC("Dynamic", 0x03),
    CLEAR("Clear", 0x04),
    TREBLE_BOOST("Treble boost", 0x05);
}
