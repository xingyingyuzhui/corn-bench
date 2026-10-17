package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea

/**
 * 可写组件（开关 / 按钮）的规则，纯 Kotlin：哪些点位能做开关 / 按钮、按钮的预设值怎么编码成寄存器字。
 * 真正发请求的计划（FC05 / FC06 / FC16 + 整帧）仍由工作台的 `planWrite` 生成、由 `ConfirmSheet` 确认，这里不绕过。
 */
object WidgetWrites {
    /** 按钮能写的寄存器类型：32 位以内的整数和 F32（64 位整数经 Double 存预设值会丢精度，位 / ASCII 不是“一个值”）。 */
    val buttonTypes = listOf(DataType.U16, DataType.S16, DataType.U32, DataType.S32, DataType.F32)

    /** 组件配置是否自洽；null = 没问题。文件里读到的开关 / 按钮不满足时按“读不懂”原样保留。 */
    fun problem(widget: Widget): String? {
        val point = widget.point ?: return null
        return when (widget.kind) {
            WidgetKinds.SWITCH -> switchProblem(point)
            WidgetKinds.BUTTON -> buttonProblem(point, widget.writeValue)
            else -> null
        }
    }

    /** 开关要能读到当前状态（Unit 1..247），并且只能是线圈或保持寄存器里的一位。 */
    fun switchProblem(point: PointDefinition): String? = when {
        point.unitId !in 1..247 -> "开关的 Unit 必须是 1–247（广播读不到状态）"
        point.area == RegisterArea.COIL -> null
        point.area == RegisterArea.HOLDING_REGISTER && point.type == DataType.BIT -> null
        else -> "开关只能是线圈或保持寄存器的某一位"
    }

    fun buttonProblem(point: PointDefinition, value: Double?): String? {
        if (point.unitId !in 0..247) return "按钮的 Unit 必须是 0–247"
        if (value == null || !value.isFinite()) return "按钮缺少预设值"
        return when (point.area) {
            RegisterArea.COIL -> if (value == 0.0 || value == 1.0) null else "线圈按钮的预设值只能是 0 或 1"
            RegisterArea.HOLDING_REGISTER -> when {
                point.type !in buttonTypes -> "按钮不支持这种数据类型"
                buttonWords(point, value) == null -> "预设值超出该类型的范围"
                else -> null
            }
            else -> "按钮只能写线圈或保持寄存器"
        }
    }

    /** 按钮预设值 → 要写的寄存器字（线路顺序）；不是整数（整数类型）/ 超范围返回 null。 */
    fun buttonWords(point: PointDefinition, value: Double): List<Int>? {
        if (point.area != RegisterArea.HOLDING_REGISTER || point.type !in buttonTypes) return null
        val raw: RawValue = if (point.type.isFloat) {
            RawValue.Real(value)
        } else {
            if (!value.isFinite() || value != Math.rint(value)) return null
            RawValue.Integer(value.toLong(), unsigned = false)
        }
        val encoded = ValueCodec.encode(point.type, raw, point.order)
        return (encoded as? EncodeResult.Words)?.words?.toList()
    }
}
