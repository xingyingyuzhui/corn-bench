package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import io.github.xingyingyuzhui.modbusmobile.application.NumberFormat
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.rtu.Crc16Modbus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbenchLogicTest {
    @Test
    fun formatsU16S16AndHex() {
        val values = listOf(0, 123, 0x8000, 0xFFFF)
        assertEquals(listOf("0", "123", "32768", "65535"), formatRegisterValues(values, NumberFormat.U16, false))
        assertEquals(listOf("0", "123", "-32768", "-1"), formatRegisterValues(values, NumberFormat.S16, false))
        assertEquals(listOf("0000", "007B", "8000", "FFFF"), formatRegisterValues(values, NumberFormat.HEX, false))
        assertEquals("0x007B", hexWord(123, true))
    }

    @Test
    fun f32CombinesRegisterPairsHighWordFirst() {
        // 0x41C8 0x0000 = 25.0；0x3F80 0x0000 = 1.0；最后一个落单。
        val values = listOf(0x41C8, 0x0000, 0x3F80, 0x0000, 7)
        assertEquals(listOf("25", "—", "1", "—", "—"), formatRegisterValues(values, NumberFormat.F32, false))
        assertEquals("0.1", formatFloat(0.1f))
        assertEquals("100", formatFloat(100f))
    }

    @Test
    fun parsesDecimalAndHexValues() {
        assertEquals(123, parseWord(" 123 ", WriteFormat.DEC))
        assertEquals(0xFFFF, parseWord("-1", WriteFormat.DEC))
        assertNull(parseWord("65536", WriteFormat.DEC))
        assertNull(parseWord("abc", WriteFormat.DEC))
        assertEquals(0x7B, parseWord("7B", WriteFormat.HEX))
        assertEquals(0x7B, parseWord("0x7b", WriteFormat.HEX))
        assertNull(parseWord("10000", WriteFormat.HEX))
    }

    @Test
    fun oneRegisterUsesFc06AndSeveralUseFc16() {
        val single = ok(planWrite(RegisterArea.HOLDING_REGISTER, "1", "5", listOf("123"), emptyList(), WriteFormat.DEC, false))
        assertTrue(single.request is ModbusRequest.WriteSingleRegister)
        assertEquals(0x06, single.functionCode)
        assertTrue(single.isSingle)
        assertArrayEquals(Crc16Modbus.append(byteArrayOf(1, 6, 0, 5, 0, 0x7B)), single.frame)
        assertEquals(4..5, single.payload)

        val multi = ok(planWrite(RegisterArea.HOLDING_REGISTER, "1", "100", listOf("100", "300", "500"), emptyList(), WriteFormat.DEC, false))
        assertEquals(0x10, multi.functionCode)
        // 稿面 b1-3：01 10 00 64 00 03 06 | 00 64 01 2C 01 F4 | CRC
        assertArrayEquals(
            byteArrayOf(1, 0x10, 0, 0x64, 0, 3, 6, 0, 0x64, 1, 0x2C, 1, 0xF4.toByte()),
            multi.frame.copyOfRange(0, 13),
        )
        assertEquals(7..12, multi.payload)
        assertEquals(15, multi.frame.size)
    }

    @Test
    fun forcingMultipleTurnsOneValueIntoFc16() {
        val forced = ok(planWrite(RegisterArea.HOLDING_REGISTER, "1", "5", listOf("123"), emptyList(), WriteFormat.DEC, true))
        assertEquals(0x10, forced.functionCode)
        assertEquals(false, forced.isSingle)
        assertEquals(7..8, forced.payload)
    }

    @Test
    fun coilsUseFc05AndFc15() {
        val single = ok(planWrite(RegisterArea.COIL, "1", "2", emptyList(), listOf(true), WriteFormat.DEC, false))
        assertEquals(0x05, single.functionCode)
        // 稿面 b1-4：01 05 00 02 FF 00 | 2D FA
        assertArrayEquals(byteArrayOf(1, 5, 0, 2, 0xFF.toByte(), 0, 0x2D, 0xFA.toByte()), single.frame)
        assertEquals(8, single.frame.size)

        val multi = ok(planWrite(RegisterArea.COIL, "1", "2", emptyList(), listOf(true, false, true), WriteFormat.DEC, false))
        assertEquals(0x0F, multi.functionCode)
        assertEquals(7..7, multi.payload)
    }

    @Test
    fun badInputIsRejectedWithoutBuildingARequest() {
        assertTrue(planWrite(RegisterArea.HOLDING_REGISTER, "x", "5", listOf("1"), emptyList(), WriteFormat.DEC, false) is PlanResult.Invalid)
        assertTrue(planWrite(RegisterArea.HOLDING_REGISTER, "1", "5", listOf("1", "oops"), emptyList(), WriteFormat.DEC, false) is PlanResult.Invalid)
        assertTrue(planWrite(RegisterArea.HOLDING_REGISTER, "1", "70000", listOf("1"), emptyList(), WriteFormat.DEC, false) is PlanResult.Invalid)
        assertTrue(planWrite(RegisterArea.INPUT_REGISTER, "1", "5", listOf("1"), emptyList(), WriteFormat.DEC, false) is PlanResult.Invalid)
        assertTrue(planWrite(RegisterArea.HOLDING_REGISTER, "1", "5", emptyList(), emptyList(), WriteFormat.DEC, false) is PlanResult.Invalid)
    }

    @Test
    fun secondsLabelTrimsWholeSeconds() {
        assertEquals("1", secondsLabel(1000))
        assertEquals("0.5", secondsLabel(500))
        assertEquals("FC03", fcLabel(3))
        assertEquals("FC16", fcLabel(0x10))
    }

    private fun ok(result: PlanResult): WritePlan = (result as PlanResult.Ok).plan
}
