package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.panel.PanelRuntime
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectLoad
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PanelUiState {
    data object Loading : PanelUiState

    /** [warnings] 是读文件时的提醒（忽略 / 保留了读不懂的组件、用了备份、只读等）。 */
    data class Ready(val project: Project, val warnings: List<String>, val readOnly: Boolean) : PanelUiState

    data class Corrupt(val reason: String, val copySaved: Boolean) : PanelUiState
}

/** 面板页状态。P3：读出工程文件，把点位交给轮询调度器，显示每个点位的最新值和质量；编辑在后续 PR。[repository] 为空（截图 / 单测）按空工程处理。 */
class PanelViewModel(
    private val repository: ProjectRepository?,
    private val runtime: PanelRuntime? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow<PanelUiState>(PanelUiState.Loading)
    val state: StateFlow<PanelUiState> = _state.asStateFlow()
    val snapshots: StateFlow<Map<String, PointSnapshot>> = runtime?.store?.snapshots ?: MutableStateFlow(emptyMap())
    val stats: StateFlow<PollStats?> = runtime?.scheduler?.stats ?: MutableStateFlow(null)

    /** 页面可见（且应用在前台）时才轮询。 */
    fun setActive(value: Boolean) {
        runtime?.scheduler?.setActive(value)
    }

    init {
        viewModelScope.launch {
            val result = if (repository == null) ProjectLoad.Loaded(Project.empty()) else withContext(io) { repository.load() }
            _state.value = when (result) {
                is ProjectLoad.Loaded -> {
                    runtime?.scheduler?.setPoints(result.project.usablePoints)
                    PanelUiState.Ready(result.project, result.warnings, result.readOnly)
                }
                is ProjectLoad.Corrupt -> PanelUiState.Corrupt(result.reason, result.corruptCopy != null)
            }
        }
    }
}
