package io.github.xingyingyuzhui.modbusmobile.application.panel

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/*
 * 面板的数据换算（寄存器字 ↔ 数值）。纯 Kotlin：没有 Android 类型、没有 modbus-core 以外的依赖，
 * 写成“以后可以原样搬进 modbus-core”的形状（panel-plan 路线 A：core 本轮不动）。
 */

/** 数据类型。[registers] = 占几个 16 位寄存器；ASCII 由点位的 asciiRegisters 决定，BOOL 读线圈 / 离散输入（不占寄存器）。 */
enum class DataType(val registers: Int?) {
    U16(1), S16(1), U32(2), S32(2), F32(2), U64(4), S64(4), F64(4),
    BCD16(1), BCD32(2), ASCII(null), BIT(1), BOOL(null),
    ;

    val isFloat: Boolean get() = this == F32 || this == F64
    val isSigned: Boolean get() = this == S16 || this == S32 || this == S64
}

/**
 * 字节序 / 字序。名字按“把 32 位值 0xAABBCCDD 放进两个寄存器时寄存器里的字节顺序”：
 * ABCD = 高字在前、字内高字节在前（标准大端）；CDAB = 字序交换；BADC = 字内字节交换；DCBA = 两者都交换（全小端）。
 * 16 位类型只有字节交换起作用；64 位类型的“字序交换”是四个字整体倒序。
 */
enum class ByteWordOrder(val byteSwap: Boolean, val wordSwap: Boolean) {
    ABCD(false, false), CDAB(false, true), BADC(true, false), DCBA(true, true),
}

/** 解码出来的原始值（还没换算）。64 位整数不经 Double，保证精度。 */
sealed interface RawValue {
    /** [bits] 是按类型解释后的整数；无符号 64 位时 bits 的最高位可能为 1，用 [unsigned] 区分。 */
    data class Integer(val bits: Long, val unsigned: Boolean) : RawValue {
        fun toBigInteger(): BigInteger =
            if (unsigned && bits < 0) BigInteger.valueOf(bits).add(BigInteger.ONE.shiftLeft(64)) else BigInteger.valueOf(bits)

        fun toDouble(): Double = toBigInteger().toDouble()
    }

    data class Real(val value: Double) : RawValue
    data class Text(val value: String) : RawValue
    data class Flag(val value: Boolean) : RawValue
    data class Invalid(val reason: String) : RawValue
}

sealed interface EncodeResult {
    data class Words(val words: IntArray) : EncodeResult {
        override fun equals(other: Any?) = other is Words && words.contentEquals(other.words)
        override fun hashCode() = words.contentHashCode()
        override fun toString() = "Words(${words.joinToString { "%04X".format(it) }})"
    }

    data class Error(val reason: EncodeError) : EncodeResult
}

enum class EncodeError {
    /** 超出该类型能表示的范围（含换算后）。 */
    OUT_OF_RANGE,

    /** 缩放系数为 0 或不是有限数，无法反算。 */
    BAD_CONVERSION,

    /** 输入不是有限数（NaN / ∞）。 */
    NOT_FINITE,

    /** 64 位整数经 Double 反算会丢精度（绝对值超过 2^53）。 */
    PRECISION,

    /** 文本含非 ASCII 字符或太长。 */
    BAD_TEXT,

    /** 这个类型不能直接编码成寄存器字（BIT、BOOL 用 [ValueCodec.withBit] / 线圈写入）。 */
    NOT_ENCODABLE,

    /** 值与类型不匹配（例如把文本写进 U16）。 */
    TYPE_MISMATCH,
}

/** 换算：工程值 = 原始值 × [scale] + [offset]；[decimals] 只影响显示，不影响写入。 */
data class Conversion(val scale: Double = 1.0, val offset: Double = 0.0, val decimals: Int? = null) {
    val isValid: Boolean get() = scale.isFinite() && scale != 0.0 && offset.isFinite()

    fun apply(raw: Double): Double = raw * scale + offset

    /** 反算：工程值 → 原始值；缩放为 0 / 非有限返回 null（拒绝写入）。 */
    fun invert(engineering: Double): Double? =
        if (!isValid || !engineering.isFinite()) null else (engineering - offset) / scale
}

object ValueCodec {
    private const val MAX_EXACT_DOUBLE = 9_007_199_254_740_992.0 // 2^53

    /** 把读到的字按字节 / 字序整理成“标准大端”的字数组（交换是对称的，编码也用它）。 */
    fun canonical(words: IntArray, order: ByteWordOrder): IntArray {
        val swapped = IntArray(words.size) {
            val w = words[it] and 0xFFFF
            if (order.byteSwap) ((w and 0xFF) shl 8) or (w ushr 8) else w
        }
        return if (order.wordSwap) swapped.reversedArray() else swapped
    }

    /**
     * 解码。[words] 的个数必须等于该类型需要的寄存器数（ASCII 为 [asciiRegisters]）；BOOL 请用 [decodeBit]。
     * BCD 里出现 A–F 的半字节返回 [RawValue.Invalid]。
     */
    fun decode(
        type: DataType,
        words: IntArray,
        order: ByteWordOrder = ByteWordOrder.ABCD,
        bit: Int = 0,
        asciiRegisters: Int = 1,
    ): RawValue {
        val expected = type.registers ?: asciiRegisters
        require(type != DataType.BOOL) { "BOOL 用 decodeBit" }
        require(words.size == expected) { "$type 需要 $expected 个寄存器，实际 ${words.size}" }
        val w = canonical(words, order)
        return when (type) {
            DataType.U16 -> RawValue.Integer(w[0].toLong(), false)
            DataType.S16 -> RawValue.Integer(w[0].toShort().toLong(), false)
            DataType.U32 -> RawValue.Integer(join(w, 2), false)
            DataType.S32 -> RawValue.Integer(join(w, 2).toInt().toLong(), false)
            DataType.F32 -> RawValue.Real(java.lang.Float.intBitsToFloat(join(w, 2).toInt()).toDouble())
            DataType.U64 -> RawValue.Integer(join(w, 4), true)
            DataType.S64 -> RawValue.Integer(join(w, 4), false)
            DataType.F64 -> RawValue.Real(java.lang.Double.longBitsToDouble(join(w, 4)))
            DataType.BCD16, DataType.BCD32 -> decodeBcd(w)
            DataType.ASCII -> RawValue.Text(decodeAscii(w))
            DataType.BIT -> {
                require(bit in 0..15) { "位号 0..15" }
                RawValue.Flag((w[0] ushr bit) and 1 == 1)
            }
            DataType.BOOL -> error("unreachable")
        }
    }

    fun decodeBit(value: Boolean): RawValue = RawValue.Flag(value)

    /** 编码成寄存器字（再按字节 / 字序还原成线路顺序）。 */
    fun encode(
        type: DataType,
        raw: RawValue,
        order: ByteWordOrder = ByteWordOrder.ABCD,
        asciiRegisters: Int = 1,
    ): EncodeResult {
        val canonical: IntArray = when (type) {
            DataType.BIT, DataType.BOOL -> return EncodeResult.Error(EncodeError.NOT_ENCODABLE)
            DataType.ASCII -> {
                val text = (raw as? RawValue.Text)?.value ?: return EncodeResult.Error(EncodeError.TYPE_MISMATCH)
                encodeAscii(text, asciiRegisters) ?: return EncodeResult.Error(EncodeError.BAD_TEXT)
            }
            DataType.F32, DataType.F64 -> {
                val v = when (raw) {
                    is RawValue.Real -> raw.value
                    is RawValue.Integer -> raw.toDouble()
                    else -> return EncodeResult.Error(EncodeError.TYPE_MISMATCH)
                }
                if (!v.isFinite()) return EncodeResult.Error(EncodeError.NOT_FINITE)
                if (type == DataType.F32) {
                    if (kotlin.math.abs(v) > Float.MAX_VALUE.toDouble()) return EncodeResult.Error(EncodeError.OUT_OF_RANGE)
                    split(java.lang.Float.floatToIntBits(v.toFloat()).toLong() and 0xFFFFFFFFL, 2)
                } else {
                    split(java.lang.Double.doubleToLongBits(v), 4)
                }
            }
            else -> {
                val integer = (raw as? RawValue.Integer)?.toBigInteger() ?: return EncodeResult.Error(EncodeError.TYPE_MISMATCH)
                encodeInteger(type, integer) ?: return EncodeResult.Error(EncodeError.OUT_OF_RANGE)
            }
        }
        return EncodeResult.Words(canonical(canonical, order))
    }

    /** 读出的字里把第 [bit] 位置 / 清，其余位不动（“位写入”要先读、改一位、再写回，非原子，由调用方提示）。 */
    fun withBit(word: Int, bit: Int, on: Boolean): Int {
        require(bit in 0..15) { "位号 0..15" }
        val mask = 1 shl bit
        return (if (on) word or mask else word and mask.inv()) and 0xFFFF
    }

    /** 原始值 → 工程值。文本 / 非法值返回 null；非有限数（F32 的 NaN / ∞）也返回 null，由界面显示“—”。 */
    fun toEngineering(raw: RawValue, conversion: Conversion): Double? {
        val base = when (raw) {
            is RawValue.Integer -> raw.toDouble()
            is RawValue.Real -> raw.value
            is RawValue.Flag -> return if (raw.value) 1.0 else 0.0
            else -> return null
        }
        return conversion.apply(base).takeIf { it.isFinite() }
    }

    /**
     * 工程值 → 原始值（写入用）：整数类型四舍五入到最近整数；缩放为 0 返回 BAD_CONVERSION；
     * 64 位整数经 Double 反算只在 ±2^53 内才精确，超出返回 PRECISION。范围检查在 [encode] 里做。
     */
    fun fromEngineering(type: DataType, engineering: Double, conversion: Conversion): Result<RawValue> {
        if (!engineering.isFinite()) return Result.failure(EncodeException(EncodeError.NOT_FINITE))
        val raw = conversion.invert(engineering) ?: return Result.failure(EncodeException(EncodeError.BAD_CONVERSION))
        if (!raw.isFinite()) return Result.failure(EncodeException(EncodeError.OUT_OF_RANGE))
        return when {
            type.isFloat -> Result.success(RawValue.Real(raw))
            type == DataType.ASCII || type == DataType.BIT || type == DataType.BOOL ->
                Result.failure(EncodeException(EncodeError.NOT_ENCODABLE))
            else -> {
                val rounded = Math.rint(raw)
                if (kotlin.math.abs(rounded) > MAX_EXACT_DOUBLE) {
                    return Result.failure(
                        EncodeException(if (type == DataType.U64 || type == DataType.S64) EncodeError.PRECISION else EncodeError.OUT_OF_RANGE),
                    )
                }
                Result.success(RawValue.Integer(rounded.toLong(), type == DataType.U64 && rounded >= 0))
            }
        }
    }

    /** 显示：[decimals] 为空时用最短表示（80.0 → "80"）；非有限数返回 null。 */
    fun format(value: Double, decimals: Int?): String? {
        if (!value.isFinite()) return null
        val decimal = BigDecimal.valueOf(value)
        return if (decimals == null) {
            decimal.stripTrailingZeros().toPlainString()
        } else {
            decimal.setScale(decimals.coerceIn(0, 10), RoundingMode.HALF_UP).toPlainString()
        }
    }

    // ---- 内部 ----

    private fun join(w: IntArray, count: Int): Long {
        var result = 0L
        for (i in 0 until count) result = (result shl 16) or (w[i].toLong() and 0xFFFF)
        return result
    }

    private fun split(value: Long, count: Int): IntArray =
        IntArray(count) { ((value ushr (16 * (count - 1 - it))) and 0xFFFF).toInt() }

    private fun decodeBcd(w: IntArray): RawValue {
        var result = 0L
        for (word in w) {
            for (shift in intArrayOf(12, 8, 4, 0)) {
                val digit = (word ushr shift) and 0xF
                if (digit > 9) return RawValue.Invalid("BCD 半字节 $digit")
                result = result * 10 + digit
            }
        }
        return RawValue.Integer(result, false)
    }

    private fun decodeAscii(w: IntArray): String {
        val chars = StringBuilder()
        for (word in w) {
            for (b in intArrayOf(word ushr 8, word and 0xFF)) {
                chars.append(if (b in 0x20..0x7E) b.toChar() else if (b == 0) '\u0000' else '?')
            }
        }
        // 去掉尾部的空字符和空格；中间的空字符保留成空格不好看，截到第一个空字符之前
        val text = chars.toString().substringBefore('\u0000')
        return text.trimEnd()
    }

    private fun encodeAscii(text: String, registers: Int): IntArray? {
        if (registers < 1 || text.length > registers * 2) return null
        if (text.any { it.code !in 0x20..0x7E }) return null
        val bytes = IntArray(registers * 2) { if (it < text.length) text[it].code else 0 }
        return IntArray(registers) { (bytes[it * 2] shl 8) or bytes[it * 2 + 1] }
    }

    private fun encodeInteger(type: DataType, value: BigInteger): IntArray? {
        fun within(min: BigInteger, max: BigInteger) = value >= min && value <= max
        val two = BigInteger.valueOf(2)
        return when (type) {
            DataType.U16 -> if (within(BigInteger.ZERO, BigInteger.valueOf(0xFFFF))) split(value.toLong(), 1) else null
            DataType.S16 -> if (within(BigInteger.valueOf(-32768), BigInteger.valueOf(32767))) split(value.toLong() and 0xFFFF, 1) else null
            DataType.U32 -> if (within(BigInteger.ZERO, BigInteger.valueOf(0xFFFFFFFFL))) split(value.toLong(), 2) else null
            DataType.S32 -> if (within(BigInteger.valueOf(Int.MIN_VALUE.toLong()), BigInteger.valueOf(Int.MAX_VALUE.toLong()))) {
                split(value.toLong() and 0xFFFFFFFFL, 2)
            } else {
                null
            }
            DataType.U64 -> if (within(BigInteger.ZERO, two.pow(64) - BigInteger.ONE)) split(value.toLong(), 4) else null
            DataType.S64 -> if (within(two.pow(63).negate(), two.pow(63) - BigInteger.ONE)) split(value.toLong(), 4) else null
            DataType.BCD16 -> if (within(BigInteger.ZERO, BigInteger.valueOf(9_999))) bcd(value.toLong(), 1) else null
            DataType.BCD32 -> if (within(BigInteger.ZERO, BigInteger.valueOf(99_999_999))) bcd(value.toLong(), 2) else null
            else -> null
        }
    }

    private fun bcd(value: Long, registers: Int): IntArray {
        var rest = value
        val words = IntArray(registers)
        for (i in registers - 1 downTo 0) {
            var word = 0
            for (shift in intArrayOf(0, 4, 8, 12)) {
                word = word or ((rest % 10).toInt() shl shift)
                rest /= 10
            }
            words[i] = word
        }
        return words
    }
}

class EncodeException(val error: EncodeError) : Exception(error.name)
