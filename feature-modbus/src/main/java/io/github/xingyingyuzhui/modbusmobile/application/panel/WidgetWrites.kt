package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea

/**
 * 可写组件（开关 / 按钮）的规则，纯 Kotlin：哪些点位能做开关 / 按钮、按钮的预设值怎么编码成寄存器字。
 * 真正发请求的计划（FC05 / FC06 / FC16 + 整帧）仍由工作台的 `planWrite` 生成、由 `ConfirmSheet` 确认，这里不绕过。
 */
object WidgetWrites {
    /** 按钮能写的寄存器类型：32 位以内的整数和 F32（64 位整数经 Double 存预设值会丢精度，位 / ASCII 不是“一个值”）。 */
    val buttonTypes = listOf(DataType.U16, DataType.S16, DataType.U32, DataType.S32, DataType.F32)

    /** 寄存器表一行占用的类型（寄存器区域）：16 位 / 32 位整数和 F32，行宽固定，方便按行地址读写。 */
    val tableTypes = listOf(DataType.U16, DataType.S16, DataType.U32, DataType.S32, DataType.F32)
    const val MAX_TABLE_ROWS = 16

    /** 组件配置是否自洽；null = 没问题。文件里读到的开关 / 按钮不满足时按“读不懂”原样保留。 */
    fun problem(widget: Widget): String? {
        val point = widget.point ?: return null
        return when (widget.kind) {
            WidgetKinds.SWITCH -> switchProblem(point)
            WidgetKinds.BUTTON -> buttonProblem(point, widget.writeValue)
            WidgetKinds.REGISTER_TABLE -> tableProblem(point, widget.tableRows, widget.tableWritable)
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

    /** 寄存器表：从站 1..247（要读）；行数 1..16；类型按区域；整张表不能越过 65535；只有线圈 / 保持寄存器能开“允许修改”。 */
    fun tableProblem(point: PointDefinition, rows: Int, writable: Boolean): String? {
        if (point.unitId !in 1..247) return "寄存器表的 Unit 必须是 1–247"
        if (rows !in 1..MAX_TABLE_ROWS) return "寄存器表的行数必须是 1–$MAX_TABLE_ROWS"
        val typeOk = if (point.isBitArea) point.type == DataType.BOOL else point.type in tableTypes
        if (!typeOk) return "寄存器表不支持这种数据类型"
        if (point.address + rows * point.quantity > 65536) return "寄存器表超出地址范围"
        if (writable && !point.isWritable) return "输入类区域只读，不能允许修改"
        return null
    }

    /**
     * 寄存器表一行：工程值 → 要写的寄存器字（线路顺序）。按行点位的 scale / offset 反算，整数类型四舍五入，
     * 超出类型范围 / 非有限数 / 缩放为 0 返回错误；线圈行不走这里（直接 ON / OFF）。
     */
    fun rowWords(rowPoint: PointDefinition, engineering: Double): Result<List<Int>> {
        if (rowPoint.area != RegisterArea.HOLDING_REGISTER || rowPoint.type !in tableTypes) {
            return Result.failure(EncodeException(EncodeError.NOT_ENCODABLE))
        }
        val raw = ValueCodec.fromEngineering(rowPoint.type, engineering, rowPoint.conversion).getOrElse { return Result.failure(it) }
        return when (val encoded = ValueCodec.encode(rowPoint.type, raw, rowPoint.order)) {
            is EncodeResult.Words -> Result.success(encoded.words.toList())
            is EncodeResult.Error -> Result.failure(EncodeException(encoded.reason))
        }
    }

    /** 读到的原始值 → 线路上的寄存器字（“HEX 列”和确认弹窗里的“当前值”用）；不能编码返回 null。 */
    fun wordsOf(rowPoint: PointDefinition, raw: RawValue?): List<Int>? {
        raw ?: return null
        val r = if (rowPoint.type.isFloat && raw is RawValue.Integer) RawValue.Real(raw.toDouble()) else raw
        return (ValueCodec.encode(rowPoint.type, r, rowPoint.order) as? EncodeResult.Words)?.words?.toList()
    }
}
