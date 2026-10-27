package io.github.xingyingyuzhui.modbusmobile.ui.connection

import io.github.xingyingyuzhui.modbusmobile.application.Parity
import io.github.xingyingyuzhui.modbusmobile.application.wireLabel

/**
 * 连接页的串口预设，稿面是 9600 8N1 / 19200 8E1 / 115200 8N1（b1-1#4）。
 *
 * 放在 ui/ 里而不是改 `application.SerialPreset`：本轮约定只改 ui/。
 * 旧的 `application.SerialPreset.N19200` 是 8N1，界面不再使用它（留给后续在 application 层清理）。
 */
enum class SerialPresetUi(
    val baudRate: Int,
    val dataBits: Int,
    val parity: Parity,
    val stopBits: Int,
) {
    N9600(9_600, 8, Parity.NONE, 1),
    E19200(19_200, 8, Parity.EVEN, 1),
    N115200(115_200, 8, Parity.NONE, 1),
    ;

    val label: String get() = wireLabel(baudRate, dataBits, parity, stopBits)

    companion object {
        fun matching(baudRate: Int, dataBits: Int, parity: Parity, stopBits: Int): SerialPresetUi? =
            entries.firstOrNull {
                it.baudRate == baudRate && it.dataBits == dataBits && it.parity == parity && it.stopBits == stopBits
            }
    }
}
