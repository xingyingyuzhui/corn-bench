package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.UiText
import io.github.xingyingyuzhui.modbusmobile.ui.components.RegisterRow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkbenchViewModel(private val session: SessionManager) : ViewModel() {
    val sessionState = session.state
    var area by mutableStateOf(RegisterArea.HOLDING_REGISTER)
    var unit by mutableStateOf("1")
    var address by mutableStateOf("0")
    var quantity by mutableStateOf("10")
    var writeValue by mutableStateOf("0")
    var table by mutableStateOf<List<RegisterRow>>(emptyList())
    var message by mutableStateOf<UiText?>(null)
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
            show(session.execute(request), start)
        }
    }

    fun writeHolding() {
        val unitId = unit.toIntOrNull()
        val start = address.toIntOrNull()
        val value = writeValue.toIntOrNull()
        if (unitId == null || start == null || value == null) {
            table = emptyList()
            message = UiText.Of(R.string.need_integer)
            return
        }
        val request = try {
            ModbusRequest.WriteSingleRegister(unitId, start, value)
        } catch (error: IllegalArgumentException) {
            table = emptyList()
            message = UiText.Plain(error.message ?: "")
            return
        }
        viewModelScope.launch {
            show(session.execute(request), start)
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
            table = emptyList()
            message = UiText.Of(R.string.need_integer)
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
            table = emptyList()
            message = UiText.Plain(error.message ?: "")
            null
        }
    }

    private fun show(result: ModbusResult, start: Int) {
        when (result) {
            is ModbusResult.Success -> when (val response = result.response) {
                is ModbusResponse.ReadRegisters -> {
                    table = response.values.mapIndexed { index, value ->
                        RegisterRow(start + index, value.toString())
                    }
                    message = if (response.values.isEmpty()) UiText.Of(R.string.result_empty) else null
                }
                is ModbusResponse.ReadBits -> {
                    table = emptyList()
                    message = if (response.values.isEmpty()) {
                        UiText.Of(R.string.result_empty)
                    } else {
                        UiText.Bits(start, response.values)
                    }
                }
                is ModbusResponse.WriteSingleRegister -> {
                    table = emptyList()
                    message = UiText.Of(R.string.result_wrote, listOf(response.address, response.value))
                }
                else -> {
                    table = emptyList()
                    message = UiText.Of(R.string.result_done)
                }
            }
            is ModbusResult.Exception -> {
                table = emptyList()
                message = exceptionText(result.code.code)
            }
            ModbusResult.Timeout -> {
                table = emptyList()
                message = UiText.Of(R.string.result_timeout)
            }
            ModbusResult.UnknownOutcome -> {
                table = emptyList()
                message = UiText.Of(R.string.result_unknown)
            }
            ModbusResult.Disconnected -> {
                table = emptyList()
                message = UiText.Of(R.string.result_disconnected)
            }
            is ModbusResult.InvalidResponse -> {
                table = emptyList()
                message = UiText.Plain(result.detail)
            }
        }
    }
}

private fun exceptionText(code: Int): UiText = when (code) {
    0x01 -> UiText.Of(R.string.ex_01)
    0x02 -> UiText.Of(R.string.ex_02)
    0x03 -> UiText.Of(R.string.ex_03)
    0x04 -> UiText.Of(R.string.ex_04)
    0x05 -> UiText.Of(R.string.ex_05)
    0x06 -> UiText.Of(R.string.ex_06)
    0x0A -> UiText.Of(R.string.ex_0a)
    0x0B -> UiText.Of(R.string.ex_0b)
    else -> UiText.Of(R.string.ex_unknown, listOf(code))
}

data class SlaveRows(
    val holding: List<CellDraft> = emptyList(),
    val coils: List<CoilDraft> = emptyList(),
)

data class CellDraft(val address: Int, val draft: String, val editing: Boolean)

data class CoilDraft(val address: Int, val value: Boolean)
