package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.UiText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 虚拟从站的一行。位区的 [value] 是 0 / 1。[changedAt] 是值最近一次变化的时刻（[SystemClock.elapsedRealtime]），用来闪动 2 秒。 */
data class SlaveRow(val address: Int, val value: Int, val changedAt: Long?)

/** 正在改值的那一格（只有字区；位区点一下直接翻转）。 */
data class SlaveEdit(val area: RegisterArea, val address: Int, val draft: String, val error: UiText? = null)

/** “＋ 添加地址段”弹窗。 */
data class SlaveAddRange(
    val area: RegisterArea,
    val start: String = "0",
    val count: String = "10",
    val error: UiText? = null,
)

/**
 * 从站工作台（b1-5）。只用现有的 [VirtualRegisterBank] 接口：读 `words/bits`，改 `putWord/putBit`，
 * 变化从 `changes` 流来。`changes` 里没有“谁改的”，所以这里**不**显示“最近来源”，见 ui-diff.md §12。
 */
class SlaveViewModel(
    private val session: SessionManager,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {
    val sessionState = session.state

    var area by mutableStateOf(RegisterArea.HOLDING_REGISTER)
        private set

    /** 四个区各自当前的全部行（按地址升序）。 */
    var rows by mutableStateOf<Map<RegisterArea, List<SlaveRow>>>(emptyMap())
        private set

    var edit by mutableStateOf<SlaveEdit?>(null)
        private set

    var addRange by mutableStateOf<SlaveAddRange?>(null)
        private set

    private val changedAt = HashMap<Pair<RegisterArea, Int>, Long>()
    private var bank: VirtualRegisterBank? = null

    init {
        viewModelScope.launch {
            session.slaveBank.collectLatest { next ->
                bank = next
                changedAt.clear()
                edit = null
                addRange = null
                if (next == null) {
                    rows = emptyMap()
                    return@collectLatest
                }
                publish(next)
                next.changes.collect { change ->
                    if (change.unitId == UNIT) changedAt[change.area to change.address] = clock()
                    publish(next)
                }
            }
        }
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
        bank?.putWord(UNIT, current.area, current.address, value)
        edit = null
    }

    fun toggleBit(area: RegisterArea, address: Int) {
        val current = rowsOf(area).firstOrNull { it.address == address } ?: return
        bank?.putBit(UNIT, area, address, current.value == 0)
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
            if (request.area == RegisterArea.COIL || request.area == RegisterArea.DISCRETE_INPUT) {
                target.putBit(UNIT, request.area, address, false)
            } else {
                target.putWord(UNIT, request.area, address, 0)
            }
        }
        area = request.area
        addRange = null
    }

    private fun publish(bank: VirtualRegisterBank) {
        val now = clock()
        fun flash(area: RegisterArea, address: Int): Long? =
            changedAt[area to address]?.takeIf { now - it < FlashWindowMs }
        rows = mapOf(
            RegisterArea.HOLDING_REGISTER to bank.words(UNIT, RegisterArea.HOLDING_REGISTER)
                .map { SlaveRow(it.address, it.value, flash(RegisterArea.HOLDING_REGISTER, it.address)) },
            RegisterArea.INPUT_REGISTER to bank.words(UNIT, RegisterArea.INPUT_REGISTER)
                .map { SlaveRow(it.address, it.value, flash(RegisterArea.INPUT_REGISTER, it.address)) },
            RegisterArea.COIL to bank.bits(UNIT, RegisterArea.COIL)
                .map { SlaveRow(it.address, if (it.value) 1 else 0, flash(RegisterArea.COIL, it.address)) },
            RegisterArea.DISCRETE_INPUT to bank.bits(UNIT, RegisterArea.DISCRETE_INPUT)
                .map { SlaveRow(it.address, if (it.value) 1 else 0, flash(RegisterArea.DISCRETE_INPUT, it.address)) },
        )
    }

    companion object {
        /** 现有 application 层只有 Unit 1 的虚拟从站（DefaultSessionManager.SLAVE_UNIT）。 */
        const val UNIT = 1
        const val MaxAddress = 0xFFFF
        const val MaxRangeCount = 125
        private const val FlashWindowMs = 2_000L
    }
}
