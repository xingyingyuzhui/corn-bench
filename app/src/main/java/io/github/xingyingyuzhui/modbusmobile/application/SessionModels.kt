package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.rtu.BusTopology
import io.github.xingyingyuzhui.modbusmobile.core.rtu.EchoMode

sealed interface ConnectionTarget {
    data class Usb(val deviceId: Int, val portIndex: Int) : ConnectionTarget
    data object Demo : ConnectionTarget
}

sealed interface SessionState {
    data object Idle : SessionState
    data object RequestingPermission : SessionState
    data object Opening : SessionState
    data class Running(val role: Role, val target: ConnectionTarget, val epoch: Long) : SessionState
    data object Stopping : SessionState
    data class Error(val reason: String) : SessionState
}

enum class Parity {
    NONE,
    ODD,
    EVEN,
}

data class SerialSettings(
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val parity: Parity = Parity.NONE,
    val stopBits: Int = 1,
    val responseTimeoutMs: Long = 1_000,
    val turnaroundMs: Long = 150,
    val interRequestDelayMs: Long = 20,
    val topology: BusTopology = BusTopology.POINT_TO_POINT,
    val echoMode: EchoMode = EchoMode.OFF,
) {
    /** 1 起始位 + 数据位 + 校验位(0 或 1) + 停止位。 */
    fun bitsPerChar(): Int = 1 + dataBits + (if (parity == Parity.NONE) 0 else 1) + stopBits
}

data class UsbDeviceSummary(
    val deviceId: Int,
    val portIndex: Int,
    val productName: String,
    val vendorId: Int,
    val productId: Int,
)
