package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.rtu.RtuCodec
import io.github.xingyingyuzhui.modbusmobile.ui.UiText

enum class WriteFormat { DEC, HEX }

/**
 * 一次写入的完整计划：要发的请求、整帧字节和载荷区间。
 *
 * 整帧用 core 的 [RtuCodec] 编码，与主站引擎实际发出的字节一致，所以确认弹窗里看到的就是将要发的内容。
 * 这是 ui 层的过渡做法：ui-plan 把它定为 `SessionManager.previewFrame()`（application 层），本轮不改 application/core。
 */
class WritePlan(
    val request: ModbusRequest,
    val area: RegisterArea,
    val unit: Int,
    val address: Int,
    val words: List<Int>,
    val bits: List<Boolean>,
) {
    val frame: ByteArray = RtuCodec.encodeRequest(request)
    val functionCode: Int = request.function.code
    val isSingle: Boolean = words.size + bits.size == 1 &&
        (request is ModbusRequest.WriteSingleCoil || request is ModbusRequest.WriteSingleRegister)

    /** 载荷字节区间：单写是“值”两个字节，多写是 byteCount 之后到 CRC 之前。 */
    val payload: IntRange = if (isSingle) 4..5 else 7 until (frame.size - 2)
}

sealed interface PlanResult {
    data class Ok(val plan: WritePlan) : PlanResult
    data class Invalid(val message: UiText) : PlanResult
}

/** 把一个值格里的文字按 [format] 解析成 0..0xFFFF。十进制允许 −32768..65535（负数按补码）。 */
fun parseWord(text: String, format: WriteFormat): Int? {
    val trimmed = text.trim()
    return when (format) {
        WriteFormat.DEC -> trimmed.toIntOrNull()?.takeIf { it in -32768..65535 }?.and(0xFFFF)
        WriteFormat.HEX -> trimmed.removePrefix("0x").removePrefix("0X").toIntOrNull(16)?.takeIf { it in 0..0xFFFF }
    }
}

/**
 * 1 个值走 FC05 / FC06；多个值自动走 FC0F / FC10；[forceMultiple] 把单个值也按 FC0F / FC10 发。
 * 只支持线圈和保持寄存器。
 */
fun planWrite(
    area: RegisterArea,
    unitText: String,
    addressText: String,
    wordTexts: List<String>,
    coils: List<Boolean>,
    format: WriteFormat,
    forceMultiple: Boolean,
): PlanResult {
    val unit = unitText.trim().toIntOrNull()
    val address = addressText.trim().toIntOrNull()
    if (unit == null || address == null) return PlanResult.Invalid(UiText.Of(R.string.need_integer))
    return try {
        when (area) {
            RegisterArea.COIL -> {
                if (coils.isEmpty()) return PlanResult.Invalid(UiText.Of(R.string.error_write_empty))
                val request = if (coils.size == 1 && !forceMultiple) {
                    ModbusRequest.WriteSingleCoil(unit, address, coils[0])
                } else {
                    ModbusRequest.WriteMultipleCoils(unit, address, coils)
                }
                PlanResult.Ok(WritePlan(request, area, unit, address, emptyList(), coils))
            }
            RegisterArea.HOLDING_REGISTER -> {
                if (wordTexts.isEmpty()) return PlanResult.Invalid(UiText.Of(R.string.error_write_empty))
                val words = ArrayList<Int>(wordTexts.size)
                wordTexts.forEachIndexed { index, text ->
                    val word = parseWord(text, format)
                        ?: return PlanResult.Invalid(UiText.Of(R.string.error_write_value, listOf(index + 1)))
                    words += word
                }
                val request = if (words.size == 1 && !forceMultiple) {
                    ModbusRequest.WriteSingleRegister(unit, address, words[0])
                } else {
                    ModbusRequest.WriteMultipleRegisters(unit, address, words)
                }
                PlanResult.Ok(WritePlan(request, area, unit, address, words, emptyList()))
            }
            else -> PlanResult.Invalid(UiText.Of(R.string.error_write_area))
        }
    } catch (error: IllegalArgumentException) {
        PlanResult.Invalid(UiText.Plain(error.message ?: ""))
    }
}
