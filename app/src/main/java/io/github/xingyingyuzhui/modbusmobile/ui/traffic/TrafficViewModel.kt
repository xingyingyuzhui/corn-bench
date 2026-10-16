package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 一条日志。[seq] 是本 ViewModel 给的递增序号：列表只留最近 [TrafficViewModel.CAP] 条，
 * 选中项和滚动位置都按它认，不按下标（下标会随老条目被挤掉而漂移）。
 * [offsetMs] = 相对本次会话第一条报文的毫秒数；起点固定，不会因为老条目被挤掉而变化。
 */
class LogEntry(
    val seq: Long,
    val event: TrafficEvent,
    val info: FrameInfo,
    val offsetMs: Long,
)

class TrafficViewModel(session: SessionManager) : ViewModel() {
    private val history = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = history.asStateFlow()

    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    /** 暂停滚动：只是不再自动跟到最新一条，新报文照常进列表。 */
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _selected = MutableStateFlow<Long?>(null)
    val selected: StateFlow<Long?> = _selected.asStateFlow()

    private var originNs: Long? = null
    private var nextSeq = 0L
    private var epoch: Long? = null

    init {
        viewModelScope.launch {
            session.state.collect { state ->
                if (state is SessionState.Running) {
                    if (epoch != null && epoch != state.epoch) reset()
                    epoch = state.epoch
                }
            }
        }
        viewModelScope.launch {
            session.traffic.collect { event -> append(event) }
        }
    }

    fun setFilter(value: LogFilter) {
        _filter.value = value
    }

    fun togglePaused() {
        _paused.update { !it }
    }

    fun select(seq: Long?) {
        _selected.value = seq
    }

    fun clear() = reset()

    private fun reset() {
        history.value = emptyList()
        originNs = null
        _selected.value = null
    }

    private fun append(event: TrafficEvent) {
        val origin = originNs ?: event.monotonicNs.also { originNs = it }
        val entry = LogEntry(
            seq = nextSeq++,
            event = event,
            info = describeFrame(event.bytes, event.kind),
            offsetMs = (event.monotonicNs - origin) / 1_000_000L,
        )
        history.update { (it + entry).takeLast(CAP) }
    }

    companion object {
        const val CAP = 1_000
    }
}
