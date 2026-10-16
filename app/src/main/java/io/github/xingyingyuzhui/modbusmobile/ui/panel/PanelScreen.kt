package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalPanelRuntime
import io.github.xingyingyuzhui.modbusmobile.ui.LocalProjectRepository
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChipRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.HeaderIconButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.PencilIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.components.SmallButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.ToneBadge
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.pageStart
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing

@Composable
fun PanelScreen() {
    val repository = LocalProjectRepository.current
    val runtime = LocalPanelRuntime.current
    val model = viewModel { PanelViewModel(repository, runtime) }
    val state by model.state.collectAsStateWithLifecycle()
    val snapshots by model.snapshots.collectAsStateWithLifecycle()
    val stats by model.stats.collectAsStateWithLifecycle()
    val paused by model.paused.collectAsStateWithLifecycle()
    val selected by model.selectedPanelId.collectAsStateWithLifecycle()
    // 页面在前台（STARTED 以上）才轮询；离开页面 / 退到后台立即停。
    LifecycleStartEffect(model) {
        model.setActive(true)
        onStopOrDispose { model.setActive(false) }
    }
    val edit by model.edit.collectAsStateWithLifecycle()
    val editing = edit
    if (editing != null) {
        PanelEditor(
            edit = editing,
            snapshots = snapshots,
            actions = EditActions(
                onRename = model::rename,
                onColumns = model::setColumns,
                onMove = model::moveWidget,
                onSpan = model::setSpan,
                onDelete = model::deleteWidget,
                onSelectPanel = model::selectEditPanel,
                onAddPanel = model::addPanel,
                onDeletePanel = model::deletePanel,
                onDone = model::save,
                onCancel = model::cancelEdit,
            ),
        )
    } else {
        PanelContent(
            state = state,
            snapshots = snapshots,
            stats = stats,
            paused = paused,
            selectedPanelId = selected,
            onSelectPanel = model::selectPanel,
            onTogglePause = model::togglePause,
            onEdit = if (model.canEdit) ({ model.startEdit(selected) }) else null,
        )
    }
}

/**
 * 面板页（稿面 b2-1 / b4-3）。竖屏：页头 + 会话条（主站 · 轮询中 / 设定 · 实际 + 暂停）+ 面板 chip + 2 列网格；
 * 横屏：一行页头（面板 16/700 + chip xs + 轮询 badge + 暂停）+ 4 列网格，无会话条。
 * ✎ 进入编辑模式（P5，见 PanelEditScreen）；⤢ 全屏曲线、＋ 添加组件在后续 PR，没有功能的入口不画。
 */
@Composable
fun PanelContent(
    state: PanelUiState,
    snapshots: Map<String, PointSnapshot> = emptyMap(),
    stats: PollStats? = null,
    paused: Boolean = false,
    selectedPanelId: String? = null,
    onSelectPanel: (String) -> Unit = {},
    onTogglePause: () -> Unit = {},
    /** 为 null 时不画 ✎（没有仓库 / 工程文件只读 / 还没有面板）。 */
    onEdit: (() -> Unit)? = null,
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val dock = LocalDockInsets.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    val sessionState by LocalSession.current.state.collectAsStateWithLifecycle()
    val masterRunning = (sessionState as? SessionState.Running)?.role == Role.MASTER

    val ready = state as? PanelUiState.Ready
    val panels = ready?.project?.panels.orEmpty()
    val current: Panel? = panels.firstOrNull { it.id == selectedPanelId } ?: panels.firstOrNull()
    val widgets = current?.widgets?.filter { it.isUsable }.orEmpty()
    val anyWidgets = panels.any { p -> p.widgets.any { it.isUsable } }
    val columns = if (landscape) 4 else (current?.columns ?: 2)
    val editEntry: (() -> Unit)? = if (ready != null && !ready.readOnly && panels.isNotEmpty()) onEdit else null

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = pageStart(), end = spacing.page)) {
            if (landscape) {
                LandscapeHeader(panels, current, stats, paused, masterRunning && anyWidgets, onSelectPanel, onTogglePause, editEntry)
            } else {
                PageHeader(stringResource(R.string.title_panel)) {
                    if (editEntry != null) EditEntryButton(editEntry)
                }
                val polling = masterRunning && anyWidgets && stats != null && !paused
                val title = when {
                    masterRunning && anyWidgets && paused -> stringResource(R.string.panel_strip_paused)
                    polling -> stringResource(R.string.panel_strip_polling)
                    else -> null
                }
                val detail = when {
                    masterRunning && anyWidgets && paused -> stringResource(R.string.panel_strip_paused_detail)
                    polling && stats != null -> stringResource(
                        R.string.panel_strip_detail,
                        stats.configuredMs,
                        stats.actualMs?.let { stringResource(R.string.panel_strip_actual, it) } ?: stringResource(R.string.panel_strip_actual_none),
                    )
                    else -> null
                }
                SessionStrip(
                    Modifier.padding(bottom = spacing.gapS).testTag("panel-strip"),
                    title = title,
                    detail = detail,
                    trailing = if (masterRunning && anyWidgets) {
                        { PauseButton(paused, onTogglePause) }
                    } else {
                        null
                    },
                )
                if (panels.size > 1) {
                    PanelChips(panels, current, false, onSelectPanel, Modifier.padding(bottom = spacing.gapS))
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = pageStart(), end = spacing.page, bottom = dock.bottom + spacing.gapS),
            verticalArrangement = Arrangement.spacedBy(spacing.gapS),
        ) {
            when (state) {
                PanelUiState.Loading -> Unit
                is PanelUiState.Corrupt -> NoticeCard(
                    title = stringResource(R.string.panel_corrupt_title),
                    body = stringResource(
                        if (state.copySaved) R.string.panel_corrupt_body_saved else R.string.panel_corrupt_body,
                        state.reason,
                    ),
                    tag = "panel-corrupt",
                )
                is PanelUiState.Ready -> {
                    state.warnings.forEach { NoticeCard(title = null, body = it, tag = "panel-warning") }
                    if (!anyWidgets) {
                        NoticeCard(
                            title = stringResource(R.string.panel_empty_title),
                            body = stringResource(R.string.panel_empty_body),
                            tag = "panel-empty",
                        )
                    } else {
                        if (stats == null && !paused) {
                            Text(
                                stringResource(R.string.panel_poll_idle),
                                color = colors.muted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(start = 2.dp).testTag("panel-poll-hint"),
                            )
                        }
                        PanelGrid(
                            columns = columns,
                            spans = widgets.map { it.spanColumns },
                            spanRows = widgets.map { it.spanRows },
                            modifier = Modifier.fillMaxWidth().testTag("panel-grid"),
                            gap = spacing.gapS,
                        ) { index ->
                            val widget: Widget = widgets[index]
                            WidgetCard(widget, snapshots[widget.point!!.id])
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseButton(paused: Boolean, onToggle: () -> Unit) {
    SmallButton(
        stringResource(if (paused) R.string.panel_resume else R.string.panel_pause),
        onToggle,
        Modifier.testTag("panel-pause"),
    )
}

@Composable
private fun PanelChips(
    panels: List<Panel>,
    current: Panel?,
    xs: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChipRow(
        options = panels,
        isSelected = { it.id == current?.id },
        label = { it.name },
        onToggle = { onSelect(it.id) },
        modifier = modifier.fillMaxWidth().testTag("panel-chips"),
        scroll = true,
        xs = xs,
    )
}

/** b4-3：面板 16/700 + 面板 chip(xs) + 轮询 badge + 暂停；一行，没有会话条。 */
@Composable
private fun LandscapeHeader(
    panels: List<Panel>,
    current: Panel?,
    stats: PollStats?,
    paused: Boolean,
    canPause: Boolean,
    onSelect: (String) -> Unit,
    onTogglePause: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    val colors = LocalModbusColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.title_panel),
            color = colors.text,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp),
        )
        Row(Modifier.weight(1f)) {
            if (panels.size > 1) PanelChips(panels, current, true, onSelect)
        }
        when {
            paused && canPause -> ToneBadge(stringResource(R.string.panel_land_paused), colors.dropped, Modifier.testTag("panel-badge"))
            stats != null && canPause -> ToneBadge(
                stringResource(R.string.panel_land_polling, (stats.actualMs ?: stats.configuredMs).toInt()),
                colors.rxLocal,
                Modifier.testTag("panel-badge"),
            )
            canPause -> ToneBadge(stringResource(R.string.panel_land_idle), colors.dropped, Modifier.testTag("panel-badge"))
        }
        if (canPause) PauseButton(paused, onTogglePause)
        if (onEdit != null) EditEntryButton(onEdit)
    }
}

@Composable
private fun EditEntryButton(onEdit: () -> Unit) {
    HeaderIconButton(stringResource(R.string.panel_edit_open), onEdit, Modifier.testTag("panel-edit")) {
        PencilIcon(LocalModbusColors.current.muted, size = 16.dp)
    }
}

@Composable
private fun NoticeCard(title: String?, body: String, tag: String) {
    val colors = LocalModbusColors.current
    ModbusCard(
        Modifier.fillMaxWidth().testTag(tag),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (title != null) {
            Text(title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(body, color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
