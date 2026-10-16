package io.github.xingyingyuzhui.modbusmobile.core.rtu

object Crc16Modbus {
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        val end = offset + length
        for (index in offset until end) {
            crc = crc xor (data[index].toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 0x0001) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    fun append(payload: ByteArray): ByteArray {
        val crc = compute(payload)
        return payload + (crc and 0xFF).toByte() + (crc ushr 8).toByte()
    }

    fun matches(frame: ByteArray): Boolean {
        if (frame.size < 4) return false
        val expected = compute(frame, 0, frame.size - 2)
        val actual = (frame[frame.size - 2].toInt() and 0xFF) or ((frame[frame.size - 1].toInt() and 0xFF) shl 8)
        return expected == actual
    }
}
