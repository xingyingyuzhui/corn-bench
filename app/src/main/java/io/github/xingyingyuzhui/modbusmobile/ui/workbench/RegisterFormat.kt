package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import io.github.xingyingyuzhui.modbusmobile.application.NumberFormat
import java.util.Locale

/** 一个寄存器的 HEX 列文字。 */
fun hexWord(value: Int, prefix: Boolean): String =
    (if (prefix) "0x" else "") + "%04X".format(Locale.ROOT, value and 0xFFFF)

/**
 * 把一次读取到的寄存器按 [format] 变成“值”列的文字，长度与 [values] 相同。
 *
 * - U16：无符号；S16：有符号；HEX：同 HEX 列。
 * - F32：相邻两个寄存器按 ABCD（高字在前）合成一个 IEEE754 单精度浮点，写在第一个寄存器那一行，
 *   第二行显示“—”；落单的最后一个寄存器也显示“—”。字序目前没有设置项，固定 ABCD。
 */
fun formatRegisterValues(values: List<Int>, format: NumberFormat, hexPrefix: Boolean): List<String> = when (format) {
    NumberFormat.U16 -> values.map { (it and 0xFFFF).toString() }
    NumberFormat.S16 -> values.map { (it and 0xFFFF).toShort().toString() }
    NumberFormat.HEX -> values.map { hexWord(it, hexPrefix) }
    NumberFormat.F32 -> values.mapIndexed { index, _ ->
        if (index % 2 == 0 && index + 1 < values.size) {
            formatFloat(Float.fromBits(((values[index] and 0xFFFF) shl 16) or (values[index + 1] and 0xFFFF)))
        } else {
            "—"
        }
    }
}

internal fun formatFloat(value: Float): String = when {
    value.isNaN() -> "NaN"
    value.isInfinite() -> if (value > 0) "∞" else "-∞"
    else -> java.math.BigDecimal(value.toDouble())
        .round(java.math.MathContext(7))
        .stripTrailingZeros()
        .toPlainString()
}

/** 轮询周期的文字：1000 → “1”，500 → “0.5”。 */
fun secondsLabel(ms: Long): String =
    if (ms % 1000L == 0L) (ms / 1000L).toString() else String.format(Locale.ROOT, "%.1f", ms / 1000.0)

/** 功能码文字，例如 3 → “FC03”。 */
fun fcLabel(code: Int): String = "FC%02X".format(Locale.ROOT, code)
