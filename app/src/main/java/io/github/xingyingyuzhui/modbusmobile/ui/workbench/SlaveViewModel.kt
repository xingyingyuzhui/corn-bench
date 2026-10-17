package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.slave.isBitArea
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.slave.ChangeSource
import io.github.xingyingyuzhui.modbusmobile.core.slave.RuleState
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.Waveform
import io.github.xingyingyuzhui.modbusmobile.ui.UiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 最近一次改值的来源（界面徽标用）。初始装载的值没有来源。 */
enum class RowSource { EXTERNAL, BROADCAST, LOCAL, SIMULATION }

/** 模拟规则的波形种类（徽标里写“模拟 · 正弦”）。 */
enum class WaveKind { CONSTANT, RAMP, SINE, SQUARE, RANDOM, SEQUENCE }

/**
 * 虚拟从站的一行。位区的 [value] 是 0 / 1。[changedAt] 是值最近一次变化的时刻（[SystemClock.elapsedRealtime]），用来闪动 2 秒。
 * [source] / [ageSeconds]：最近一次改值的来源和距今秒数；[wave]：来源是模拟时，是哪种波形。
 */
data class SlaveRow(
    val address: Int,
    val value: Int,
    val changedAt: Long?,
    val source: RowSource? = null,
    val ageSeconds: Long? = null,
    val wave: WaveKind? = null,
)

/** 正在改值的那一格（只有字区；位区点一下直接翻转）。 */
data class SlaveEdit(val area: RegisterArea, val address: Int, val draft: String, val error: UiText? = null)

/** “＋ 添加地址段”浮层。 */
data class SlaveAddRange(
    val area: RegisterArea,
    val start: String = "0",
    val count: String = "10",
    val error: UiText? = null,
)

/** “＋ 添加 Unit”浮层。 */
data class SlaveAddUnit(val draft: String = "", val error: UiText? = null)

/**
 * 从站工作台（b1-5）：多个虚拟 Unit、四个区、每行的最近来源。
 * 本地改值、加地址段、增删 Unit 之后都调 [SessionManager.saveSlaveSetup]，下次连接从站时按保存的配置装载。
 */
class SlaveViewModel(
    private val session: SessionManager,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    /** 单调时钟（纳秒），和寄存器库记录改值时间用的是同一种。 */
    private val monotonicNs: () -> Long = { System.nanoTime() },
) : ViewModel() {
    val sessionState = session.state
    val units = session.slaveUnits

    /** 当前查看的 Unit；会话的 Unit 列表里没有它时自动换成第一个。 */
    var unit by mutableStateOf(1)
        private set

    var area by mutableStateOf(RegisterArea.HOLDING_REGISTER)
        private set

    /** 当前 Unit 四个区各自的全部行（按地址升序）。 */
    var rows by mutableStateOf<Map<RegisterArea, List<SlaveRow>>>(emptyMap())
        private set

    var edit by mutableStateOf<SlaveEdit?>(null)
        private set

    var addRange by mutableStateOf<SlaveAddRange?>(null)
        private set

    var addUnit by mutableStateOf<SlaveAddUnit?>(null)
        private set

    /** 待确认删除的 Unit。 */
    var removeUnit by mutableStateOf<Int?>(null)
        private set

    /** 最近一次保存失败时为 true（文件只读 / 写失败），界面提示一行。 */
    var saveFailed by mutableStateOf(false)
        private set

    private val changedAt = HashMap<Triple<Int, RegisterArea, Int>, Long>()
    private var bank: VirtualRegisterBank? = null

    init {
        viewModelScope.launch {
            session.slaveBank.collectLatest { next ->
                bank = next
                changedAt.clear()
                edit = null
                addRange = null
                addUnit = null
                removeUnit = null
                if (next == null) {
                    rows = emptyMap()
                    return@collectLatest
                }
                publish(next)
                next.changes.collect { change ->
                    if (change.source != ChangeSource.INITIAL) {
                        changedAt[Triple(change.unitId, change.area, change.address)] = clock()
                    }
                    publish(next)
                }
            }
        }
        viewModelScope.launch {
            session.slaveUnits.collect { list ->
                if (list.isNotEmpty() && unit !in list) unit = list.first()
                bank?.let { publish(it) }
            }
        }
        // “外部写 · 2 s”里的秒数每秒走一次。
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                bank?.let { publish(it) }
            }
        }
    }

    fun selectUnit(next: Int) {
        if (next !in units.value) return
        unit = next
        edit = null
        bank?.let { publish(it) }
    }

    fun selectArea(next: RegisterArea) {
        area = next
    }

    fun rowsOf(area: RegisterArea): List<SlaveRow> = rows[area].orEmpty()

    // ---- 改值 ----

    fun startEdit(area: RegisterArea, address: Int) {
        val current = rowsOf(area).firstOrNull { it.address == address } ?: return
        edit = SlaveEdit(area, address, current.value.toString())
    }

    fun setDraft(text: String) {
        edit = edit?.copy(draft = text, error = null)
    }

    fun cancelEdit() {
        edit = null
    }

    fun commitEdit() {
        val current = edit ?: return
        val value = parseWord(current.draft, WriteFormat.DEC)
        if (value == null) {
            edit = current.copy(error = UiText.Of(R.string.error_slave_value))
            return
        }
        bank?.putWord(unit, current.area, current.address, value)
        edit = null
        save()
    }

    fun toggleBit(area: RegisterArea, address: Int) {
        val current = rowsOf(area).firstOrNull { it.address == address } ?: return
        bank?.putBit(unit, area, address, current.value == 0)
        save()
    }

    // ---- 添加地址段 ----

    fun openAddRange() {
        addRange = SlaveAddRange(area)
    }

    fun updateAddRange(update: SlaveAddRange.() -> SlaveAddRange) {
        addRange = addRange?.update()?.copy(error = null)
    }

    fun cancelAddRange() {
        addRange = null
    }

    /** 已有的地址不动（不覆盖现值），只补上缺的，初始值 0 / OFF。 */
    fun commitAddRange() {
        val request = addRange ?: return
        val start = request.start.trim().toIntOrNull()
        val count = request.count.trim().toIntOrNull()
        if (start == null || count == null || start !in 0..MaxAddress || count !in 1..MaxRangeCount || start + count - 1 > MaxAddress) {
            addRange = request.copy(error = UiText.Of(R.string.error_slave_range, listOf(MaxRangeCount)))
            return
        }
        val target = bank ?: return
        val existing = rowsOf(request.area).map { it.address }.toSet()
        for (address in start until start + count) {
            if (address in existing) continue
            if (request.area.isBitArea()) {
                target.putBit(unit, request.area, address, false)
            } else {
                target.putWord(unit, request.area, address, 0)
            }
        }
        area = request.area
        addRange = null
        save()
    }

    // ---- 增删 Unit ----

    fun openAddUnit() {
        addUnit = SlaveAddUnit()
    }

    fun setUnitDraft(text: String) {
        addUnit = addUnit?.copy(draft = text.filter { it.isDigit() }.take(3), error = null)
    }

    fun cancelAddUnit() {
        addUnit = null
    }

    /** 新 Unit 没有任何地址，外部主站读它会收到“非法地址”，界面提示去加地址段。 */
    fun commitAddUnit() {
        val request = addUnit ?: return
        val id = request.draft.toIntOrNull()
        val current = units.value
        val error = when {
            id == null || id !in 1..247 -> UiText.Of(R.string.error_slave_unit_range)
            id in current -> UiText.Of(R.string.error_slave_unit_exists, listOf(id))
            else -> null
        }
        if (error != null) {
            addUnit = request.copy(error = error)
            return
        }
        addUnit = null
        viewModelScope.launch {
            if (session.setSlaveUnits(current + id!!)) {
                unit = id
                bank?.let { publish(it) }
            } else {
                saveFailed = true
            }
        }
    }

    fun askRemoveUnit() {
        if (units.value.size > 1) removeUnit = unit
    }

    fun cancelRemoveUnit() {
        removeUnit = null
    }

    /** 删掉的 Unit 不再响应；它的地址和值不再保存（寄存器库里的值本次会话内还在，断开后就没了）。 */
    fun confirmRemoveUnit() {
        val target = removeUnit ?: return
        removeUnit = null
        val next = units.value - target
        if (next.isEmpty()) return
        viewModelScope.launch {
            if (!session.setSlaveUnits(next)) saveFailed = true
        }
    }

    private fun save() {
        saveFailed = !session.saveSlaveSetup()
    }

    private fun publish(bank: VirtualRegisterBank) {
        val now = clock()
        val nowNs = monotonicNs()
        val rules = session.simulation.value?.states?.value.orEmpty()
        val u = unit
        fun row(area: RegisterArea, address: Int, value: Int): SlaveRow {
            val change = bank.lastChange(u, area, address)?.takeIf { it.source != ChangeSource.INITIAL }
            val source = when (change?.source) {
                ChangeSource.EXTERNAL_WRITE -> RowSource.EXTERNAL
                ChangeSource.BROADCAST -> RowSource.BROADCAST
                ChangeSource.LOCAL_EDIT -> RowSource.LOCAL
                ChangeSource.SIMULATION -> RowSource.SIMULATION
                ChangeSource.INITIAL, null -> null
            }
            return SlaveRow(
                address = address,
                value = value,
                changedAt = changedAt[Triple(u, area, address)]?.takeIf { now - it < FlashWindowMs },
                source = source,
                ageSeconds = change?.let { ((nowNs - it.monotonicNs) / 1_000_000_000L).coerceAtLeast(0) },
                wave = if (source == RowSource.SIMULATION) waveAt(rules, u, area, address) else null,
            )
        }
        rows = SlaveAreasInOrder.associateWith { area ->
            if (area.isBitArea()) {
                bank.bits(u, area).map { row(area, it.address, if (it.value) 1 else 0) }
            } else {
                bank.words(u, area).map { row(area, it.address, it.value) }
            }
        }
    }

    companion object {
        const val MaxAddress = 0xFFFF
        const val MaxRangeCount = 125
        private const val FlashWindowMs = 2_000L
        private val SlaveAreasInOrder = io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveAreas
    }
}

private fun waveAt(rules: List<RuleState>, unit: Int, area: RegisterArea, address: Int): WaveKind? {
    val rule = rules.firstOrNull { state ->
        val t = state.rule.target
        t.unitId == unit && t.area == area && address in t.address until t.address + t.count
    }?.rule ?: return null
    return when (rule.waveform) {
        is Waveform.Constant -> WaveKind.CONSTANT
        is Waveform.Ramp -> WaveKind.RAMP
        is Waveform.Sine -> WaveKind.SINE
        is Waveform.Square -> WaveKind.SQUARE
        is Waveform.RandomUniform -> WaveKind.RANDOM
        is Waveform.Sequence -> WaveKind.SEQUENCE
    }
}
