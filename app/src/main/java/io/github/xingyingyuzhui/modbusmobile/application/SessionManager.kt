package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface SessionManager {
    val state: StateFlow<SessionState>
    val traffic: SharedFlow<TrafficEvent>
    val slaveBank: StateFlow<VirtualRegisterBank?>
    val usbDevices: StateFlow<List<UsbDeviceSummary>>
    val echoDetected: StateFlow<Boolean?>
    val serial: StateFlow<SerialSettings?>

    suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role)
    suspend fun disconnect()
    suspend fun execute(request: ModbusRequest): ModbusResult

    /** 本次会话的收发计数，连接时从 0 开始，断开后归零。见 [SessionCounters]。 */
    val counters: StateFlow<SessionCounters> get() = NoCounters

    /**
     * 这条请求将要发出的整帧字节（含 CRC），给写入确认弹窗做预览。
     * 和主站引擎发送时用同一个编码器，所以预览的就是实际会发出的字节；界面层不直接碰 core 的 RTU 编码。
     */
    fun previewFrame(request: ModbusRequest): ByteArray = encodeRequestFrame(request)

    fun refreshDevices() {}
}
