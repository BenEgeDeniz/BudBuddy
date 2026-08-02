package com.benegedeniz.budsdynamiceq.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class SppPacketEncoderTest {

    @Test
    fun buildPacket_correctFraming() {
        val msgId = SppPacketEncoder.MSG_ID_EQUALIZER
        val payload = byteArrayOf(0x03) // Dynamic preset

        val packet = SppPacketEncoder.buildPacket(msgId, payload)

        // Packet structure: SOM (1) + Header (2) + MsgId (1) + Payload (1) + CRC (2) + EOM (1) = 8 bytes
        assertEquals(8, packet.size)
        
        // SOM
        assertEquals(0xFD.toByte(), packet[0])
        
        // EOM
        assertEquals(0xDD.toByte(), packet[packet.size - 1])
        
        // MsgId is at index 3 (SOM + 2 bytes header)
        assertEquals(0x86.toByte(), packet[3])
        
        // Payload is at index 4
        assertEquals(0x03.toByte(), packet[4])

    }
}
