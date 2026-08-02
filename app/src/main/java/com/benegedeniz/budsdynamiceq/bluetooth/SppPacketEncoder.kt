package com.benegedeniz.budsdynamiceq.bluetooth

object SppPacketEncoder {
    
    private const val SOM: Byte = 0xFD.toByte()
    private const val EOM: Byte = 0xDD.toByte()
    const val MSG_ID_EQUALIZER: Byte = 0x86.toByte()
    const val MSG_ID_NOISE_CONTROLS: Byte = 0x78.toByte()
    const val MSG_ID_SET_CALL_PATH_CONTROL: Byte = 0x6E.toByte() // 110 (0x6E)
    const val MSG_ID_SET_ANC_WITH_ONE_EARBUD: Byte = 0x6F.toByte() // 111 (0x6F)
    const val MSG_ID_SET_SIDETONE: Byte = 0x8B.toByte() // 139 (0x8B)
    const val MSG_ID_SET_SPATIAL_AUDIO: Byte = 0x7C.toByte()
    const val MSG_ID_SPATIAL_AUDIO_CONTROL: Byte = 0xC3.toByte()
    const val MSG_ID_SPATIAL_AUDIO_DATA: Byte = 0xC2.toByte()

    /**
     * Builds a Samsung SPP packet.
     * Packet structure for modern Buds (e.g., Buds 2, Buds Pro, Buds FE):
     * 1 byte: SOM (0xFD)
     * 2 bytes: header (Little-endian Size. Size = MsgId (1) + Payload Length + CRC (2)). Bit 12 = Type (Request=0), Bit 13 = Fragment (No=0).
     * 1 byte: message ID
     * N bytes: payload
     * 2 bytes: CRC16 over message ID + payload
     * 1 byte: EOM (0xDD)
     */
    fun buildPacket(msgId: Byte, payload: ByteArray): ByteArray {
        val size = 1 + payload.size + 2 // MsgId (1) + Payload + CRC (2)
        
        // Header bytes (2 bytes)
        val headerBytes = byteArrayOf(
            (size and 0xFF).toByte(),
            ((size shr 8) and 0xFF).toByte() // Type=Request (0), IsFragment=No (0)
        )

        // Calculate CRC16 over MsgId + Payload only
        val crcData = ByteArray(1 + payload.size)
        crcData[0] = msgId
        System.arraycopy(payload, 0, crcData, 1, payload.size)
        val crc = calculateCrc16(crcData)

        val packet = ByteArray(1 + 2 + 1 + payload.size + 2 + 1)
        var offset = 0
        packet[offset++] = SOM
        packet[offset++] = headerBytes[0]
        packet[offset++] = headerBytes[1]
        packet[offset++] = msgId
        System.arraycopy(payload, 0, packet, offset, payload.size)
        offset += payload.size
        
        // CRC in little endian
        packet[offset++] = (crc and 0xFF).toByte()
        packet[offset++] = ((crc shr 8) and 0xFF).toByte()
        
        packet[offset] = EOM
        
        return packet
    }

    /**
     * Calculates CRC16 matching the GalaxyBudsClient implementation.
     */
    private fun calculateCrc16(data: ByteArray): Int {
        var crc = 0x0000
        for (b in data) {
            crc = (((crc shr 8) and 0xFF) or ((crc shl 8) and 0xFF00)) and 0xFFFF
            crc = crc xor (b.toInt() and 0xFF)
            crc = crc xor ((crc and 0xFF) shr 4)
            crc = crc xor ((crc shl 12) and 0xFFFF)
            crc = crc xor (((crc and 0xFF) shl 5) and 0xFFFF)
        }
        return crc and 0xFFFF
    }
}
