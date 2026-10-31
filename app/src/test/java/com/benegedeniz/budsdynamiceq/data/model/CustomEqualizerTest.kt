package com.benegedeniz.budsdynamiceq.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomEqualizerTest {

    @Test
    fun clamp_outOfRangeAndWrongSize() {
        assertEquals(CustomEqualizer.FLAT, CustomEqualizer.clamp(listOf(1, 2)))
        assertEquals(
            listOf(-10, 10, 0, 0, 0, 0, 0, 0, 0),
            CustomEqualizer.clamp(listOf(-99, 99, 0, 0, 0, 0, 0, 0, 0))
        )
    }

    @Test
    fun serializeAndParse_roundTrip() {
        val bands = listOf(6, 3, 0, 0, 1, 1, 3, 5, 5)
        val stored = CustomEqualizer.serialize(bands)
        assertEquals("6,3,0,0,1,1,3,5,5", stored)
        assertEquals(bands, CustomEqualizer.parseStored(stored))
    }

    @Test
    fun parseStored_invalidFallsBackToFlat() {
        assertEquals(CustomEqualizer.FLAT, CustomEqualizer.parseStored(null))
        assertEquals(CustomEqualizer.FLAT, CustomEqualizer.parseStored("1,2,nope"))
    }

    @Test
    fun formatGain_signed() {
        assertEquals("+6", CustomEqualizer.formatGain(6))
        assertEquals("0", CustomEqualizer.formatGain(0))
        assertEquals("-3", CustomEqualizer.formatGain(-3))
    }

    @Test
    fun customPreset_usesWearablePayload() {
        assertEquals(0x07.toByte(), EqPreset.CUSTOM_1.payloadByte)
    }
}
