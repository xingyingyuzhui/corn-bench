package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea

enum class ThresholdLevel { NORMAL, WARN, ALERT }

/** 阈值区间：[min, max)（min 含、max 不含；为空表示不设界）。多个区间同时命中时取最严重的一档。 */
data class ThresholdBand(val level: ThresholdLevel, val min: Double? = null, val max: Double? = null) {
    fun contains(value: Double): Boolean = (min == null || value >= min) && (max == null || value < max)
}

fun List<ThresholdBand>.levelFor(value: Double?): ThresholdLevel {
    if (value == null || !value.isFinite()) return ThresholdLevel.NORMAL
    return filter { it.contains(value) }.maxByOrNull { it.level.ordinal }?.level ?: ThresholdLevel.NORMAL
}

enum class PointIssue {
    BLANK_ID, BLANK_NAME,

    /** 轮询读要有明确的从站号：1..247（0 是广播，读不到回应）。 */
    UNIT_RANGE,
    ADDRESS_RANGE,

    /** 起始地址 + 占用个数超过 65536。 */
    SPAN_OVERFLOW,

    /** 类型和区域对不上：BOOL 只用于线圈 / 离散输入，其余类型只用于输入 / 保持寄存器。 */
    TYPE_AREA_MISMATCH,
    BIT_RANGE,
    ASCII_LENGTH,

    /** 一次读最多 125 个寄存器。 */
    TOO_MANY_REGISTERS,
    BAD_CONVERSION, DECIMALS_RANGE,

    /** 轮询周期 100..600000 ms。 */
    POLL_RANGE, RANGE_ORDER, THRESHOLD_ORDER,
}

/** 一个“点位”：从哪个从站的哪个地址、按什么类型解释、怎么换算和着色。 */
data class PointDefinition(
    val id: String,
    val name: String,
    val unitId: Int,
    val area: RegisterArea,
    val address: Int,
    val type: DataType,
    val order: ByteWordOrder = ByteWordOrder.ABCD,
    /** BIT 类型：寄存器里的第几位（0 = 最低位）。 */
    val bit: Int = 0,
    /** ASCII 类型：占几个寄存器。 */
    val asciiRegisters: Int = 1,
    val conversion: Conversion = Conversion(),
    val unitLabel: String = "",
    val rangeMin: Double? = null,
    val rangeMax: Double? = null,
    val thresholds: List<ThresholdBand> = emptyList(),
    /** 轮询周期（毫秒），100..600000。 */
    val pollMs: Int = DEFAULT_POLL_MS,
) {
    val isBitArea: Boolean get() = area == RegisterArea.COIL || area == RegisterArea.DISCRETE_INPUT

    /** 读这个点位要读几个线圈 / 寄存器。 */
    val quantity: Int get() = if (type == DataType.BOOL) 1 else type.registers ?: asciiRegisters

    /** 线圈 / 保持寄存器可以写；离散输入 / 输入寄存器只读。 */
    val isWritable: Boolean get() = area == RegisterArea.COIL || area == RegisterArea.HOLDING_REGISTER

    fun validate(): List<PointIssue> {
        val issues = LinkedHashSet<PointIssue>()
        if (id.isBlank()) issues += PointIssue.BLANK_ID
        if (name.isBlank()) issues += PointIssue.BLANK_NAME
        if (unitId !in 1..247) issues += PointIssue.UNIT_RANGE
        if (address !in 0..65535) issues += PointIssue.ADDRESS_RANGE
        val typeOk = if (isBitArea) type == DataType.BOOL else type != DataType.BOOL
        if (!typeOk) issues += PointIssue.TYPE_AREA_MISMATCH
        if (type == DataType.BIT && bit !in 0..15) issues += PointIssue.BIT_RANGE
        if (type == DataType.ASCII && asciiRegisters !in 1..125) issues += PointIssue.ASCII_LENGTH
        val count = quantity
        if (!isBitArea && count > 125) issues += PointIssue.TOO_MANY_REGISTERS
        if (address in 0..65535 && address + count > 65536) issues += PointIssue.SPAN_OVERFLOW
        if (!conversion.isValid) issues += PointIssue.BAD_CONVERSION
        if (pollMs !in MIN_POLL_MS..MAX_POLL_MS) issues += PointIssue.POLL_RANGE
        conversion.decimals?.let { if (it !in 0..10) issues += PointIssue.DECIMALS_RANGE }
        if (rangeMin != null && rangeMax != null && !(rangeMin < rangeMax)) issues += PointIssue.RANGE_ORDER
        if (thresholds.any { it.min != null && it.max != null && !(it.min < it.max) }) issues += PointIssue.THRESHOLD_ORDER
        return issues.toList()
    }

    /** 读到的原始字 → 显示用的工程值文本（非法 / 非有限数返回 null）。 */
    fun formatWords(words: IntArray): String? {
        val raw = ValueCodec.decode(type, words, order, bit, asciiRegisters)
        if (raw is RawValue.Text) return raw.value
        val eng = ValueCodec.toEngineering(raw, conversion) ?: return null
        return ValueCodec.format(eng, conversion.decimals)
    }

    companion object {
        const val DEFAULT_POLL_MS = 1000
        const val MIN_POLL_MS = 100
        const val MAX_POLL_MS = 600_000
    }
}
