package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import io.github.xingyingyuzhui.modbusmobile.application.FrameInfo
import io.github.xingyingyuzhui.modbusmobile.application.describeFrame
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.RecordedItem
import io.github.xingyingyuzhui.modbusmobile.application.RecorderState
import io.github.xingyingyuzhui.modbusmobile.application.TrafficRecorder
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 日志列表里的一项：一帧（[LogEntry]）或“新会话”分隔（[LogMarker]）。[seq] 由记录器给出，选中项按它认，不按下标。 */
sealed interface LogItem {
    val seq: Long
    val wallMs: Long
}

/**
 * 一条日志帧。[wallMs] = 采集时换算的墙钟毫秒时间戳；[offsetMs] = 相对本次记录起点的毫秒数。
 */
class LogEntry(
    override val seq: Long,
    val event: TrafficEvent,
    val info: FrameInfo,
    override val wallMs: Long,
    val offsetMs: Long,
) : LogItem

class LogMarker(override val seq: Long, override val wallMs: Long, val epoch: Long) : LogItem

/** 界面要的全部状态（一次算好，翻页 / 过滤 / 新帧都走这里）。 */
class LogUiState(
    val items: List<LogItem> = emptyList(),
    val recorder: RecorderState = RecorderState.IDLE,
    /** 已记录的帧数（不含分隔标记，不受过滤影响）。 */
    val recorded: Int = 0,
    /** 过滤后的项数。 */
    val matching: Int = 0,
    val page: Int = 0,
    val pageCount: Int = 1,
    val pageSize: Int = TrafficViewModel.DEFAULT_PAGE_SIZE,
    /** 在最后一页且记录中：自动跟到最新。 */
    val following: Boolean = true,
)

/**
 * 日志页的 ViewModel：不再自己收 `session.traffic`，而是读应用级的 [TrafficRecorder]（默认不记录、点开始才记录）。
 * 在这里做：过滤、分页、选中、“锁定滚动”、按下标缓存解码结果（只解码一次）。
 */
class TrafficViewModel(
    private val recorder: TrafficRecorder,
    pageSize: Int = DEFAULT_PAGE_SIZE,
) : ViewModel() {
    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    /** 锁定滚动：列表不再自动跟到最新一帧；**不影响记录**（暂停记录 ≠ 锁定滚动）。 */
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _selected = MutableStateFlow<Long?>(null)
    val selected: StateFlow<Long?> = _selected.asStateFlow()

    private val _ui = MutableStateFlow(LogUiState(pageSize = pageSize))
    val ui: StateFlow<LogUiState> = _ui.asStateFlow()

    val recorderState: StateFlow<RecorderState> get() = recorder.state

    private var pageSize = pageSize
    /** null = 跟随最新（最后一页）。 */
    private var pinnedPage: Int? = null

    // 按记录器下标对齐的解码缓存；记录器清除（generation 变）时整体丢弃。只在 takeSnapshot() 里读写。
    private val infos = ArrayList<FrameInfo?>()
    private var generation = recorder.generation
    private var matchingIndices: List<Int> = emptyList()

    init {
        viewModelScope.launch {
            recorder.version.collect { refresh() }
        }
        viewModelScope.launch {
            recorder.state.collect { refresh() }
        }
    }

    fun start() = recorder.start()

    fun pauseRecording() = recorder.pause()

    fun clear() {
        recorder.clear()
        _selected.value = null
        pinnedPage = null
        refresh()
    }

    fun setFilter(value: LogFilter) {
        _filter.value = value
        pinnedPage = null
        refresh()
    }

    fun toggleLocked() {
        _locked.update { !it }
    }

    fun select(seq: Long?) {
        _selected.value = seq
    }

    fun setPageSize(value: Int) {
        pageSize = value.coerceIn(10, 1000)
        pinnedPage = null
        refresh()
    }

    fun goToPage(page: Int) {
        val count = _ui.value.pageCount
        val target = page.coerceIn(0, count - 1)
        // 跳到最后一页 = 回到“跟随最新”
        pinnedPage = if (target == count - 1) null else target
        refresh()
    }

    fun nextPage() = goToPage(_ui.value.page + 1)

    fun previousPage() = goToPage(_ui.value.page - 1)

    fun lastPage() = goToPage(Int.MAX_VALUE)

    /** 导出用：全部已记录项（不受过滤和分页影响），按时间顺序。 */
    fun exportItems(): List<LogItem> {
        val snap = takeSnapshot()
        return snap.items.indices.map { toLogItem(snap.items[it], snap.infos[it]) }
    }

    /**
     * 同一时刻的一份记录：[items] 是记录器的拷贝，[infos] 与它按下标一一对应（长度相同）。
     * 记录器在后台协程里持续追加（TrafficRecorder.onEvent），所以界面只能基于这一份快照算，
     * 不能再分两次读 `recorder.size` / `recorder.get(i)`（T-20261018-qa-01：两次读之间进了新帧就越界）。
     */
    private class Snapshot(val items: List<RecordedItem>, val infos: List<FrameInfo?>)

    private fun takeSnapshot(): Snapshot {
        var result: Snapshot? = null
        while (result == null) {
            // generation 在记录器锁内和 items.clear() 一起变；前后两次读到同一个值，说明 items 属于这一代。
            val gen = recorder.generation
            val items = recorder.snapshot()
            if (recorder.generation != gen) continue // 拷贝期间被清除了：用新的一代重来
            result = synchronized(infos) {
                if (generation != gen || infos.size > items.size) {
                    generation = gen
                    infos.clear()
                }
                while (infos.size < items.size) {
                    val item = items[infos.size]
                    infos += item.event?.let { describeFrame(it.bytes, it.kind) }
                }
                Snapshot(items, ArrayList(infos.subList(0, items.size)))
            }
        }
        return checkNotNull(result)
    }

    private fun toLogItem(item: RecordedItem, cached: FrameInfo?): LogItem {
        val event = item.event ?: return LogMarker(item.seq, item.wallMs, item.epoch)
        val origin = recorder.originNs ?: event.monotonicNs
        return LogEntry(
            seq = item.seq,
            event = event,
            info = cached ?: describeFrame(event.bytes, event.kind),
            wallMs = item.wallMs,
            offsetMs = ((event.monotonicNs - origin) / 1_000_000L).coerceAtLeast(0),
        )
    }

    private fun refresh() {
        // 整页只基于一次快照：循环、过滤、分页、toLogItem 都只用 snap，上限取两者较小值。
        val snap = takeSnapshot()
        val records = snap.items
        val infos = snap.infos
        val total = minOf(records.size, infos.size)
        val filter = _filter.value
        val indices = ArrayList<Int>()
        var recorded = 0
        for (i in 0 until total) {
            val event = records[i].event
            if (event == null) {
                indices += i // 分隔标记不被过滤掉
            } else {
                recorded++
                val info = infos[i]
                if (info != null && filter.accepts(event.kind, info)) indices += i
            }
        }
        matchingIndices = indices
        val count = indices.size
        val pageCount = maxOf(1, (count + pageSize - 1) / pageSize)
        val following = pinnedPage == null
        val page = (pinnedPage ?: (pageCount - 1)).coerceIn(0, pageCount - 1)
        val from = page * pageSize
        val to = minOf(count, from + pageSize)
        val items = (from until to).map { indices[it].let { index -> toLogItem(records[index], infos[index]) } }
        val state = recorder.state.value
        _ui.value = LogUiState(
            items = items,
            recorder = state,
            // 与 items 同一份快照（分隔标记不算帧），不再单独读 recorder.frames
            recorded = recorded,
            matching = count,
            page = page,
            pageCount = pageCount,
            pageSize = pageSize,
            following = following && state == RecorderState.RECORDING,
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}
