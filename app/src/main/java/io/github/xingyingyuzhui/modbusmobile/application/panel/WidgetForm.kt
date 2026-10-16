package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea

/*
 * 添加 / 编辑组件向导的表单（纯 Kotlin）。所有输入框都是字符串（用户可能正输到一半），
 * [WidgetForm.build] 一次性校验并产出 [Widget]；界面只负责显示 [FormError]。
 */

enum class FormField { NAME, UNIT, ADDRESS, BIT, ASCII, SCALE, OFFSET, DECIMALS, RANGE_MIN, RANGE_MAX, POLL, THRESHOLD, STATE }

enum class FormError {
    REQUIRED, NOT_A_NUMBER, NOT_INTEGER, OUT_OF_RANGE, ZERO_SCALE, RANGE_ORDER, DUPLICATE, SPAN_OVERFLOW, TOO_MANY,
    /** 曲线：时间窗口比内存保留还长。 */
    EXCEEDS_RETENTION,
}

/** 阈值方向：≥ = 区间 [值, ∞)；< = 区间 (−∞, 值)。 */
enum class ThresholdOp { GE, LT }

data class ThresholdRow(val op: ThresholdOp = ThresholdOp.GE, val value: String = "", val level: ThresholdLevel = ThresholdLevel.WARN)

data class StateRow(val value: String = "", val label: String = "", val tone: StateTone = StateTone.BLUE)

class FormResult(
    val widget: Widget?,
    val errors: Map<FormField, FormError>,
    /** 出错的阈值行 / 映射行下标。 */
    val badThresholds: Set<Int> = emptySet(),
    val badStates: Set<Int> = emptySet(),
) {
    val ok: Boolean get() = widget != null && errors.isEmpty()
}

data class WidgetForm(
    val kind: String = WidgetKinds.NUMBER,
    val name: String = "",
    val unitId: String = "1",
    val area: RegisterArea = RegisterArea.HOLDING_REGISTER,
    val address: String = "0",
    val type: DataType = DataType.U16,
    val order: ByteWordOrder = ByteWordOrder.ABCD,
    val bit: String = "0",
    val asciiRegisters: String = "1",
    val scale: String = "1",
    val offset: String = "0",
    val decimals: String = "",
    val unitLabel: String = "",
    val rangeMin: String = "",
    val rangeMax: String = "",
    val thresholds: List<ThresholdRow> = emptyList(),
    val pollMs: String = PointDefinition.DEFAULT_POLL_MS.toString(),
    val onLabel: String = "",
    val offLabel: String = "",
    val states: List<StateRow> = emptyList(),
    val spanColumns: Int = 1,
    val spanRows: Int = 1,
) {
    val usesConversion: Boolean get() = kind == WidgetKinds.NUMBER || kind == WidgetKinds.PROGRESS
    val usesRange: Boolean get() = kind == WidgetKinds.PROGRESS
    val usesThresholds: Boolean get() = kind == WidgetKinds.NUMBER || kind == WidgetKinds.PROGRESS
    val usesUnitLabel: Boolean get() = kind == WidgetKinds.NUMBER || kind == WidgetKinds.PROGRESS

    /** 这一类组件在当前区域下能选的数据类型。 */
    fun allowedTypes(): List<DataType> = allowedTypes(kind, area)

    fun withArea(value: RegisterArea): WidgetForm {
        val next = copy(area = value)
        return if (type in next.allowedTypes()) next else next.copy(type = defaultType(kind, value))
    }

    fun withType(value: DataType): WidgetForm {
        if (value !in allowedTypes()) return this
        // 字节 / 字序只对 32 / 64 位有意义：换成别的类型就回到默认，避免留着看不见的设置
        return copy(type = value, order = if ((value.registers ?: 1) >= 2) order else ByteWordOrder.ABCD)
    }

    fun build(widgetId: String, pointId: String): FormResult {
        val errors = LinkedHashMap<FormField, FormError>()
        val name = name.trim().take(MAX_NAME)
        if (name.isEmpty()) errors[FormField.NAME] = FormError.REQUIRED

        fun int(text: String, field: FormField, range: IntRange): Int? {
            val t = text.trim()
            if (t.isEmpty()) { errors[field] = FormError.REQUIRED; return null }
            val v = t.toIntOrNull()
            if (v == null) { errors[field] = if (t.toDoubleOrNull() != null) FormError.NOT_INTEGER else FormError.NOT_A_NUMBER; return null }
            if (v !in range) { errors[field] = FormError.OUT_OF_RANGE; return null }
            return v
        }

        fun number(text: String, field: FormField, required: Boolean = true): Double? {
            val t = text.trim()
            if (t.isEmpty()) { if (required) errors[field] = FormError.REQUIRED; return null }
            val v = t.toDoubleOrNull()
            if (v == null || !v.isFinite()) { errors[field] = FormError.NOT_A_NUMBER; return null }
            return v
        }

        val unit = int(unitId, FormField.UNIT, 1..247)
        val addr = int(address, FormField.ADDRESS, 0..65535)
        val bitIndex = if (type == DataType.BIT) int(bit, FormField.BIT, 0..15) else 0
        val ascii = if (type == DataType.ASCII) int(asciiRegisters, FormField.ASCII, 1..125) else 1
        val poll = int(pollMs, FormField.POLL, PointDefinition.MIN_POLL_MS..PointDefinition.MAX_POLL_MS)

        var conversion = Conversion()
        if (usesConversion) {
            val s = number(scale, FormField.SCALE)
            val o = number(offset, FormField.OFFSET)
            if (s == 0.0) errors[FormField.SCALE] = FormError.ZERO_SCALE
            val d = decimals.trim().let { t ->
                if (t.isEmpty()) null else int(t, FormField.DECIMALS, 0..10)
            }
            if (s != null && o != null) conversion = Conversion(s, o, d)
        }

        var min: Double? = null
        var max: Double? = null
        if (usesRange) {
            min = number(rangeMin, FormField.RANGE_MIN, required = false)
            max = number(rangeMax, FormField.RANGE_MAX, required = false)
            if (min != null && max != null && !(min < max)) errors[FormField.RANGE_MAX] = FormError.RANGE_ORDER
        }

        val bands = ArrayList<ThresholdBand>()
        val badThresholds = LinkedHashSet<Int>()
        if (usesThresholds) {
            thresholds.forEachIndexed { i, row ->
                val v = row.value.trim().toDoubleOrNull()
                if (v == null || !v.isFinite()) {
                    badThresholds += i
                } else {
                    bands += if (row.op == ThresholdOp.GE) ThresholdBand(row.level, min = v) else ThresholdBand(row.level, max = v)
                }
            }
            if (badThresholds.isNotEmpty()) errors[FormField.THRESHOLD] = FormError.NOT_A_NUMBER
        }

        val mappings = ArrayList<StateMapping>()
        val badStates = LinkedHashSet<Int>()
        if (kind == WidgetKinds.STATUS_TEXT) {
            val seen = HashSet<Long>()
            states.forEachIndexed { i, row ->
                val v = row.value.trim().toLongOrNull()
                val label = row.label.trim()
                if (v == null || label.isEmpty() || !seen.add(v)) badStates += i else mappings += StateMapping(v, label, row.tone)
            }
            if (states.isEmpty()) errors[FormField.STATE] = FormError.REQUIRED
            else if (badStates.isNotEmpty()) errors[FormField.STATE] = if (states.map { it.value.trim() }.distinct().size < states.size) FormError.DUPLICATE else FormError.REQUIRED
            if (mappings.size > MAX_STATES) errors[FormField.STATE] = FormError.TOO_MANY
        }

        if (errors.isNotEmpty() || unit == null || addr == null || bitIndex == null || ascii == null || poll == null) {
            return FormResult(null, errors, badThresholds, badStates)
        }
        val point = PointDefinition(
            id = pointId, name = name, unitId = unit, area = area, address = addr, type = type, order = order,
            bit = bitIndex, asciiRegisters = ascii, conversion = conversion,
            unitLabel = if (usesUnitLabel) unitLabel.trim().take(MAX_UNIT_LABEL) else "",
            rangeMin = min, rangeMax = max, thresholds = bands, pollMs = poll,
        )
        for (issue in point.validate()) {
            when (issue) {
                PointIssue.SPAN_OVERFLOW -> errors[FormField.ADDRESS] = FormError.SPAN_OVERFLOW
                PointIssue.TOO_MANY_REGISTERS -> errors[FormField.ASCII] = FormError.TOO_MANY
                else -> Unit
            }
        }
        if (errors.isNotEmpty()) return FormResult(null, errors)
        val widget = Widget(
            id = widgetId, kind = kind, title = "", point = point,
            spanColumns = spanColumns.coerceIn(1, 2), spanRows = spanRows.coerceIn(1, 2),
            onLabel = if (kind == WidgetKinds.LAMP) onLabel.trim() else "",
            offLabel = if (kind == WidgetKinds.LAMP) offLabel.trim() else "",
            states = mappings,
        )
        return FormResult(widget, emptyMap())
    }

    companion object {
        const val MAX_NAME = 32
        const val MAX_UNIT_LABEL = 8
        const val MAX_STATES = 32

        private val numeric = listOf(
            DataType.U16, DataType.S16, DataType.U32, DataType.S32, DataType.F32, DataType.U64, DataType.S64, DataType.F64,
            DataType.BCD16, DataType.BCD32,
        )

        fun isBitArea(area: RegisterArea) = area == RegisterArea.COIL || area == RegisterArea.DISCRETE_INPUT

        fun allowedTypes(kind: String, area: RegisterArea): List<DataType> = when {
            isBitArea(area) -> listOf(DataType.BOOL)
            kind == WidgetKinds.LAMP -> listOf(DataType.BIT, DataType.U16, DataType.S16)
            kind == WidgetKinds.STATUS_TEXT -> listOf(DataType.U16, DataType.S16, DataType.U32, DataType.S32, DataType.BCD16, DataType.BCD32)
            kind == WidgetKinds.PROGRESS -> numeric
            else -> numeric + listOf(DataType.BIT, DataType.ASCII)
        }

        fun defaultType(kind: String, area: RegisterArea): DataType =
            if (isBitArea(area)) DataType.BOOL else if (kind == WidgetKinds.LAMP) DataType.BIT else DataType.U16

        /** 选了类型后的初始表单：灯 / 状态文本带上合理的默认。 */
        fun forKind(kind: String): WidgetForm = when (kind) {
            WidgetKinds.LAMP -> WidgetForm(kind = kind, type = DataType.BIT, onLabel = "", offLabel = "")
            WidgetKinds.PROGRESS -> WidgetForm(kind = kind, rangeMin = "0", rangeMax = "100", unitLabel = "%")
            else -> WidgetForm(kind = kind)
        }

        fun fromWidget(widget: Widget): WidgetForm? {
            val p = widget.point ?: return null
            if (!widget.isUsable) return null
            fun d(v: Double?) = v?.let { ValueCodec.format(it, null) }.orEmpty()
            return WidgetForm(
                kind = widget.kind, name = widget.title.ifBlank { p.name }, unitId = p.unitId.toString(), area = p.area,
                address = p.address.toString(), type = p.type, order = p.order, bit = p.bit.toString(),
                asciiRegisters = p.asciiRegisters.toString(), scale = d(p.conversion.scale), offset = d(p.conversion.offset),
                decimals = p.conversion.decimals?.toString().orEmpty(), unitLabel = p.unitLabel,
                rangeMin = d(p.rangeMin), rangeMax = d(p.rangeMax),
                thresholds = p.thresholds.mapNotNull { b ->
                    when {
                        b.min != null && b.max == null -> ThresholdRow(ThresholdOp.GE, d(b.min), b.level)
                        b.max != null && b.min == null -> ThresholdRow(ThresholdOp.LT, d(b.max), b.level)
                        else -> null
                    }
                },
                pollMs = p.pollMs.toString(), onLabel = widget.onLabel, offLabel = widget.offLabel,
                states = widget.states.map { StateRow(it.value.toString(), it.label, it.tone) },
                spanColumns = widget.spanColumns, spanRows = widget.spanRows,
            )
        }
    }
}
