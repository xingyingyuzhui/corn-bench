package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.ValueCodec
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetWrites
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.PendingWrite
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.PlanResult
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.WritePlan
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.WriteFormat
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.planWrite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 位写入的上下文：[baseWord] 是确认前刚读到的整字，[bit] 被改成 [target]；确认时会再读一次核对。 */
data class BitEdit(val bit: Int, val baseWord: Int, val target: Boolean)

/** 等用户确认的面板写入：[write] 就是工作台同一个 [PendingWrite]（整帧、当前值、广播标记都来自它）。 */
data class PanelPending(val widget: Widget, val write: PendingWrite, val bit: BitEdit? = null)

enum class WriteNoticeKind { OK, SENT, EXCEPTION, TIMEOUT, UNKNOWN, DISCONNECTED, INVALID, READ_FAILED, CHANGED, NOT_MASTER, BAD_STATE }

/** 一次写入 / 读后写的结果提示，面板顶部的一行，由界面翻译成文字。 */
data class PanelWriteNotice(val widgetName: String, val kind: WriteNoticeKind, val code: Int? = null, val summary: String = "")

/**
 * 面板上所有“会写设备”的操作的唯一入口。
 *
 * 不绕过工作台的写入流程：
 * - 计划由 [planWrite] 生成（FC05 / FC06 / FC16 与整帧和工作台逐字节一致），
 * - **只有**用户在 [io.github.xingyingyuzhui.modbusmobile.ui.workbench.WriteConfirmSheet] 里点“发送写入”（[confirm]）才会调用 `session.execute` 发写请求；
 *   请求之后取消 / 点外面（[dismiss]）什么都不发；
 * - Unit 0 = 广播：[PendingWrite.broadcast] 让确认弹窗变红；
 * - 寄存器位：先 FC03 读整字 → 改一位 → FC06 写回；确认时再读一次，字变了就不发、用新字重新确认。
 *
 * 守卫：只有主站会话运行中才接受请求（从站 / 没连接：拒绝并提示）；[editing] 为 true（编辑模式 / 向导）时一律拒绝；
 * 同一时间只有一个写入流程（弹窗开着或正在读 / 写时，新的请求被忽略）。
 */
class PanelWriteController(
    private val session: SessionManager,
    private val scope: CoroutineScope,
    /** 编辑模式 / 向导里为 true：这时面板上的开关 / 按钮不可操作，控制器也不接受请求。 */
    private val editing: () -> Boolean = { false },
    private val snapshotOf: (String) -> PointSnapshot? = { null },
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    var pending by mutableStateOf<PanelPending?>(null)
        private set

    /** 正在“读整字 / 核对 / 发送”的组件 id；这期间该卡不可再点。 */
    var busyWidgetId by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<PanelWriteNotice?>(null)
        private set

    val isMaster: Boolean
        get() = (session.state.value as? SessionState.Running)?.role == Role.MASTER

    fun clearNotice() {
        notice = null
    }

    // ---- 开关 ----

    /** 开关被点：[target] 是想切到的状态。线圈直接出确认；寄存器位先读整字。 */
    fun requestSwitch(widget: Widget, target: Boolean) {
        val point = widget.point ?: return
        if (widget.kind != WidgetKinds.SWITCH || WidgetWrites.switchProblem(point) != null) return
        if (!accept(widget)) return
        if (point.area == RegisterArea.COIL) {
            val snap = snapshotOf(point.id)
            val known = snap?.takeIf { it.quality == Quality.OK && it.raw != null }
            val current = known?.engineering?.let { if (it != 0.0) 1 else 0 }
            val plan = planWrite(RegisterArea.COIL, point.unitId.toString(), point.address.toString(), emptyList(), listOf(target), WriteFormat.DEC, false)
            val ok = (plan as? PlanResult.Ok)?.plan ?: return reject(widget, WriteNoticeKind.BAD_STATE)
            val age = known?.updatedWallMs?.let { (wallClock() - it).coerceAtLeast(0) }
            pending = PanelPending(widget, PendingWrite(ok, listOf(current), age, currentFromRead = true))
            return
        }
        busyWidgetId = widget.id
        notice = null
        scope.launch {
            val word = readWord(point)
            if (word == null) {
                busyWidgetId = null
                notice = PanelWriteNotice(widget.displayName(point), WriteNoticeKind.READ_FAILED)
                return@launch
            }
            val next = bitPending(widget, point, word, target)
            busyWidgetId = null
            if (next == null) notice = PanelWriteNotice(widget.displayName(point), WriteNoticeKind.BAD_STATE) else pending = next
        }
    }

    // ---- 按钮 ----

    /** 按钮被点：生成“预设值”的写入计划，交确认弹窗。Unit 0 = 广播（红色确认）。 */
    fun requestButton(widget: Widget) {
        val point = widget.point ?: return
        val value = widget.writeValue ?: return
        if (widget.kind != WidgetKinds.BUTTON || WidgetWrites.buttonProblem(point, value) != null) return
        if (!accept(widget)) return
        val result = when (point.area) {
            RegisterArea.COIL -> planWrite(
                RegisterArea.COIL, point.unitId.toString(), point.address.toString(), emptyList(), listOf(value != 0.0), WriteFormat.DEC, false,
            )
            else -> {
                val words = WidgetWrites.buttonWords(point, value) ?: return reject(widget, WriteNoticeKind.BAD_STATE)
                planWrite(
                    RegisterArea.HOLDING_REGISTER, point.unitId.toString(), point.address.toString(),
                    words.map { it.toString() }, emptyList(), WriteFormat.DEC, forceMultiple = false,
                )
            }
        }
        val plan = (result as? PlanResult.Ok)?.plan ?: return reject(widget, WriteNoticeKind.BAD_STATE)
        // 按钮点位不轮询，没有“当前值”：确认弹窗里写“未读取”（广播连这一栏都没有）
        val count = plan.words.size + plan.bits.size
        pending = PanelPending(widget, PendingWrite(plan, List(count) { null }))
        notice = null
    }

    // ---- 确认 / 取消 ----

    /** 确认弹窗里改单个线圈的新值（“OFF | ON”分段）。 */
    fun setPendingCoil(value: Boolean) {
        val current = pending ?: return
        val plan = current.write.plan
        if (plan.area != RegisterArea.COIL || plan.bits.size != 1) return
        val request = if (plan.request is ModbusRequest.WriteSingleCoil) {
            ModbusRequest.WriteSingleCoil(plan.unit, plan.address, value)
        } else {
            ModbusRequest.WriteMultipleCoils(plan.unit, plan.address, listOf(value))
        }
        pending = current.copy(write = current.write.copy(plan = WritePlan(request, plan.area, plan.unit, plan.address, emptyList(), listOf(value))))
    }

    /** 取消 / 点弹窗外：什么都不发。 */
    fun dismiss() {
        pending = null
    }

    /** 用户点了“发送写入”。位写入先再读一次核对整字；变了就不发，用新字重新弹确认。 */
    fun confirm() {
        val ready = pending ?: return
        if (editing() || !isMaster) {
            pending = null
            return
        }
        pending = null
        busyWidgetId = ready.widget.id
        val point = ready.widget.point
        val name = point?.let { ready.widget.displayName(it) } ?: ready.widget.displayTitle
        scope.launch {
            try {
                val bit = ready.bit
                if (bit != null && point != null) {
                    val now = readWord(point)
                    if (now == null) {
                        notice = PanelWriteNotice(name, WriteNoticeKind.READ_FAILED)
                        return@launch
                    }
                    if (now != bit.baseWord) {
                        // 整字在弹窗开着的时候被别人改了：不拿旧字写回（会把别人的改动覆盖掉）
                        val again = bitPending(ready.widget, point, now, bit.target)
                        notice = PanelWriteNotice(name, WriteNoticeKind.CHANGED)
                        if (again != null) pending = again
                        return@launch
                    }
                }
                val result = session.execute(ready.write.plan.request)
                notice = outcome(name, ready.write.plan, result)
            } finally {
                busyWidgetId = null
            }
        }
    }

    // ---- 内部 ----

    private fun accept(widget: Widget): Boolean {
        if (editing()) return false
        if (!isMaster) {
            reject(widget, WriteNoticeKind.NOT_MASTER)
            return false
        }
        return pending == null && busyWidgetId == null
    }

    private fun reject(widget: Widget, kind: WriteNoticeKind) {
        notice = PanelWriteNotice(widget.point?.let { widget.displayName(it) } ?: widget.displayTitle, kind)
    }

    /** FC03 读 1 个字；任何非成功 / 长度不对都当读取失败（不写）。 */
    private suspend fun readWord(point: PointDefinition): Int? {
        val result = session.execute(ModbusRequest.ReadHoldingRegisters(point.unitId, point.address, 1))
        val response = (result as? ModbusResult.Success)?.response as? ModbusResponse.ReadRegisters ?: return null
        return response.values.singleOrNull()?.and(0xFFFF)
    }

    private fun bitPending(widget: Widget, point: PointDefinition, word: Int, target: Boolean): PanelPending? {
        if (point.type != DataType.BIT) return null
        val newWord = ValueCodec.withBit(word, point.bit, target)
        val plan = planWrite(
            RegisterArea.HOLDING_REGISTER, point.unitId.toString(), point.address.toString(), listOf(newWord.toString()), emptyList(),
            WriteFormat.DEC, false,
        )
        val ok = (plan as? PlanResult.Ok)?.plan ?: return null
        return PanelPending(widget, PendingWrite(ok, listOf(word), currentAgeMs = 0, currentFromRead = true), BitEdit(point.bit, word, target))
    }

    private fun outcome(name: String, plan: WritePlan, result: ModbusResult): PanelWriteNotice {
        val summary = summarize(plan)
        return when (result) {
            // 广播没有响应：引擎返回的是合成的成功，只能说“已发送”
            is ModbusResult.Success -> PanelWriteNotice(name, if (plan.unit == 0) WriteNoticeKind.SENT else WriteNoticeKind.OK, summary = summary)
            is ModbusResult.Exception -> PanelWriteNotice(name, WriteNoticeKind.EXCEPTION, result.code.code, summary)
            ModbusResult.Timeout -> PanelWriteNotice(name, WriteNoticeKind.TIMEOUT, summary = summary)
            ModbusResult.UnknownOutcome -> PanelWriteNotice(name, WriteNoticeKind.UNKNOWN, summary = summary)
            ModbusResult.Disconnected -> PanelWriteNotice(name, WriteNoticeKind.DISCONNECTED, summary = summary)
            is ModbusResult.InvalidResponse -> PanelWriteNotice(name, WriteNoticeKind.INVALID, summary = summary)
        }
    }

    private fun summarize(plan: WritePlan): String {
        val target = if (plan.unit == 0) "广播" else "U${plan.unit}"
        val value = when {
            plan.area == RegisterArea.COIL -> if (plan.bits.firstOrNull() == true) "ON" else "OFF"
            plan.words.size == 1 -> plan.words[0].toString()
            else -> plan.words.joinToString(" ")
        }
        val prefix = if (plan.area == RegisterArea.COIL) "C" else "HR"
        return "$target $prefix${plan.address} = $value"
    }
}
