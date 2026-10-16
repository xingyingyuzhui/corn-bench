package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea

enum class SerialPreset(
    val baudRate: Int,
    val dataBits: Int,
    val parity: Parity,
    val stopBits: Int,
) {
    N9600(9600, 8, Parity.NONE, 1),
    N19200(19200, 8, Parity.NONE, 1),
    N115200(115200, 8, Parity.NONE, 1),
}

fun framingLabel(dataBits: Int, parity: Parity, stopBits: Int): String {
    val mark = when (parity) {
        Parity.NONE -> "N"
        Parity.ODD -> "O"
        Parity.EVEN -> "E"
    }
    return "$dataBits$mark$stopBits"
}

fun wireLabel(baudRate: Int, dataBits: Int, parity: Parity, stopBits: Int): String =
    "$baudRate ${framingLabel(dataBits, parity, stopBits)}"

fun SerialSettings.wireLabel(): String = wireLabel(baudRate, dataBits, parity, stopBits)

fun matchingPreset(baudRate: Int, dataBits: Int, parity: Parity, stopBits: Int): SerialPreset? =
    SerialPreset.entries.firstOrNull {
        it.baudRate == baudRate && it.dataBits == dataBits && it.parity == parity && it.stopBits == stopBits
    }

fun SerialPreset.wireLabel(): String = wireLabel(baudRate, dataBits, parity, stopBits)

/** 和 strings.xml 的 connect_action 同一句。界面用资源，测试用这个纯函数。 */
fun connectAction(roleName: String, wire: String): String = "连接为$roleName · $wire"

enum class InputError { TIMEOUT_RANGE, GAP_POSITIVE }

data class TimeoutGapValidation(
    val timeoutError: InputError?,
    val gapError: InputError?,
) {
    val ok: Boolean get() = timeoutError == null && gapError == null
}

fun validateTimeoutGap(timeoutText: String, gapText: String): TimeoutGapValidation {
    val timeout = timeoutText.trim().toLongOrNull()
    val gap = gapText.trim().toLongOrNull()
    val timeoutError = if (timeout == null || timeout !in 1L..120_000L) InputError.TIMEOUT_RANGE else null
    val gapError = if (gap == null || gap < 1L) InputError.GAP_POSITIVE else null
    return TimeoutGapValidation(timeoutError, gapError)
}

/** 地址 0 的保持寄存器显示为 40001。线圈显示为 00001。 */
fun modiconLabel(area: RegisterArea, address: Int): String = when (area) {
    RegisterArea.COIL -> "%05d".format(address + 1)
    RegisterArea.DISCRETE_INPUT -> (10_001 + address).toString()
    RegisterArea.INPUT_REGISTER -> (30_001 + address).toString()
    RegisterArea.HOLDING_REGISTER -> (40_001 + address).toString()
}
