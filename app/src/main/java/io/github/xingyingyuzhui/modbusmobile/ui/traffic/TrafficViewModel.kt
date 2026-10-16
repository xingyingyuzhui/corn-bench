package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    // 按记录器下标对齐的解码缓存；记录器清除（generation 变）时整体丢弃。
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
        sync()
        return recorder.snapshot().mapIndexed { index, item -> toLogItem(index, item) }
    }

    private fun sync() {
        synchronized(infos) {
            if (generation != recorder.generation) {
                generation = recorder.generation
                infos.clear()
            }
            val size = recorder.size
            while (infos.size < size) {
                val item = recorder.get(infos.size)
                infos += item.event?.let { describeFrame(it.bytes, it.kind) }
            }
        }
    }

    private fun toLogItem(index: Int, item: io.github.xingyingyuzhui.modbusmobile.application.RecordedItem): LogItem {
        val event = item.event ?: return LogMarker(item.seq, item.wallMs, item.epoch)
        val origin = recorder.originNs ?: event.monotonicNs
        return LogEntry(
            seq = item.seq,
            event = event,
            info = synchronized(infos) { infos[index] } ?: describeFrame(event.bytes, event.kind),
            wallMs = item.wallMs,
            offsetMs = ((event.monotonicNs - origin) / 1_000_000L).coerceAtLeast(0),
        )
    }

    private fun refresh() {
        sync()
        val total = recorder.size
        val filter = _filter.value
        val indices = ArrayList<Int>()
        for (i in 0 until total) {
            val item = recorder.get(i)
            val event = item.event
            if (event == null) {
                indices += i // 分隔标记不被过滤掉
            } else {
                val info = synchronized(infos) { infos[i] }
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
        val items = (from until to).map { toLogItem(indices[it], recorder.get(indices[it])) }
        _ui.value = LogUiState(
            items = items,
            recorder = recorder.state.value,
            recorded = recorder.frames,
            matching = count,
            page = page,
            pageCount = pageCount,
            pageSize = pageSize,
            following = following && recorder.state.value == RecorderState.RECORDING,
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}
