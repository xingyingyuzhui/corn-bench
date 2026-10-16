package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointDefinitionTest {
    private fun point(
        area: RegisterArea = RegisterArea.HOLDING_REGISTER,
        type: DataType = DataType.U16,
        address: Int = 0,
        unitId: Int = 1,
        conversion: Conversion = Conversion(),
        thresholds: List<ThresholdBand> = emptyList(),
        bit: Int = 0,
        ascii: Int = 1,
        min: Double? = null,
        max: Double? = null,
    ) = PointDefinition(
        id = "p1", name = "温度", unitId = unitId, area = area, address = address, type = type,
        bit = bit, asciiRegisters = ascii, conversion = conversion, thresholds = thresholds, rangeMin = min, rangeMax = max,
    )

    @Test fun validPointHasNoIssues() {
        assertTrue(point().validate().isEmpty())
        assertTrue(point(area = RegisterArea.COIL, type = DataType.BOOL).validate().isEmpty())
        assertTrue(point(type = DataType.F32, address = 65534).validate().isEmpty())
    }

    @Test fun quantityFollowsType() {
        assertEquals(1, point(type = DataType.S16).quantity)
        assertEquals(2, point(type = DataType.F32).quantity)
        assertEquals(4, point(type = DataType.U64).quantity)
        assertEquals(5, point(type = DataType.ASCII, ascii = 5).quantity)
        assertEquals(1, point(area = RegisterArea.COIL, type = DataType.BOOL).quantity)
    }

    @Test fun addressAndSpanAreChecked() {
        assertTrue(PointIssue.ADDRESS_RANGE in point(address = 65536).validate())
        assertTrue(PointIssue.ADDRESS_RANGE in point(address = -1).validate())
        assertTrue(PointIssue.SPAN_OVERFLOW in point(type = DataType.F32, address = 65535).validate())
        assertTrue(PointIssue.SPAN_OVERFLOW !in point(type = DataType.F32, address = 65534).validate())
        assertTrue(PointIssue.TOO_MANY_REGISTERS in point(type = DataType.ASCII, ascii = 126).validate())
    }

    @Test fun unitMustBeAnAddressedSlave() {
        assertTrue(PointIssue.UNIT_RANGE in point(unitId = 0).validate())
        assertTrue(PointIssue.UNIT_RANGE in point(unitId = 248).validate())
        assertTrue(point(unitId = 247).validate().isEmpty())
    }

    @Test fun typeAndAreaMustAgree() {
        assertTrue(PointIssue.TYPE_AREA_MISMATCH in point(area = RegisterArea.COIL, type = DataType.U16).validate())
        assertTrue(PointIssue.TYPE_AREA_MISMATCH in point(area = RegisterArea.HOLDING_REGISTER, type = DataType.BOOL).validate())
    }

    @Test fun conversionAndRangesAreChecked() {
        assertTrue(PointIssue.BAD_CONVERSION in point(conversion = Conversion(scale = 0.0)).validate())
        assertTrue(PointIssue.BAD_CONVERSION in point(conversion = Conversion(scale = Double.NaN)).validate())
        assertTrue(PointIssue.DECIMALS_RANGE in point(conversion = Conversion(decimals = 11)).validate())
        assertTrue(PointIssue.RANGE_ORDER in point(min = 10.0, max = 10.0).validate())
        assertTrue(PointIssue.BIT_RANGE in point(type = DataType.BIT, bit = 16).validate())
        assertTrue(
            PointIssue.THRESHOLD_ORDER in point(thresholds = listOf(ThresholdBand(ThresholdLevel.WARN, 5.0, 1.0))).validate(),
        )
    }

    @Test fun pollPeriodIsBounded() {
        assertTrue(PointIssue.POLL_RANGE in point().copy(pollMs = 99).validate())
        assertTrue(PointIssue.POLL_RANGE in point().copy(pollMs = 600_001).validate())
        assertTrue(point().copy(pollMs = 100).validate().isEmpty())
        assertTrue(point().copy(pollMs = 600_000).validate().isEmpty())
    }

    @Test fun writableOnlyForCoilsAndHolding() {
        assertTrue(point(area = RegisterArea.HOLDING_REGISTER).isWritable)
        assertTrue(point(area = RegisterArea.COIL, type = DataType.BOOL).isWritable)
        assertFalse(point(area = RegisterArea.INPUT_REGISTER).isWritable)
        assertFalse(point(area = RegisterArea.DISCRETE_INPUT, type = DataType.BOOL).isWritable)
    }

    @Test fun formatWordsEndToEnd() {
        val temp = point(type = DataType.S16, conversion = Conversion(0.1, -20.0, 1))
        assertEquals("80.0", temp.formatWords(intArrayOf(1000)))
        assertEquals("-21.0", temp.formatWords(intArrayOf(0xFFFF - 9))) // 原始 -10 → -21.0
        val nan = point(type = DataType.F32)
        assertNull(nan.formatWords(intArrayOf(0x7FC0, 0)))
        assertEquals("AB", point(type = DataType.ASCII).formatWords(intArrayOf(0x4142)))
    }
}
