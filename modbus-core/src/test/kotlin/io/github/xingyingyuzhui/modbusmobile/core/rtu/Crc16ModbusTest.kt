package io.github.xingyingyuzhui.modbusmobile.core.rtu

import io.github.xingyingyuzhui.modbusmobile.core.model.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun windowMatchUsesTheSameCrc() {
        val frame = Crc16Modbus.append(byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x02))
        val window = RxBuffer(frame.size)
        window.append(frame)
        assertTrue(Crc16Modbus.matches(window, 0, frame.size))
        assertTrue(Crc16Modbus.matches(frame))
        assertFalse(Crc16Modbus.matches(window, 1, frame.size - 1))
    }
}
