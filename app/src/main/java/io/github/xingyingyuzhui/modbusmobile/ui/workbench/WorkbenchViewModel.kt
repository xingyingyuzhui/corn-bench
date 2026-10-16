package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.NumberFormat
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.UiText
import io.github.xingyingyuzhui.modbusmobile.ui.components.FlashDurationMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class WorkbenchMode { READ, WRITE }

/** 一次成功读取的结果。位读取（线圈 / 离散）的 [values] 是 0 / 1。 */
data class ReadResult(
    val area: RegisterArea,
    val unit: Int,
    val start: Int,
    val values: List<Int>,
    val isBits: Boolean,
    val elapsedMs: Long,
    /** 与上一次同一段读取相比变化的地址 → 变化时刻（[SystemClock.elapsedRealtime]）。 */
    val changedAt: Map<Int, Long>,
)

sealed interface ReadState {
    data object None : ReadState
    data class Ok(val result: ReadResult) : ReadState

    /** [badge] 是结果头的红色/灰色小标，[message] 是详细说明。 */
    data class Failed(val badge: UiText, val message: UiText, val elapsedMs: Long) : ReadState
}

enum class WriteOutcome { OK, EXCEPTION, TIMEOUT, UNKNOWN, DISCONNECTED, INVALID }

data class RecentWrite(
    val area: RegisterArea,
    val unit: Int,
    val address: Int,
    val words: List<Int>,
    val bits: List<Boolean>,
    val functionCode: Int,
    val outcome: WriteOutcome,
    val exceptionCode: Int?,
    val atMillis: Long,
)

/** 等待用户在确认弹窗里点“发送写入”的写请求。[current] 是写入前已知的当前值（没读过为 null）。 */
data class PendingWrite(val plan: WritePlan, val current: List<Int?>)

private data class ValueKey(val unit: Int, val area: RegisterArea, val address: Int)

val PollIntervalsMs = listOf(500L, 1_000L, 2_000L, 5_000L)

private const val MaxRecentWrites = 5

class WorkbenchViewModel(
    private val session: SessionManager,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    val sessionState = session.state

    var mode by mutableStateOf(WorkbenchMode.READ)
        private set

    /** 读取用的区域。 */
    var area by mutableStateOf(RegisterArea.HOLDING_REGISTER)

    /** 写入用的区域，只有线圈和保持寄存器。 */
    var writeArea by mutableStateOf(RegisterArea.HOLDING_REGISTER)
        private set
    var unit by mutableStateOf("1")
    var address by mutableStateOf("0")
    var quantity by mutableStateOf("10")

    var writeFormat by mutableStateOf(WriteFormat.DEC)
        private set
    var writeWords by mutableStateOf(listOf("0"))
        private set
    var writeCoils by mutableStateOf(listOf(false))
        private set
    var forceMultiple by mutableStateOf(false)
        private set

    /** 用户在结果头里点过的格式；null 表示跟随设置里的“默认数值格式”。 */
    var formatOverride by mutableStateOf<NumberFormat?>(null)

    var readState by mutableStateOf<ReadState>(ReadState.None)
        private set

    /** 输入有误时显示在请求卡下面的一行红字。 */
    var formError by mutableStateOf<UiText?>(null)
        private set

    var polling by mutableStateOf(false)
        private set
    var pollIntervalMs by mutableLongStateOf(1_000L)
        private set

    var pending by mutableStateOf<PendingWrite?>(null)
        private set
    var recentWrites by mutableStateOf<List<RecentWrite>>(emptyList())
        private set
    private var writing by mutableStateOf(false)

    var slaveRows by mutableStateOf(SlaveRows())

    private val known = HashMap<ValueKey, Int>()
    private var pollJob: Job? = null

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

    // ---- 模式、区域、值格 ----

    fun selectMode(next: WorkbenchMode) {
        mode = next
        formError = null
    }

    fun selectWriteArea(next: RegisterArea) {
        if (next != RegisterArea.COIL && next != RegisterArea.HOLDING_REGISTER) return
        if (next == writeArea) return
        writeArea = next
        writeWords = listOf("0")
        writeCoils = listOf(false)
        forceMultiple = false
        formError = null
    }

    fun toggleWriteFormat() {
        val next = if (writeFormat == WriteFormat.DEC) WriteFormat.HEX else WriteFormat.DEC
        // 已经能解析的值跟着换算，免得用户看到数字“变了”。解析不了的原样保留。
        writeWords = writeWords.map { text ->
            val word = parseWord(text, writeFormat)
            when {
                word == null -> text
                next == WriteFormat.HEX -> "%X".format(word)
                else -> word.toString()
            }
        }
        writeFormat = next
    }

    fun setWordText(index: Int, text: String) {
        writeWords = writeWords.toMutableList().also { if (index in it.indices) it[index] = text }
    }

    fun toggleCoil(index: Int) {
        writeCoils = writeCoils.toMutableList().also { if (index in it.indices) it[index] = !it[index] }
    }

    fun addValue() {
        if (writeArea == RegisterArea.COIL) {
            if (writeCoils.size < MaxFormValues) writeCoils = writeCoils + false
        } else if (writeWords.size < MaxFormValues) {
            writeWords = writeWords + "0"
        }
    }

    fun removeValue() {
        if (writeArea == RegisterArea.COIL) {
            if (writeCoils.size > 1) writeCoils = writeCoils.dropLast(1)
        } else if (writeWords.size > 1) {
            writeWords = writeWords.dropLast(1)
        }
    }

    fun toggleForceMultiple() {
        forceMultiple = !forceMultiple
    }

    // ---- 读取与轮询 ----

    fun read() {
        val params = readParams() ?: return
        viewModelScope.launch { performRead(params) }
    }

    fun changePolling(enabled: Boolean) {
        pollJob?.cancel()
        pollJob = null
        polling = enabled
        if (!enabled) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                // 手动写入优先：写模式、有待确认的写入、写入在途时，轮询让路。
                if (mode == WorkbenchMode.WRITE || pending != null || writing) {
                    delay(100)
                    continue
                }
                val params = readParams()
                if (params == null) {
                    polling = false
                    return@launch
                }
                val stop = performRead(params)
                if (stop) {
                    polling = false
                    return@launch
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun setPollInterval(ms: Long) {
        pollIntervalMs = ms
    }

    fun clearResult() {
        readState = ReadState.None
    }

    private data class ReadParams(val request: ModbusRequest, val area: RegisterArea, val unit: Int, val start: Int)

    private fun readParams(): ReadParams? {
        val unitId = unit.trim().toIntOrNull()
        val start = address.trim().toIntOrNull()
        val count = quantity.trim().toIntOrNull()
        if (unitId == null || start == null || count == null) {
            formError = UiText.Of(R.string.need_integer)
            return null
        }
        val request = try {
            when (area) {
                RegisterArea.COIL -> ModbusRequest.ReadCoils(unitId, start, count)
                RegisterArea.DISCRETE_INPUT -> ModbusRequest.ReadDiscreteInputs(unitId, start, count)
                RegisterArea.HOLDING_REGISTER -> ModbusRequest.ReadHoldingRegisters(unitId, start, count)
                RegisterArea.INPUT_REGISTER -> ModbusRequest.ReadInputRegisters(unitId, start, count)
            }
        } catch (error: IllegalArgumentException) {
            formError = UiText.Plain(error.message ?: "")
            return null
        }
        formError = null
        return ReadParams(request, area, unitId, start)
    }

    /** 返回 true 表示轮询应该停（没有会话 / 连接已断开）。 */
    private suspend fun performRead(params: ReadParams): Boolean {
        val began = clock()
        val result = session.execute(params.request)
        val elapsed = clock() - began
        return showRead(params, result, elapsed)
    }

    private fun showRead(params: ReadParams, result: ModbusResult, elapsed: Long): Boolean {
        when (result) {
            is ModbusResult.Success -> {
                val response = result.response
                val (values, isBits) = when (response) {
                    is ModbusResponse.ReadRegisters -> response.values to false
                    is ModbusResponse.ReadBits -> response.values.map { if (it) 1 else 0 } to true
                    else -> {
                        readState = ReadState.Failed(UiText.Of(R.string.badge_invalid), UiText.Of(R.string.result_done), elapsed)
                        return false
                    }
                }
                val now = clock()
                val previous = (readState as? ReadState.Ok)?.result
                val changed = buildMap<Int, Long> {
                    if (previous != null && previous.area == params.area && previous.unit == params.unit &&
                        previous.start == params.start
                    ) {
                        values.forEachIndexed { index, value ->
                            val address = params.start + index
                            val before = previous.values.getOrNull(index)
                            when {
                                before != null && before != value -> put(address, now)
                                else -> previous.changedAt[address]?.let { at ->
                                    if (now - at < FlashDurationMs) put(address, at)
                                }
                            }
                        }
                    }
                }
                values.forEachIndexed { index, value ->
                    known[ValueKey(params.unit, params.area, params.start + index)] = value
                }
                readState = if (values.isEmpty()) {
                    ReadState.Failed(UiText.Of(R.string.badge_empty), UiText.Of(R.string.result_empty), elapsed)
                } else {
                    ReadState.Ok(ReadResult(params.area, params.unit, params.start, values, isBits, elapsed, changed))
                }
                return false
            }
            is ModbusResult.Exception -> {
                readState = ReadState.Failed(
                    UiText.Of(R.string.badge_exception, listOf(result.code.code)),
                    exceptionText(result.code.code),
                    elapsed,
                )
            }
            ModbusResult.Timeout -> {
                readState = ReadState.Failed(UiText.Of(R.string.badge_timeout), UiText.Of(R.string.result_timeout), elapsed)
            }
            ModbusResult.UnknownOutcome -> {
                readState = ReadState.Failed(UiText.Of(R.string.badge_unknown), UiText.Of(R.string.result_unknown), elapsed)
            }
            ModbusResult.Disconnected -> {
                readState = ReadState.Failed(UiText.Of(R.string.badge_disconnected), UiText.Of(R.string.result_disconnected), elapsed)
                return true
            }
            is ModbusResult.InvalidResponse -> {
                readState = ReadState.Failed(UiText.Of(R.string.badge_invalid), UiText.Plain(result.detail), elapsed)
                return true
            }
        }
        return false
    }

    // ---- 写入 ----

    /** 点“预览并写入”：校验输入，生成写入计划，交给确认弹窗。**不会**直接发送。 */
    fun requestWrite() {
        when (val planned = currentPlan()) {
            is PlanResult.Invalid -> formError = planned.message
            is PlanResult.Ok -> {
                formError = null
                pending = pendingFor(planned.plan)
            }
        }
    }

    fun currentPlan(): PlanResult = planWrite(writeArea, unit, address, writeWords, writeCoils, writeFormat, forceMultiple)

    /** 确认弹窗里改单个线圈的新值（稿面“OFF | ON”分段）。 */
    fun setPendingCoil(value: Boolean) {
        val current = pending ?: return
        val plan = current.plan
        if (plan.area != RegisterArea.COIL || plan.bits.size != 1) return
        val request = if (plan.request is ModbusRequest.WriteSingleCoil) {
            ModbusRequest.WriteSingleCoil(plan.unit, plan.address, value)
        } else {
            ModbusRequest.WriteMultipleCoils(plan.unit, plan.address, listOf(value))
        }
        val next = WritePlan(request, plan.area, plan.unit, plan.address, emptyList(), listOf(value))
        pending = PendingWrite(next, current.current)
        writeCoils = listOf(value)
    }

    fun dismissPending() {
        pending = null
    }

    /** 用户在确认弹窗里点“发送写入”。 */
    fun confirmWrite() {
        val ready = pending ?: return
        pending = null
        writing = true
        viewModelScope.launch {
            try {
                val result = session.execute(ready.plan.request)
                recordWrite(ready.plan, result)
            } finally {
                writing = false
            }
        }
    }

    fun clearRecentWrites() {
        recentWrites = emptyList()
    }

    /** 点“最近写入”里的一条：把它的参数填回写入表单（不会直接发送）。 */
    fun reuse(entry: RecentWrite) {
        mode = WorkbenchMode.WRITE
        writeArea = entry.area
        unit = entry.unit.toString()
        address = entry.address.toString()
        writeFormat = WriteFormat.DEC
        if (entry.area == RegisterArea.COIL) {
            writeCoils = entry.bits.ifEmpty { listOf(false) }
            writeWords = listOf("0")
        } else {
            writeWords = entry.words.map { it.toString() }.ifEmpty { listOf("0") }
            writeCoils = listOf(false)
        }
        forceMultiple = false
        formError = null
    }

    /** 点结果表里一行的 ✎：切到写入模式，并把这一行的地址和当前值填进去。 */
    fun editRow(result: ReadResult, rowAddress: Int) {
        if (result.area != RegisterArea.COIL && result.area != RegisterArea.HOLDING_REGISTER) return
        val value = result.values.getOrNull(rowAddress - result.start) ?: return
        mode = WorkbenchMode.WRITE
        writeArea = result.area
        unit = result.unit.toString()
        address = rowAddress.toString()
        writeFormat = WriteFormat.DEC
        if (result.area == RegisterArea.COIL) {
            writeCoils = listOf(value != 0)
            writeWords = listOf("0")
        } else {
            writeWords = listOf(value.toString())
            writeCoils = listOf(false)
        }
        forceMultiple = false
        formError = null
    }

    private fun pendingFor(plan: WritePlan): PendingWrite {
        val count = plan.words.size + plan.bits.size
        val current = List(count) { index -> known[ValueKey(plan.unit, plan.area, plan.address + index)] }
        return PendingWrite(plan, current)
    }

    private fun recordWrite(plan: WritePlan, result: ModbusResult) {
        var exceptionCode: Int? = null
        val outcome = when (result) {
            is ModbusResult.Success -> WriteOutcome.OK
            is ModbusResult.Exception -> {
                exceptionCode = result.code.code
                WriteOutcome.EXCEPTION
            }
            ModbusResult.Timeout -> WriteOutcome.TIMEOUT
            ModbusResult.UnknownOutcome -> WriteOutcome.UNKNOWN
            ModbusResult.Disconnected -> WriteOutcome.DISCONNECTED
            is ModbusResult.InvalidResponse -> WriteOutcome.INVALID
        }
        if (outcome == WriteOutcome.OK) {
            plan.words.forEachIndexed { index, word -> known[ValueKey(plan.unit, plan.area, plan.address + index)] = word }
            plan.bits.forEachIndexed { index, bit -> known[ValueKey(plan.unit, plan.area, plan.address + index)] = if (bit) 1 else 0 }
        }
        val entry = RecentWrite(
            area = plan.area,
            unit = plan.unit,
            address = plan.address,
            words = plan.words,
            bits = plan.bits,
            functionCode = plan.functionCode,
            outcome = outcome,
            exceptionCode = exceptionCode,
            atMillis = wallClock(),
        )
        recentWrites = (listOf(entry) + recentWrites).take(MaxRecentWrites)
    }

    // ---- 从站（本轮不改，PR-7 再做） ----

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

    private companion object {
        /** 写入表单里的值格上限。协议上限更大（123 个寄存器 / 1968 个线圈），但一屏手写这么多没有意义。 */
        const val MaxFormValues = 16
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
