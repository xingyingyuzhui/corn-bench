package io.github.xingyingyuzhui.modbusmobile.core.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RuleStatus {
    RUNNING,

    /** 用户点了暂停（或规则本身 enabled = false）。 */
    PAUSED,

    /** 外部主站写了规则管着的地址，按 [ExternalWritePolicy.PAUSE] 自动暂停，等用户恢复。 */
    PAUSED_BY_EXTERNAL_WRITE,

    /** 目标地址没有映射。模拟器不会替用户新建寄存器。 */
    UNMAPPED,
}

data class RuleState(
    val rule: SimulationRule,
    val status: RuleStatus,
    /** 最近一次写进去的原始值；还没写过为 null。 */
    val lastRaw: Int? = null,
)

/**
 * 从站模拟器：按规则周期性改虚拟寄存器（spec §4、§5.3）。
 *
 * - 只通过 [VirtualRegisterBank.putWord] / [VirtualRegisterBank.putBit] 写，来源标 [ChangeSource.SIMULATION]；
 *   不发任何报文，也不占用从站响应的协程。
 * - 写之前先看 [VirtualRegisterBank.lastChange]：目标地址在本规则上次写之后被外部主站写过（单播或广播），
 *   [ExternalWritePolicy.PAUSE] 的规则就暂停，不覆盖外部写进去的值。
 * - 目标地址没有映射就标 [RuleStatus.UNMAPPED]、不写。
 * - 所有状态只在一个协程里改，对外只暴露不可变的 [states]。
 */
class SimulationEngine(
    private val bank: VirtualRegisterBank,
    private val clock: MonotonicClock,
    private val random: Random = Random.Default,
    private val tickMs: Long = DEFAULT_TICK_MS,
) {
    private class Running(
        var rule: SimulationRule,
        var status: RuleStatus,
        var originNs: Long,
        var nextDueNs: Long,
        var step: Long = 0,
        var lastWriteNs: Long = Long.MIN_VALUE,
        var lastRaw: Int? = null,
    )

    private val lock = Any()
    private val rules = LinkedHashMap<String, Running>()
    private val _states = MutableStateFlow<List<RuleState>>(emptyList())
    val states: StateFlow<List<RuleState>> = _states.asStateFlow()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        check(job == null) { "模拟器已经启动" }
        job = scope.launch {
            while (isActive) {
                tick()
                // 没有在跑的规则时放慢节拍，少唤醒几次。
                val idle = synchronized(lock) { rules.values.none { it.status == RuleStatus.RUNNING } }
                delay(if (idle) IDLE_TICK_MS else tickMs)
            }
        }
    }

    fun close() {
        job?.cancel()
        job = null
    }

    /** 替换全部规则。保留的规则（同 id 且内容相同）不重新计时。 */
    fun setRules(next: List<SimulationRule>) = synchronized(lock) {
        require(next.map { it.id }.toSet().size == next.size) { "规则 id 不能重复" }
        val now = clock.nowNs()
        val kept = rules.filterKeys { id -> next.any { it.id == id && it == rules[id]?.rule } }
        rules.clear()
        next.forEach { rule ->
            rules[rule.id] = kept[rule.id] ?: Running(
                rule = rule,
                status = if (rule.enabled) RuleStatus.RUNNING else RuleStatus.PAUSED,
                originNs = now,
                nextDueNs = now,
                // 规则开始之前的外部写入不算：只看开始之后的。
                lastWriteNs = now,
            )
        }
        publish()
    }

    fun pause(id: String) = synchronized(lock) {
        rules[id]?.let { it.status = RuleStatus.PAUSED }
        publish()
    }

    /** 恢复：从现在起重新计时；外部写入造成的暂停也由这里恢复（之后再被外部写才会再停）。 */
    fun resume(id: String) = synchronized(lock) {
        rules[id]?.let { resumeLocked(it) }
        publish()
    }

    fun pauseAll() = synchronized(lock) {
        rules.values.forEach { it.status = RuleStatus.PAUSED }
        publish()
    }

    fun resumeAll() = synchronized(lock) {
        rules.values.forEach { resumeLocked(it) }
        publish()
    }

    /** 做一拍：到期的规则各写一次。测试里直接调用，不用起协程。 */
    fun tick() = synchronized(lock) {
        val now = clock.nowNs()
        var changed = false
        for (running in rules.values) {
            if (running.status != RuleStatus.RUNNING || now < running.nextDueNs) continue
            changed = true
            if (externallyWritten(running)) {
                running.status = RuleStatus.PAUSED_BY_EXTERNAL_WRITE
                continue
            }
            if (!mapped(running.rule.target)) {
                running.status = RuleStatus.UNMAPPED
                continue
            }
            val elapsedMs = (now - running.originNs) / 1_000_000L
            val value = Waveforms.value(running.rule.waveform, elapsedMs, running.step, random)
            val raw = Waveforms.raw(running.rule, value, random)
            write(running.rule.target, raw)
            running.lastRaw = raw
            running.lastWriteNs = clock.nowNs()
            running.step += 1
            // 落后时不补跑，直接排到下一个周期（与轮询的“跳过过期轮次”一致）。
            val refreshNs = running.rule.refreshMs * 1_000_000L
            running.nextDueNs = maxOf(running.nextDueNs + refreshNs, now + 1)
        }
        if (changed) publish()
    }

    private fun resumeLocked(running: Running) {
        val now = clock.nowNs()
        running.status = RuleStatus.RUNNING
        running.nextDueNs = now
        // 恢复之后，此前的外部写入不再触发暂停。
        running.lastWriteNs = now
    }

    private fun externallyWritten(running: Running): Boolean {
        if (running.rule.onExternalWrite != ExternalWritePolicy.PAUSE) return false
        val target = running.rule.target
        return (0 until target.count).any { offset ->
            val change = bank.lastChange(target.unitId, target.area, target.address + offset) ?: return@any false
            (change.source == ChangeSource.EXTERNAL_WRITE || change.source == ChangeSource.BROADCAST) &&
                change.monotonicNs > running.lastWriteNs
        }
    }

    private fun mapped(target: SimulationTarget): Boolean {
        val read = if (target.isBit) {
            bank.readBits(target.unitId, target.area, target.address, target.count)
        } else {
            bank.readWords(target.unitId, target.area, target.address, target.count)
        }
        return read is BankAccess.Ok<*>
    }

    private fun write(target: SimulationTarget, raw: Int) {
        for (offset in 0 until target.count) {
            val address = target.address + offset
            if (target.isBit) {
                bank.putBit(target.unitId, target.area, address, raw != 0, ChangeSource.SIMULATION)
            } else {
                bank.putWord(target.unitId, target.area, address, raw, ChangeSource.SIMULATION)
            }
        }
    }

    private fun publish() {
        _states.value = rules.values.map { RuleState(it.rule, it.status, it.lastRaw) }
    }

    companion object {
        /** 节拍：规则的刷新间隔最小 50 ms，节拍取同样的粒度。 */
        const val DEFAULT_TICK_MS = 50L

        /** 没有在跑的规则时的节拍。用户点“恢复”最多等这么久才开始写。 */
        const val IDLE_TICK_MS = 500L
    }
}

/** 规则只写寄存器区或位区里能本地改的四类，都可以；这里只给界面用来列选项。 */
val SimulatableAreas: List<RegisterArea> = listOf(
    RegisterArea.HOLDING_REGISTER,
    RegisterArea.INPUT_REGISTER,
    RegisterArea.COIL,
    RegisterArea.DISCRETE_INPUT,
)
