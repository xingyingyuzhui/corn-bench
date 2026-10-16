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

    fun refreshDevices() {}
}
