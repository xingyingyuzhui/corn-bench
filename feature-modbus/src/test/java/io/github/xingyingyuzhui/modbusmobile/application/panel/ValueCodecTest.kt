package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.application.panel.ByteWordOrder.ABCD
import io.github.xingyingyuzhui.modbusmobile.application.panel.ByteWordOrder.BADC
import io.github.xingyingyuzhui.modbusmobile.application.panel.ByteWordOrder.CDAB
import io.github.xingyingyuzhui.modbusmobile.application.panel.ByteWordOrder.DCBA
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class ValueCodecTest {
    private fun words(vararg v: Int) = v

    private fun int(type: DataType, vararg w: Int, order: ByteWordOrder = ABCD) =
        ValueCodec.decode(type, w, order) as RawValue.Integer

    private fun encoded(type: DataType, raw: RawValue, order: ByteWordOrder = ABCD, ascii: Int = 1): IntArray =
        (ValueCodec.encode(type, raw, order, ascii) as EncodeResult.Words).words

    private fun err(type: DataType, raw: RawValue) =
        (ValueCodec.encode(type, raw) as EncodeResult.Error).reason

    // ---- 16 位 ----

    @Test fun u16AndNegativeS16() {
        assertEquals(65535L, int(DataType.U16, 0xFFFF).bits)
        assertEquals(-1L, int(DataType.S16, 0xFFFF).bits)
        assertEquals(-32768L, int(DataType.S16, 0x8000).bits)
        assertEquals(32767L, int(DataType.S16, 0x7FFF).bits)
        assertArrayEquals(words(0xFFFF), encoded(DataType.S16, RawValue.Integer(-1, false)))
        assertArrayEquals(words(0x8000), encoded(DataType.S16, RawValue.Integer(-32768, false)))
    }

    @Test fun byteSwapOnSixteenBit() {
        assertEquals(0x1234L, int(DataType.U16, 0x3412, order = BADC).bits)
        assertEquals(0x1234L, int(DataType.U16, 0x3412, order = DCBA).bits)
        // 字序对 16 位类型没有影响
        assertEquals(0x1234L, int(DataType.U16, 0x1234, order = CDAB).bits)
        assertArrayEquals(words(0x3412), encoded(DataType.U16, RawValue.Integer(0x1234, false), BADC))
    }

    // ---- 32 位四种顺序 ----

    @Test fun u32FourOrders() {
        // 值 0x0A0B0C0D；线路上的寄存器随顺序而不同
        val expected = 0x0A0B0C0DL
        assertEquals(expected, int(DataType.U32, 0x0A0B, 0x0C0D, order = ABCD).bits)
        assertEquals(expected, int(DataType.U32, 0x0C0D, 0x0A0B, order = CDAB).bits)
        assertEquals(expected, int(DataType.U32, 0x0B0A, 0x0D0C, order = BADC).bits)
        assertEquals(expected, int(DataType.U32, 0x0D0C, 0x0B0A, order = DCBA).bits)
        val raw = RawValue.Integer(expected, false)
        assertArrayEquals(words(0x0A0B, 0x0C0D), encoded(DataType.U32, raw, ABCD))
        assertArrayEquals(words(0x0C0D, 0x0A0B), encoded(DataType.U32, raw, CDAB))
        assertArrayEquals(words(0x0B0A, 0x0D0C), encoded(DataType.U32, raw, BADC))
        assertArrayEquals(words(0x0D0C, 0x0B0A), encoded(DataType.U32, raw, DCBA))
    }

    @Test fun s32Negative() {
        assertEquals(-2L, int(DataType.S32, 0xFFFF, 0xFFFE).bits)
        assertEquals(-2L, int(DataType.S32, 0xFFFE, 0xFFFF, order = CDAB).bits)
        assertArrayEquals(words(0xFFFF, 0xFFFE), encoded(DataType.S32, RawValue.Integer(-2, false)))
        assertEquals(4_294_967_295L, int(DataType.U32, 0xFFFF, 0xFFFF).bits)
    }

    @Test fun float32AllOrders() {
        // 1.5f = 0x3FC00000
        for ((order, w) in listOf(
            ABCD to words(0x3FC0, 0x0000),
            CDAB to words(0x0000, 0x3FC0),
            BADC to words(0xC03F, 0x0000),
            DCBA to words(0x0000, 0xC03F),
        )) {
            val raw = ValueCodec.decode(DataType.F32, w, order) as RawValue.Real
            assertEquals("$order", 1.5, raw.value, 0.0)
            assertArrayEquals("$order", w, encoded(DataType.F32, RawValue.Real(1.5), order))
        }
    }

    @Test fun float32SpecialsAreNotFinite() {
        val nan = ValueCodec.decode(DataType.F32, words(0x7FC0, 0x0000)) as RawValue.Real
        assertTrue(nan.value.isNaN())
        assertNull(ValueCodec.toEngineering(nan, Conversion()))
        val inf = ValueCodec.decode(DataType.F32, words(0x7F80, 0x0000)) as RawValue.Real
        assertNull(ValueCodec.toEngineering(inf, Conversion()))
        assertNull(ValueCodec.format(Double.NaN, 2))
    }

    // ---- 64 位 ----

    @Test fun u64KeepsFullPrecision() {
        val max = ValueCodec.decode(DataType.U64, words(0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF)) as RawValue.Integer
        assertEquals(BigInteger("18446744073709551615"), max.toBigInteger())
        // 2^53 + 1 经 Double 会丢，经 Long 不会
        val big = ValueCodec.decode(DataType.U64, words(0x0020, 0x0000, 0x0000, 0x0001)) as RawValue.Integer
        assertEquals(BigInteger("9007199254740993"), big.toBigInteger())
        assertArrayEquals(words(0x0020, 0x0000, 0x0000, 0x0001), encoded(DataType.U64, big))
        assertArrayEquals(
            words(0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF),
            encoded(DataType.U64, RawValue.Integer(-1L, true)),
        )
    }

    @Test fun s64AndOrders() {
        assertEquals(-1L, int(DataType.S64, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF).bits)
        assertEquals(Long.MIN_VALUE, int(DataType.S64, 0x8000, 0, 0, 0).bits)
        val value = 0x0102030405060708L
        assertEquals(value, int(DataType.S64, 0x0102, 0x0304, 0x0506, 0x0708, order = ABCD).bits)
        assertEquals(value, int(DataType.S64, 0x0708, 0x0506, 0x0304, 0x0102, order = CDAB).bits)
        assertEquals(value, int(DataType.S64, 0x0201, 0x0403, 0x0605, 0x0807, order = BADC).bits)
        assertEquals(value, int(DataType.S64, 0x0807, 0x0605, 0x0403, 0x0201, order = DCBA).bits)
    }

    @Test fun f64RoundTrip() {
        val w = encoded(DataType.F64, RawValue.Real(-123.456), CDAB)
        assertEquals(4, w.size)
        assertEquals(-123.456, (ValueCodec.decode(DataType.F64, w, CDAB) as RawValue.Real).value, 0.0)
    }

    // ---- BCD / ASCII / 位 ----

    @Test fun bcd() {
        assertEquals(1234L, int(DataType.BCD16, 0x1234).bits)
        assertEquals(12345678L, int(DataType.BCD32, 0x1234, 0x5678).bits)
        assertArrayEquals(words(0x0042), encoded(DataType.BCD16, RawValue.Integer(42, false)))
        assertArrayEquals(words(0x1234, 0x5678), encoded(DataType.BCD32, RawValue.Integer(12345678, false)))
        assertTrue(ValueCodec.decode(DataType.BCD16, words(0x12AB)) is RawValue.Invalid)
        assertEquals(EncodeError.OUT_OF_RANGE, err(DataType.BCD16, RawValue.Integer(10_000, false)))
        assertEquals(EncodeError.OUT_OF_RANGE, err(DataType.BCD16, RawValue.Integer(-1, false)))
    }

    @Test fun ascii() {
        val text = ValueCodec.decode(DataType.ASCII, words(0x4142, 0x4300), asciiRegisters = 2) as RawValue.Text
        assertEquals("ABC", text.value)
        assertArrayEquals(words(0x4142, 0x4300), encoded(DataType.ASCII, RawValue.Text("ABC"), ascii = 2))
        assertEquals(EncodeError.BAD_TEXT, (ValueCodec.encode(DataType.ASCII, RawValue.Text("ABCDE"), asciiRegisters = 2) as EncodeResult.Error).reason)
        assertEquals(EncodeError.BAD_TEXT, (ValueCodec.encode(DataType.ASCII, RawValue.Text("温"), asciiRegisters = 2) as EncodeResult.Error).reason)
        // 非可打印字节显示成 ?
        assertEquals("A?", (ValueCodec.decode(DataType.ASCII, words(0x41FF)) as RawValue.Text).value)
    }

    @Test fun bitExtractAndSet() {
        val w = words(0b0000_0000_0000_0101)
        assertEquals(true, (ValueCodec.decode(DataType.BIT, w, bit = 0) as RawValue.Flag).value)
        assertEquals(false, (ValueCodec.decode(DataType.BIT, w, bit = 1) as RawValue.Flag).value)
        assertEquals(true, (ValueCodec.decode(DataType.BIT, w, bit = 2) as RawValue.Flag).value)
        assertEquals(true, (ValueCodec.decode(DataType.BIT, words(0x8000), bit = 15) as RawValue.Flag).value)
        assertEquals(0b0111, ValueCodec.withBit(0b0101, 1, true))
        assertEquals(0b0001, ValueCodec.withBit(0b0101, 2, false))
        assertEquals(0xFFFF, ValueCodec.withBit(0x7FFF, 15, true))
        assertEquals(0x7FFF, ValueCodec.withBit(0xFFFF, 15, false))
        assertEquals(EncodeError.NOT_ENCODABLE, err(DataType.BIT, RawValue.Flag(true)))
    }

    // ---- 范围检查 ----

    @Test fun outOfRangeIsRejectedNotWrapped() {
        assertEquals(EncodeError.OUT_OF_RANGE, err(DataType.U16, RawValue.Integer(65536, false)))
        assertEquals(EncodeError.OUT_OF_RANGE, err(DataType.U16, RawValue.Integer(-1, false)))
        assertEquals(EncodeError.OUT_OF_RANGE, err(DataType.S16, RawValue.Integer(32768, false)))
        assertEquals(EncodeError.OUT_OF_RANGE, err(DataType.S16, RawValue.Integer(-32769, false)))
        assertEquals(EncodeError.OUT_OF_RANGE, err(DataType.U32, RawValue.Integer(4_294_967_296L, false)))
        assertEquals(EncodeError.OUT_OF_RANGE, err(DataType.S32, RawValue.Integer(2_147_483_648L, false)))
        assertEquals(EncodeError.OUT_OF_RANGE, err(DataType.U64, RawValue.Integer(-1, false)))
        assertEquals(EncodeError.OUT_OF_RANGE, err(DataType.F32, RawValue.Real(1e39)))
        assertEquals(EncodeError.NOT_FINITE, err(DataType.F32, RawValue.Real(Double.NaN)))
        assertEquals(EncodeError.TYPE_MISMATCH, err(DataType.U16, RawValue.Text("1")))
        // 边界值可以
        assertArrayEquals(words(0xFFFF), encoded(DataType.U16, RawValue.Integer(65535, false)))
        assertArrayEquals(words(0x7FFF, 0xFFFF), encoded(DataType.S32, RawValue.Integer(Int.MAX_VALUE.toLong(), false)))
    }

    // ---- 换算 ----

    private val temp = Conversion(scale = 0.1, offset = -20.0, decimals = 1)

    @Test fun conversionBothDirections() {
        // 1000 × 0.1 − 20 = 80
        val eng = ValueCodec.toEngineering(RawValue.Integer(1000, false), temp)!!
        assertEquals(80.0, eng, 1e-9)
        val raw = ValueCodec.fromEngineering(DataType.U16, 80.0, temp).getOrThrow() as RawValue.Integer
        assertEquals(1000L, raw.bits)
        // 0.1 不是二进制精确数：反算后四舍五入到整数，不能得到 999 / 1001
        for (v in listOf(-20.0, -19.9, 0.0, 12.3, 80.0, 123.4)) {
            val r = ValueCodec.fromEngineering(DataType.S16, v, temp).getOrThrow() as RawValue.Integer
            assertEquals("$v", v, ValueCodec.toEngineering(r, temp)!!, 0.05 + 1e-9)
        }
        assertEquals("80.0", ValueCodec.format(eng, temp.decimals))
        assertEquals("80", ValueCodec.format(eng, null))
        assertEquals("12.35", ValueCodec.format(12.345, 2))
        assertEquals("-0.5", ValueCodec.format(-0.5, null))
    }

    @Test fun scaleZeroIsRejected() {
        val zero = Conversion(scale = 0.0)
        assertTrue(!zero.isValid)
        assertNull(zero.invert(5.0))
        val r = ValueCodec.fromEngineering(DataType.U16, 5.0, zero)
        assertEquals(EncodeError.BAD_CONVERSION, (r.exceptionOrNull() as EncodeException).error)
        assertEquals(
            EncodeError.NOT_FINITE,
            (ValueCodec.fromEngineering(DataType.U16, Double.NaN, Conversion()).exceptionOrNull() as EncodeException).error,
        )
    }

    @Test fun writingConvertedValueChecksTargetRange() {
        // 把 0..100 映射到 U16 的 scale 0.01：工程值 700 → 原始 70000 > 65535
        val c = Conversion(scale = 0.01)
        val raw = ValueCodec.fromEngineering(DataType.U16, 700.0, c).getOrThrow()
        assertEquals(EncodeError.OUT_OF_RANGE, (ValueCodec.encode(DataType.U16, raw) as EncodeResult.Error).reason)
        // 负数写无符号：同样拒绝而不是回绕
        val neg = ValueCodec.fromEngineering(DataType.U16, -5.0, Conversion()).getOrThrow()
        assertEquals(EncodeError.OUT_OF_RANGE, (ValueCodec.encode(DataType.U16, neg) as EncodeResult.Error).reason)
    }

    @Test fun sixtyFourBitWriteBeyondDoublePrecisionIsRefused() {
        val r = ValueCodec.fromEngineering(DataType.S64, 1.0e18, Conversion())
        assertEquals(EncodeError.PRECISION, (r.exceptionOrNull() as EncodeException).error)
        val ok = ValueCodec.fromEngineering(DataType.S64, 123456789.0, Conversion()).getOrThrow() as RawValue.Integer
        assertEquals(123456789L, ok.bits)
    }

    @Test fun thresholdPicksMostSevereBand() {
        val bands = listOf(
            ThresholdBand(ThresholdLevel.WARN, min = 60.0, max = 90.0),
            ThresholdBand(ThresholdLevel.ALERT, min = 80.0),
            ThresholdBand(ThresholdLevel.ALERT, max = 0.0),
        )
        assertEquals(ThresholdLevel.NORMAL, bands.levelFor(30.0))
        assertEquals(ThresholdLevel.WARN, bands.levelFor(60.0))
        assertEquals(ThresholdLevel.ALERT, bands.levelFor(85.0))
        assertEquals(ThresholdLevel.ALERT, bands.levelFor(-1.0))
        assertEquals(ThresholdLevel.NORMAL, bands.levelFor(null))
        assertEquals(ThresholdLevel.NORMAL, bands.levelFor(Double.NaN))
    }
}
