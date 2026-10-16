package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.panel.PanelRuntime
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectEdits
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectLoad
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectSave
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

enum class EditError { BLANK_NAME, SAVE_FAILED, READ_ONLY }

/**
 * 编辑模式的草稿：所有修改都先改 [draft]，点“完成”才写盘，点 ✕ 放弃（有修改会先确认）。
 * 编辑模式不发任何写入请求（面板现在也没有写入类组件）。
 */
data class EditState(
    val draft: Project,
    val panelId: String,
    val dirty: Boolean,
    val saving: Boolean = false,
    val error: EditError? = null,
)

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

    val paused: StateFlow<Boolean> = runtime?.scheduler?.paused ?: MutableStateFlow(false)

    private val _selectedPanelId = MutableStateFlow<String?>(null)

    /** 当前选中的面板 id；还没选或已不存在时由界面回落到第一个面板。 */
    val selectedPanelId: StateFlow<String?> = _selectedPanelId.asStateFlow()

    fun selectPanel(id: String) {
        _selectedPanelId.value = id
    }

    fun togglePause() {
        runtime?.scheduler?.setPaused(!paused.value)
    }

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

    // ---- 编辑模式 ----

    private val _edit = MutableStateFlow<EditState?>(null)
    val edit: StateFlow<EditState?> = _edit.asStateFlow()
    private var editOriginal: Project? = null

    /** 能不能进编辑模式：有存储位置、文件可读写（新版本写的文件只读）。 */
    val canEdit: Boolean get() = repository != null

    fun startEdit(currentPanelId: String?) {
        val ready = _state.value as? PanelUiState.Ready ?: return
        if (ready.readOnly || repository == null) return
        val project = ready.project
        val panelId = project.panels.firstOrNull { it.id == currentPanelId }?.id ?: project.panels.firstOrNull()?.id
        editOriginal = project
        if (panelId == null) {
            // 空工程：先建一个面板，才有东西可以改名 / 调列数
            val (withPanel, id) = ProjectEdits.addPanel(project)
            _edit.value = EditState(withPanel, id, dirty = true)
        } else {
            _edit.value = EditState(project, panelId, dirty = false)
        }
    }

    fun cancelEdit() {
        _edit.value = null
        editOriginal = null
    }

    private fun change(transform: (Project, String) -> Project) {
        val e = _edit.value ?: return
        if (e.saving) return
        val next = transform(e.draft, e.panelId)
        _edit.value = e.copy(draft = next, dirty = next != editOriginal, error = null)
    }

    fun rename(name: String) = change { p, id -> ProjectEdits.renamePanel(p, id, name) }
    fun setColumns(columns: Int) = change { p, id -> ProjectEdits.setColumns(p, id, columns) }
    fun moveWidget(widgetId: String, targetId: String) = change { p, id -> ProjectEdits.moveWidget(p, id, widgetId, targetId) }
    fun setSpan(widgetId: String, columns: Int, rows: Int) = change { p, id -> ProjectEdits.setSpan(p, id, widgetId, columns, rows) }
    fun deleteWidget(widgetId: String) = change { p, id -> ProjectEdits.deleteWidget(p, id, widgetId) }

    fun selectEditPanel(id: String) {
        val e = _edit.value ?: return
        if (e.draft.panels.any { it.id == id }) _edit.value = e.copy(panelId = id)
    }

    fun addPanel() {
        val e = _edit.value ?: return
        if (e.saving) return
        val (next, id) = ProjectEdits.addPanel(e.draft)
        _edit.value = e.copy(draft = next, panelId = id, dirty = true, error = null)
    }

    fun deletePanel() {
        val e = _edit.value ?: return
        if (e.saving) return
        val next = ProjectEdits.deletePanel(e.draft, e.panelId)
        if (next == e.draft) return
        val panelId = next.panels.first().id
        _edit.value = e.copy(draft = next, panelId = panelId, dirty = next != editOriginal, error = null)
    }

    fun save() {
        val e = _edit.value ?: return
        if (e.saving || repository == null) return
        if (e.draft.panels.any { !ProjectEdits.isValidName(it.name) }) {
            _edit.value = e.copy(error = EditError.BLANK_NAME)
            return
        }
        if (!e.dirty) {
            selectPanel(e.panelId)
            cancelEdit()
            return
        }
        _edit.value = e.copy(saving = true, error = null)
        viewModelScope.launch {
            val result = withContext(io) { repository.save(e.draft) }
            val current = _edit.value ?: return@launch
            when (result) {
                ProjectSave.Saved -> {
                    val warnings = (_state.value as? PanelUiState.Ready)?.warnings.orEmpty()
                    _state.value = PanelUiState.Ready(e.draft, warnings, readOnly = false)
                    runtime?.scheduler?.setPoints(e.draft.usablePoints)
                    _selectedPanelId.value = current.panelId
                    cancelEdit()
                }
                ProjectSave.ReadOnly -> _edit.value = current.copy(saving = false, error = EditError.READ_ONLY)
                is ProjectSave.Failed -> _edit.value = current.copy(saving = false, error = EditError.SAVE_FAILED)
            }
        }
    }
}
