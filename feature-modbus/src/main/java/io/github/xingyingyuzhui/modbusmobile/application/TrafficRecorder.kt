package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 记录器用的时钟：单调时钟（与 core 的 `MonotonicClock` 同源，默认 `System.nanoTime()`）和墙钟。 */
interface RecorderClock {
    fun nanoTime(): Long
    fun wallMs(): Long
}

object SystemRecorderClock : RecorderClock {
    override fun nanoTime(): Long = System.nanoTime()
    override fun wallMs(): Long = System.currentTimeMillis()
}

enum class RecorderState {
    /** 默认：没点过“开始”，什么都不记。 */
    IDLE,
    RECORDING,
    /** 暂停：停止捕获，已有帧保留。 */
    PAUSED,
    /** 记满（帧数或字节数到上限）：已自动停止，已有帧保留；清除后可继续。 */
    FULL,
}

/**
 * 一条记录。[event] 为空表示“新会话”分隔标记（[epoch] 是新会话的编号），不算帧、不占容量。
 * [wallMs] 是这一帧发生时的墙钟（毫秒时间戳，UTC 纪元），在**采集的那一刻**由单调时钟换算得到。
 */
class RecordedItem(
    val seq: Long,
    val wallMs: Long,
    val event: TrafficEvent?,
    val epoch: Long,
)

/**
 * 日志记录器（application 层，不动 modbus-core）：
 * - 默认不记录；点 [start] 才开始收 [SessionManager.traffic]；
 * - 丢弃 `monotonicNs` 早于“开始 / 继续”那一刻的事件——`SharedFlow` 有 replay，不丢弃就会把点开始之前的旧帧也记进来；
 * - 墙钟时间：`TrafficEvent` 只带单调时钟 `monotonicNs`（= `System.nanoTime()`），采集时用
 *   `wallMs = now − (nanoTime − monotonicNs)/1e6` 换算并存下来（设备休眠后再换算会差一段休眠时间，所以必须当场换）；
 * - 容量：[maxFrames] 帧或 [maxBytes] 字节先到者为准，记满后**停止并进入 [RecorderState.FULL]**（不静默丢旧帧）；
 * - 换会话（`SessionState.Running.epoch` 变化）：保留已记录帧，插一条分隔标记，不自动清、不自动停。
 */
class TrafficRecorder(
    private val session: SessionManager,
    private val scope: CoroutineScope,
    private val clock: RecorderClock = SystemRecorderClock,
    val maxFrames: Int = DEFAULT_MAX_FRAMES,
    val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val lock = Any()
    private val items = ArrayList<RecordedItem>()
    private var nextSeq = 0L
    private var frameCount = 0
    private var byteCount = 0L
    private var minNs = Long.MIN_VALUE
    private var lastEpoch: Long? = null
    private var collectJob: Job? = null
    private var epochJob: Job? = null

    private val _state = MutableStateFlow(RecorderState.IDLE)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    /** 每次内容变化 +1（`StateFlow` 会合并），界面据此刷新。 */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    /** 记录起点的单调时钟（第一次点开始的那一刻）；用于“相对时间”。 */
    @Volatile
    var originNs: Long? = null
        private set

    /** 每次“清除”+1：界面据此丢弃按下标缓存的解码结果。 */
    @Volatile
    var generation: Int = 0
        private set

    val size: Int get() = synchronized(lock) { items.size }
    val frames: Int get() = synchronized(lock) { frameCount }
    val bytes: Long get() = synchronized(lock) { byteCount }

    fun get(index: Int): RecordedItem = synchronized(lock) { items[index] }

    fun slice(from: Int, to: Int): List<RecordedItem> = synchronized(lock) {
        items.subList(from.coerceIn(0, items.size), to.coerceIn(0, items.size)).toList()
    }

    fun snapshot(): List<RecordedItem> = synchronized(lock) { items.toList() }

    /** 开始 / 继续记录。记满时不能继续（要先清除）。 */
    fun start() {
        synchronized(lock) {
            if (_state.value == RecorderState.RECORDING || _state.value == RecorderState.FULL) return
            minNs = clock.nanoTime()
            if (originNs == null) originNs = minNs
            _state.value = RecorderState.RECORDING
            collectJob?.cancel()
            collectJob = scope.launch { session.traffic.collect { onEvent(it) } }
            if (epochJob == null) {
                epochJob = scope.launch {
                    session.state.collect { state ->
                        if (state is SessionState.Running) onEpoch(state.epoch)
                    }
                }
            }
        }
    }

    /** 暂停记录：停止捕获，已有帧保留。 */
    fun pause() {
        synchronized(lock) {
            if (_state.value != RecorderState.RECORDING) return
            collectJob?.cancel()
            collectJob = null
            _state.value = RecorderState.PAUSED
        }
    }

    /** 清除全部已记录帧；记录 / 暂停状态不变；记满状态清除后回到暂停（可再“继续”）。 */
    fun clear() {
        synchronized(lock) {
            items.clear()
            generation++
            frameCount = 0
            byteCount = 0
            originNs = if (_state.value == RecorderState.RECORDING) clock.nanoTime() else null
            if (_state.value == RecorderState.FULL) _state.value = RecorderState.PAUSED
            _version.value++
        }
    }

    private fun onEpoch(epoch: Long) {
        synchronized(lock) {
            val previous = lastEpoch
            lastEpoch = epoch
            if (previous != null && previous != epoch && frameCount > 0 && _state.value != RecorderState.IDLE) {
                items += RecordedItem(nextSeq++, clock.wallMs(), null, epoch)
                _version.value++
            }
        }
    }

    private fun onEvent(event: TrafficEvent) {
        synchronized(lock) {
            if (_state.value != RecorderState.RECORDING) return
            if (event.monotonicNs < minNs) return // replay 里点“开始”之前的旧帧
            val wall = clock.wallMs() - (clock.nanoTime() - event.monotonicNs) / 1_000_000L
            val cost = event.bytes.size + ITEM_OVERHEAD
            if (frameCount >= maxFrames || byteCount + cost > maxBytes) {
                collectJob?.cancel()
                collectJob = null
                _state.value = RecorderState.FULL
                _version.value++
                return
            }
            items += RecordedItem(nextSeq++, wall, event, lastEpoch ?: 0L)
            frameCount++
            byteCount += cost
            _version.value++
            if (frameCount >= maxFrames || byteCount + ITEM_OVERHEAD > maxBytes) {
                collectJob?.cancel()
                collectJob = null
                _state.value = RecorderState.FULL
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_FRAMES = 20_000
        const val DEFAULT_MAX_BYTES = 8L * 1024 * 1024
        /** 每帧的对象开销估计（事件对象、HEX 字符串、列表槽），用来估算内存。 */
        const val ITEM_OVERHEAD = 200
    }
}
