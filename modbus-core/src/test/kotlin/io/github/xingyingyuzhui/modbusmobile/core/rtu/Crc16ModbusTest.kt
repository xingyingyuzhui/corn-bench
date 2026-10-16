package io.github.xingyingyuzhui.modbusmobile.core.rtu

import io.github.xingyingyuzhui.modbusmobile.core.model.toHex
import kotlin.test.Test
import kotlin.test.assertEquals

class Crc16ModbusTest {
    @Test
    fun modbusFrameAppendsLowByteFirst() {
        val payload = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x02)
        assertEquals(0x0BC4, Crc16Modbus.compute(payload))
        assertEquals("01 03 00 00 00 02 C4 0B", Crc16Modbus.append(payload).toHex())
    }

    @Test
    fun asciiCheckValue() {
        assertEquals(0x4B37, Crc16Modbus.compute("123456789".encodeToByteArray()))
    }
}
