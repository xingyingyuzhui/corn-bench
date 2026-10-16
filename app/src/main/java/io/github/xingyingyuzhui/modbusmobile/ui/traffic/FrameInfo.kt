package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.core.rtu.Crc16Modbus

/**
 * 日志里一帧的只读解码，纯 ui 层的过渡实现。
 *
 * 只读帧字节，不猜测：方向未知（回显 / 方向不明 / 丢弃）的帧只解 Unit、功能码、CRC，
 * 不解地址和数量；长度对不上的帧也不解字段，只标出长度。
 * 目标是改调 core 的 `FrameDescriber`（ui-diff.md §12），届时本文件只留展示格式化。
 */
enum class FrameDirection { REQUEST, RESPONSE, UNKNOWN }

fun TrafficKind.frameDirection(): FrameDirection = when (this) {
    TrafficKind.TX_REQUEST, TrafficKind.RX_LOCAL_REQUEST, TrafficKind.RX_FOREIGN_REQUEST -> FrameDirection.REQUEST
    TrafficKind.TX_RESPONSE, TrafficKind.RX_RESPONSE, TrafficKind.RX_FOREIGN_RESPONSE -> FrameDirection.RESPONSE
    TrafficKind.ECHO, TrafficKind.AMBIGUOUS, TrafficKind.DROPPED -> FrameDirection.UNKNOWN
}

/** 帧里一段有名字的字节：对应详情卡的一行。 */
enum class FieldKind { UNIT, FUNCTION, ADDRESS, QUANTITY, BYTE_COUNT, DATA, VALUE, EXCEPTION, CRC }

class FrameField(
    val kind: FieldKind,
    /** 这一段的字节，十六进制，空格分隔（数据段过长时截断，末尾加 …）。 */
    val hex: String,
    /** 已解出的数值；功能码 / 异常码 / 地址 / 数据个数由界面再格式化。 */
    val number: Int? = null,
    val number2: Int? = null,
)

class FrameInfo(
    val size: Int,
    val direction: FrameDirection,
    val unit: Int?,
    /** 去掉 0x80 的功能码；帧不足两字节时为 null。 */
    val function: Int?,
    val isException: Boolean,
    val exceptionCode: Int?,
    /** null = 不足 4 字节，无法校验。 */
    val crcOk: Boolean?,
    val crcValue: Int?,
    /** 按帧内容算出的 CRC；只在校验失败时用来告诉用户“应为”多少。 */
    val crcExpected: Int?,
    val address: Int?,
    /** 请求的数量 / 写响应的数量。 */
    val quantity: Int?,
    /** 读响应：寄存器个数（寄存器读）或字节数（位读）。 */
    val dataCount: Int?,
    /** 单写：写入值（线圈为 0/1）。 */
    val value: Int?,
    val fields: List<FrameField>,
) {
    val isBadCrc: Boolean get() = crcOk == false

    /** 地址所在区域，按功能码推出；只用来给“参考号”小字。 */
    val area: RegisterArea?
        get() = when (function) {
            0x01, 0x05, 0x0F -> RegisterArea.COIL
            0x02 -> RegisterArea.DISCRETE_INPUT
            0x03, 0x06, 0x10 -> RegisterArea.HOLDING_REGISTER
            0x04 -> RegisterArea.INPUT_REGISTER
            else -> null
        }

    val isRegisterRead: Boolean get() = function == 0x03 || function == 0x04
    val isBitRead: Boolean get() = function == 0x01 || function == 0x02
}

private const val MAX_DATA_BYTES_SHOWN = 2

fun describeFrame(bytes: ByteArray, kind: TrafficKind): FrameInfo {
    val direction = kind.frameDirection()
    val size = bytes.size
    fun u8(i: Int) = bytes[i].toInt() and 0xFF
    fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
    fun hex(from: Int, to: Int) = (from until to).joinToString(" ") { "%02X".format(u8(it)) }

    val crcOk = if (size >= 4) Crc16Modbus.matches(bytes) else null
    val crcValue = if (size >= 4) u8(size - 2) or (u8(size - 1) shl 8) else null
    val unit = if (size >= 1) u8(0) else null
    val raw = if (size >= 2) u8(1) else null
    val exception = raw != null && (raw and 0x80) != 0 && size == 5
    val function = raw?.and(0x7F)

    val fields = ArrayList<FrameField>()
    if (unit != null) fields += FrameField(FieldKind.UNIT, hex(0, 1), unit)
    if (raw != null) fields += FrameField(FieldKind.FUNCTION, hex(1, 2), function, if (exception) 1 else 0)

    var address: Int? = null
    var quantity: Int? = null
    var dataCount: Int? = null
    var value: Int? = null
    var exceptionCode: Int? = null

    fun dataField(from: Int, to: Int, count: Int) {
        val shown = minOf(to, from + MAX_DATA_BYTES_SHOWN)
        val text = hex(from, shown) + if (shown < to) " …" else ""
        fields += FrameField(FieldKind.DATA, text, count)
    }

    if (exception) {
        exceptionCode = u8(2)
        fields += FrameField(FieldKind.EXCEPTION, hex(2, 3), exceptionCode)
    } else if (function != null && direction != FrameDirection.UNKNOWN && crcOk != false) {
        when (direction) {
            FrameDirection.REQUEST -> when (function) {
                0x01, 0x02, 0x03, 0x04 -> if (size == 8) {
                    address = u16(2); quantity = u16(4)
                    fields += FrameField(FieldKind.ADDRESS, hex(2, 4), address)
                    fields += FrameField(FieldKind.QUANTITY, hex(4, 6), quantity)
                }
                0x05, 0x06 -> if (size == 8) {
                    address = u16(2)
                    val raw16 = u16(4)
                    value = if (function == 0x05) (if (raw16 == 0xFF00) 1 else 0) else raw16
                    fields += FrameField(FieldKind.ADDRESS, hex(2, 4), address)
                    fields += FrameField(FieldKind.VALUE, hex(4, 6), value, function)
                }
                0x0F, 0x10 -> if (size >= 9 && size == 9 + u8(6)) {
                    address = u16(2); quantity = u16(4)
                    fields += FrameField(FieldKind.ADDRESS, hex(2, 4), address)
                    fields += FrameField(FieldKind.QUANTITY, hex(4, 6), quantity)
                    fields += FrameField(FieldKind.BYTE_COUNT, hex(6, 7), u8(6))
                    dataField(7, size - 2, quantity)
                }
            }
            FrameDirection.RESPONSE -> when (function) {
                0x01, 0x02, 0x03, 0x04 -> if (size >= 5 && size == 5 + u8(2)) {
                    val byteCount = u8(2)
                    dataCount = if (function >= 0x03) byteCount / 2 else byteCount
                    fields += FrameField(FieldKind.BYTE_COUNT, hex(2, 3), byteCount)
                    dataField(3, size - 2, dataCount)
                }
                0x05, 0x06 -> if (size == 8) {
                    address = u16(2)
                    val raw16 = u16(4)
                    value = if (function == 0x05) (if (raw16 == 0xFF00) 1 else 0) else raw16
                    fields += FrameField(FieldKind.ADDRESS, hex(2, 4), address)
                    fields += FrameField(FieldKind.VALUE, hex(4, 6), value, function)
                }
                0x0F, 0x10 -> if (size == 8) {
                    address = u16(2); quantity = u16(4)
                    fields += FrameField(FieldKind.ADDRESS, hex(2, 4), address)
                    fields += FrameField(FieldKind.QUANTITY, hex(4, 6), quantity)
                }
            }
            FrameDirection.UNKNOWN -> Unit
        }
    }
    if (crcValue != null) fields += FrameField(FieldKind.CRC, hex(size - 2, size), crcValue)

    return FrameInfo(
        size = size,
        direction = direction,
        unit = unit,
        function = function,
        isException = exception,
        exceptionCode = exceptionCode,
        crcOk = crcOk,
        crcValue = crcValue,
        crcExpected = if (size >= 4) Crc16Modbus.compute(bytes, 0, size - 2) else null,
        address = address,
        quantity = quantity,
        dataCount = dataCount,
        value = value,
        fields = fields,
    )
}

/** 日志过滤：全部 / 本机 / 外站 / 回显 / 异常 / 不明。 */
enum class LogFilter { ALL, LOCAL, FOREIGN, ECHO, ERROR, UNKNOWN }

fun LogFilter.accepts(kind: TrafficKind, info: FrameInfo): Boolean = when (this) {
    LogFilter.ALL -> true
    LogFilter.LOCAL -> when (kind) {
        TrafficKind.TX_REQUEST, TrafficKind.TX_RESPONSE, TrafficKind.RX_LOCAL_REQUEST, TrafficKind.RX_RESPONSE -> true
        else -> false
    }
    LogFilter.FOREIGN -> kind == TrafficKind.RX_FOREIGN_REQUEST || kind == TrafficKind.RX_FOREIGN_RESPONSE
    LogFilter.ECHO -> kind == TrafficKind.ECHO
    LogFilter.ERROR -> info.isException || info.isBadCrc
    LogFilter.UNKNOWN -> kind == TrafficKind.AMBIGUOUS || kind == TrafficKind.DROPPED
}

/** 相对会话起点的时间：mm:ss.mmm，超过一小时前面加小时。 */
fun formatOffset(ms: Long): String {
    val total = ms.coerceAtLeast(0)
    val millis = (total % 1000).toInt()
    val seconds = (total / 1000 % 60).toInt()
    val minutes = (total / 60_000 % 60).toInt()
    val hours = (total / 3_600_000).toInt()
    return if (hours > 0) "%d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    else "%02d:%02d.%03d".format(minutes, seconds, millis)
}
