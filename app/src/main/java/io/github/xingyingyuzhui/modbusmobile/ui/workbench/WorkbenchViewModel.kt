package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkbenchViewModel(private val session: SessionManager) : ViewModel() {
    val sessionState = session.state
    var area by mutableStateOf(RegisterArea.HOLDING_REGISTER)
    var unit by mutableStateOf("1")
    var address by mutableStateOf("0")
    var quantity by mutableStateOf("10")
    var writeValue by mutableStateOf("0")
    var rows by mutableStateOf<List<String>>(emptyList())
    var slaveRows by mutableStateOf(SlaveRows())

    init {
        viewModelScope.launch {
            session.slaveBank.collectLatest { bank ->
                if (bank == null) {
                    slaveRows = SlaveRows()
                    return@collectLatest
                }
                publish(bank)
                bank.changes.collect { publish(bank) }
            }
        }
    }

    fun read() {
        val start = address.toIntOrNull()
        val request = readRequest() ?: return
        if (start == null) return
        viewModelScope.launch {
            rows = listOf(describe(session.execute(request), start))
        }
    }

    fun writeHolding() {
        val unitId = unit.toIntOrNull()
        val start = address.toIntOrNull()
        val value = writeValue.toIntOrNull()
        if (unitId == null || start == null || value == null) {
            rows = listOf("请输入整数")
            return
        }
        val request = try {
            ModbusRequest.WriteSingleRegister(unitId, start, value)
        } catch (error: IllegalArgumentException) {
            rows = listOf(error.message ?: "参数不合法")
            return
        }
        viewModelScope.launch {
            rows = listOf(describe(session.execute(request), start))
        }
    }

    fun editHolding(address: Int, text: String) {
        slaveRows = slaveRows.copy(
            holding = slaveRows.holding.map { cell ->
                if (cell.address == address) cell.copy(draft = text) else cell
            },
        )
        val value = text.toIntOrNull() ?: return
        if (value !in 0..0xFFFF) return
        session.slaveBank.value?.putWord(1, RegisterArea.HOLDING_REGISTER, address, value)
    }

    fun editCoil(address: Int, value: Boolean) {
        session.slaveBank.value?.putBit(1, RegisterArea.COIL, address, value)
    }

    private fun publish(bank: VirtualRegisterBank) {
        slaveRows = SlaveRows(
            holding = bank.words(1, RegisterArea.HOLDING_REGISTER).map { CellDraft(it.address, it.value.toString(), false) },
            coils = bank.bits(1, RegisterArea.COIL).map { CoilDraft(it.address, it.value) },
        )
    }

    private fun readRequest(): ModbusRequest? {
        val unitId = unit.toIntOrNull()
        val start = address.toIntOrNull()
        val count = quantity.toIntOrNull()
        if (unitId == null || start == null || count == null) {
            rows = listOf("请输入整数")
            return null
        }
        return try {
            when (area) {
                RegisterArea.COIL -> ModbusRequest.ReadCoils(unitId, start, count)
                RegisterArea.DISCRETE_INPUT -> ModbusRequest.ReadDiscreteInputs(unitId, start, count)
                RegisterArea.HOLDING_REGISTER -> ModbusRequest.ReadHoldingRegisters(unitId, start, count)
                RegisterArea.INPUT_REGISTER -> ModbusRequest.ReadInputRegisters(unitId, start, count)
            }
        } catch (error: IllegalArgumentException) {
            rows = listOf(error.message ?: "参数不合法")
            null
        }
    }

    private fun describe(result: ModbusResult, start: Int): String = when (result) {
        is ModbusResult.Success -> when (val response = result.response) {
            is ModbusResponse.ReadRegisters -> response.values.mapIndexed { index, value ->
                "${start + index}: $value"
            }.joinToString("\n").ifEmpty { "空" }
            is ModbusResponse.ReadBits -> response.values.mapIndexed { index, value ->
                "${start + index}: ${if (value) "ON" else "OFF"}"
            }.joinToString("\n")
            is ModbusResponse.WriteSingleRegister -> "HR ${response.address} = ${response.value}"
            else -> "完成"
        }
        is ModbusResult.Exception -> "异常 ${result.code.wire()}"
        ModbusResult.Timeout -> "超时"
        ModbusResult.UnknownOutcome -> "结果未知，写入可能已经生效"
        ModbusResult.Disconnected -> "连接已断开"
        is ModbusResult.InvalidResponse -> result.detail
    }
}

data class SlaveRows(
    val holding: List<CellDraft> = emptyList(),
    val coils: List<CoilDraft> = emptyList(),
)

data class CellDraft(val address: Int, val draft: String, val editing: Boolean)

data class CoilDraft(val address: Int, val value: Boolean)

private fun ExceptionCode.wire(): String = when (code) {
    0x01 -> "01 非法功能"
    0x02 -> "02 非法地址"
    0x03 -> "03 非法数据"
    0x04 -> "04 从站设备故障"
    0x05 -> "05 确认"
    0x06 -> "06 从站设备忙"
    0x0A -> "0A 网关路径不可用"
    0x0B -> "0B 网关目标失败"
    else -> "%02X 未知异常".format(code)
}
