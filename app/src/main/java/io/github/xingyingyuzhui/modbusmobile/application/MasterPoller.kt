package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 主站工作台的周期读取（「每 N s」）。T-20261018-qa-02：轮询是会话级行为，挂在 AppContainer 的应用级作用域上，
 * 是「运行中 / 间隔 / 请求」的唯一状态源；工作台只订阅 [state] 显示按钮，并通过 [attach] 提供当前表单和接收结果。
 *
 * 只在这几种情况下停：用户点停止（[stop]）、连接断开或换了一次连接（会话 epoch 变化 / 不再 Running）、[close]。
 * 另外保留原来的两条保护：收到 Disconnected、连续 [MaxInvalid] 次无效响应时停（避免对着坏设备一直刷）；
 * 订阅方的表单变成非法（[Client.nextRequest] 返回 null）时停，由订阅方显示表单错误。
 * 离开工作台、应用退到后台都**不**停。
 */
class MasterPoller(
    private val session: SessionManager,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    data class State(
        val running: Boolean = false,
        val intervalMs: Long = DefaultIntervalMs,
        /** 正在轮询（或最后一次轮询）的读请求；工作台重建时用它回填表单。 */
        val request: ModbusRequest? = null,
    )

    /** 一次轮询读取的结果，交给订阅方显示。 */
    data class Outcome(val request: ModbusRequest, val result: ModbusResult, val elapsedMs: Long)

    /** 订阅方（工作台）。不在时轮询照常进行，只是结果没人显示。 */
    interface Client {
        /** true = 这一拍让路（手动写入优先：写模式、待确认写入、写入在途）。 */
        fun holdPolling(): Boolean = false

        /** 下一拍用的请求（跟随表单）；null = 表单非法，轮询停止。 */
        fun nextRequest(): ModbusRequest?

        fun onPollResult(outcome: Outcome)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null
    private var client: Client? = null
    /** 构造时就取当前 epoch，免得 watcher 第一次收到同一个值时把刚开始的轮询停掉。 */
    private var epoch: Long? = (session.state.value as? SessionState.Running)?.epoch
    private var closed = false

    private val watcher: Job = scope.launch {
        session.state.collect { s ->
            val next = (s as? SessionState.Running)?.epoch
            if (next != epoch) {
                epoch = next
                stop()
            }
        }
    }

    /** 工作台出现时接上；返回值用来断开（只断开自己，不影响后接上的）。 */
    fun attach(client: Client): () -> Unit {
        this.client = client
        return { if (this.client === client) this.client = null }
    }

    fun start(request: ModbusRequest, intervalMs: Long = _state.value.intervalMs) {
        if (closed) return
        job?.cancel()
        // 同步一次当前 epoch：刚连上、watcher 还没收到新 epoch 时就点了开始，watcher 随后看到「变化」会把这次轮询停掉。
        epoch = (session.state.value as? SessionState.Running)?.epoch
        _state.value = State(running = true, intervalMs = intervalMs, request = request)
        // LAZY：先记下 job 再启动。Main.immediate 下 launch 会同步跑到第一次挂起，finish() 要能认出自己。
        val next = scope.launch(start = CoroutineStart.LAZY) { loop() }
        job = next
        next.start()
    }

    fun stop() {
        job?.cancel()
        job = null
        if (_state.value.running) _state.value = _state.value.copy(running = false)
    }

    /** 运行中改间隔立即生效（下一次等待用新值）。 */
    fun setInterval(ms: Long) {
        _state.value = _state.value.copy(intervalMs = ms)
    }

    /** 停掉并不再接受 [start]。 */
    fun close() {
        closed = true
        stop()
        watcher.cancel()
        client = null
    }

    private suspend fun CoroutineScope.loop() {
        val me = coroutineContext[Job]
        // finally 里 finish：正常结束、异常退出都把「运行中」清掉；被 stop() / 新的 start() 取消时 job 已经不是自己，finish 不动状态。
        try {
            var invalidStreak = 0
            while (isActive) {
                if ((session.state.value as? SessionState.Running)?.epoch != epoch) return
                val c = client
                if (c != null && c.holdPolling()) {
                    delay(HoldStepMs)
                    continue
                }
                val request = (if (c != null) c.nextRequest() else _state.value.request) ?: return
                if (request != _state.value.request) _state.value = _state.value.copy(request = request)
                val began = clock()
                val result = session.execute(request)
                if (!isActive || (session.state.value as? SessionState.Running)?.epoch != epoch) return
                val outcome = Outcome(request, result, clock() - began)
                client?.onPollResult(outcome)
                invalidStreak = if (isInvalid(result)) invalidStreak + 1 else 0
                if (result == ModbusResult.Disconnected || invalidStreak >= MaxInvalid) return
                delay(_state.value.intervalMs)
            }
        } finally {
            finish(me)
        }
    }

    /** 轮询自己结束（正常结束或异常；不是被 [stop] / 新的 [start] 取消）。只清自己那一个 Job。 */
    private fun finish(me: Job?) {
        if (job !== me) return
        job = null
        _state.value = _state.value.copy(running = false)
    }

    private fun isInvalid(result: ModbusResult): Boolean = when (result) {
        is ModbusResult.InvalidResponse -> true
        is ModbusResult.Success -> result.response !is ModbusResponse.ReadRegisters && result.response !is ModbusResponse.ReadBits
        else -> false
    }

    companion object {
        const val DefaultIntervalMs = 1_000L

        /** 连续这么多次无效响应才停；一次噪声帧不该让轮询永久停掉。 */
        const val MaxInvalid = 3
        private const val HoldStepMs = 100L
    }
}
