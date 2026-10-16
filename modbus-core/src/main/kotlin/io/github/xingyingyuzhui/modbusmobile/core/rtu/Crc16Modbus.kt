package io.github.xingyingyuzhui.modbusmobile.core.rtu

object Crc16Modbus {
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int =
        fold(length) { index -> data[offset + index].toInt() and 0xFF }

    /** 在接收窗口上核对 CRC，避免为每个候选帧复制一份数组。 */
    fun matches(buf: RxWindow, offset: Int, length: Int): Boolean {
        if (length < 4 || offset < 0 || offset + length > buf.size) return false
        val expected = fold(length - 2) { index -> buf[offset + index] }
        val actual = buf[offset + length - 2] or (buf[offset + length - 1] shl 8)
        return expected == actual
    }

    private inline fun fold(length: Int, byteAt: (Int) -> Int): Int {
        var crc = 0xFFFF
        for (index in 0 until length) {
            crc = crc xor byteAt(index)
            repeat(8) {
                crc = if ((crc and 0x0001) != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
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
