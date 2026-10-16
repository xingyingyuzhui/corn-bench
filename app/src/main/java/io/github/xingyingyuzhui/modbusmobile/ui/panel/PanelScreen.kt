package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.layout.Arrangement
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.ErrorKind
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdLevel
import io.github.xingyingyuzhui.modbusmobile.ui.LocalPanelRuntime
import io.github.xingyingyuzhui.modbusmobile.ui.LocalProjectRepository
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
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
    // 页面在前台（STARTED 以上）才轮询；离开页面 / 退到后台立即停。
    LifecycleStartEffect(model) {
        model.setActive(true)
        onStopOrDispose { model.setActive(false) }
    }
    PanelContent(state, snapshots, stats)
}

@Composable
fun PanelContent(
    state: PanelUiState,
    snapshots: Map<String, PointSnapshot> = emptyMap(),
    stats: PollStats? = null,
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val dock = LocalDockInsets.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = pageStart(), end = spacing.page)) {
            if (landscape) {
                // 稿面横屏：页头一行（面板 16/700），没有会话条，把高度还给卡片区（b4-3#1）。
                Text(
                    stringResource(R.string.title_panel),
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 2.dp),
                )
            } else {
                PageHeader(stringResource(R.string.title_panel))
                SessionStrip(Modifier.padding(bottom = spacing.gapS))
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
                    val widgets = state.project.panels.flatMap { p -> p.widgets.filter { it.isUsable } }
                    if (widgets.isEmpty()) {
                        NoticeCard(
                            title = stringResource(R.string.panel_empty_title),
                            body = stringResource(R.string.panel_empty_body),
                            tag = "panel-empty",
                        )
                    } else {
                        Text(
                            if (stats == null) {
                                stringResource(R.string.panel_poll_idle)
                            } else if (stats.actualMs == null) {
                                stringResource(R.string.panel_poll_running, stats.configuredMs)
                            } else {
                                stringResource(R.string.panel_poll_running_actual, stats.configuredMs, stats.actualMs)
                            },
                            color = colors.muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 2.dp).testTag("panel-poll-stats"),
                        )
                        widgets.forEach { w -> PointRow(w.title.ifBlank { w.point!!.name }, w.point!!.unitLabel, snapshots[w.point.id], w.id) }
                    }
                }
            }
        }
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

/** P3 的临时行：名称 + 最新值 + 质量说明。P4 换成真正的卡片网格。 */
@Composable
private fun PointRow(title: String, unit: String, snapshot: PointSnapshot?, widgetId: String) {
    val colors = LocalModbusColors.current
    val quality = snapshot?.quality ?: Quality.NO_DATA
    val valueText = when {
        snapshot == null || snapshot.raw == null -> "—"
        snapshot.text != null -> snapshot.text
        else -> if (snapshot.engineering == 1.0) stringResource(R.string.panel_flag_on) else stringResource(R.string.panel_flag_off)
    }
    val valueColor = when {
        quality == Quality.OK -> when (snapshot?.level) {
            ThresholdLevel.ALERT -> colors.error
            ThresholdLevel.WARN -> colors.txText
            else -> colors.text
        }
        else -> colors.dim
    }
    val note = when (quality) {
        Quality.NO_DATA -> stringResource(R.string.panel_q_no_data)
        Quality.OK -> null
        Quality.STALE -> stringResource(R.string.panel_q_stale)
        Quality.TIMEOUT -> stringResource(R.string.panel_q_timeout, snapshot?.failures ?: 0)
        Quality.ERROR -> when (snapshot?.error?.kind) {
            ErrorKind.EXCEPTION -> stringResource(R.string.panel_q_exception, snapshot.error.code ?: 0)
            ErrorKind.BAD_VALUE -> stringResource(R.string.panel_q_bad_value)
            else -> stringResource(R.string.panel_q_invalid)
        }
    }
    ModbusCard(
        Modifier.fillMaxWidth().testTag("panel-row-$widgetId"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, color = colors.muted, fontSize = 12.5.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(valueText, style = Mono.copy(fontSize = 24.sp), color = valueColor, modifier = Modifier.testTag("panel-value-$widgetId"))
            if (unit.isNotBlank()) Text(unit, color = colors.dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (note != null) Text(note, color = colors.muted, fontSize = 12.sp, modifier = Modifier.testTag("panel-note-$widgetId"))
    }
}
