package io.github.xingyingyuzhui.modbusmobile.ui.curve

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveConfig
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveMath
import io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory
import io.github.xingyingyuzhui.modbusmobile.application.panel.ValueCodec
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalPanelRuntime
import io.github.xingyingyuzhui.modbusmobile.ui.LocalProjectRepository
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.CloseIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.HeaderIconButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.PauseIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.PlayIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.SelectChip
import io.github.xingyingyuzhui.modbusmobile.ui.components.SmallButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.ToneBadge
import io.github.xingyingyuzhui.modbusmobile.ui.panel.CURVE_OWNER
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelUiState
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelViewModel
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

/** 全屏页头里那个小绿徽标：轮询状态（和面板会话条同一套判断）。 */
enum class CurveStatus { POLLING, PAUSED, IDLE }

/**
 * 曲线全屏（稿面 b4-1）：页头（标题 + 轮询徽标 + 时间窗 1 分 / 10 分 / 1 时 + ❚❚ + ✕）、
 * 绘图卡（4 条网格线、双 Y 轴刻度、时间轴、缺测斜纹 + “超时 N s”、游标）、右侧 150 宽读数（名称 / 当前值 / 最小·最大）+ 快照。
 * [widgetId] 指向面板里的一个曲线组件；找不到（被删了 / 配置不可用）就只说明原因，不画空图。
 * 页面在前台时自己向轮询调度器“报到”（[CURVE_OWNER]），所以从面板进来后不会停轮询。
 */
@Composable
fun CurveFullscreenScreen(owner: ViewModelStoreOwner, widgetId: String?, onClose: () -> Unit) {
    val repository = LocalProjectRepository.current
    val runtime = LocalPanelRuntime.current
    val session = LocalSession.current
    val model = viewModel<PanelViewModel>(viewModelStoreOwner = owner) { PanelViewModel(repository, runtime, session = session) }
    val state by model.state.collectAsStateWithLifecycle()
    val stats by model.stats.collectAsStateWithLifecycle()
    val pollPaused by model.paused.collectAsStateWithLifecycle()
    val sessionState by LocalSession.current.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(model) {
        model.setActive(true, CURVE_OWNER)
        onStopOrDispose { model.setActive(false, CURVE_OWNER) }
    }
    val widget = (state as? PanelUiState.Ready)?.project?.panels?.flatMap { it.widgets }
        ?.firstOrNull { it.id == widgetId && it.curve != null && it.isUsable }
    val master = (sessionState as? SessionState.Running)?.role == Role.MASTER
    val status = when {
        master && pollPaused -> CurveStatus.PAUSED
        master && stats != null -> CurveStatus.POLLING
        else -> CurveStatus.IDLE
    }
    CurveFullscreenContent(widget, model.history, status, onClose)
}

private val WindowPresets = listOf(60, 600, 3600)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurveFullscreenContent(
    widget: Widget?,
    history: SampleHistory?,
    status: CurveStatus,
    onClose: () -> Unit,
) {
    val colors = LocalModbusColors.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    val config = widget?.curve
    // 时间窗：默认用组件配置的；芯片只给历史里真保留得住的（保留时长不足的不给，免得点了没用）
    var windowSec by rememberSaveable(widget?.id) { mutableStateOf(config?.windowSec ?: 60) }
    var frozenAt by rememberSaveable(widget?.id) { mutableStateOf<Long?>(null) }
    val liveNow = rememberCurveNow()
    val layer = rememberGraphicsLayer()
    val saver = rememberCurveSnapshotSaver(layer)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
            .testTag("curve-fs"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    widget?.displayTitle ?: stringResource(R.string.title_curve),
                    color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 2.dp).testTag("curve-fs-title"),
                )
                if (widget != null) {
                    val (text, tone) = when {
                        frozenAt != null -> stringResource(R.string.curve_fs_frozen) to colors.muted
                        status == CurveStatus.POLLING -> stringResource(R.string.panel_strip_polling_short) to colors.rxLocal
                        status == CurveStatus.PAUSED -> stringResource(R.string.panel_strip_paused_short) to colors.muted
                        else -> stringResource(R.string.curve_fs_idle) to colors.muted
                    }
                    ToneBadge(text, tone, Modifier.testTag("curve-fs-status"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (config != null) {
                    val choices = remember(config) { windowChoices(config) }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        choices.forEach { sec ->
                            Box(Modifier.testTag("curve-fs-win-$sec")) {
                                SelectChip(windowChipText(sec), windowSec == sec, xs = true, minWidth = 48.dp) { windowSec = sec }
                            }
                        }
                    }
                    val freeze = stringResource(if (frozenAt == null) R.string.curve_fs_pause else R.string.curve_fs_resume)
                    HeaderIconButton(freeze, { frozenAt = if (frozenAt == null) liveNow else null }, Modifier.testTag("curve-fs-pause")) {
                        if (frozenAt == null) PauseIcon(colors.text) else PlayIcon(colors.accentText)
                    }
                }
                HeaderIconButton(stringResource(R.string.action_close), onClose, Modifier.testTag("curve-fs-close")) { CloseIcon(colors.text) }
            }
        }
        if (widget == null || config == null) {
            Text(
                stringResource(R.string.curve_fs_missing), color = colors.muted, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp).testTag("curve-fs-missing"),
            )
        } else {
            val shown = remember(config, windowSec) { config.copy(windowSec = windowSec) }
            val now = frozenAt ?: liveNow
            val frame = remember(shown, now, history) {
                CurveMath.frame(shown, now) { id, from -> history?.read(id, from) ?: SampleHistory.Slice.EMPTY }
            }
            val bg = colors.bg
            // 快照的图层只包“图 + 读数”，“快照”按钮在图层外面（不会画进图里）
            val captured = Modifier.drawWithContent {
                layer.record(size = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())) { drawRect(bg); this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
            if (landscape) {
                var legendHeightPx by remember { mutableIntStateOf(0) }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Row(Modifier.fillMaxSize().then(captured), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChartCard(shown, frame, Modifier.weight(1f).fillMaxSize())
                        Column(
                            Modifier.width(150.dp).padding(bottom = SnapshotSlot).verticalScroll(rememberScrollState())
                                .onSizeChanged { legendHeightPx = it.height },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            frame.series.forEachIndexed { i, sf -> SeriesReadout(i, sf, Modifier.fillMaxWidth()) }
                        }
                    }
                    // 稿面：快照紧跟在读数卡下面
                    SnapshotButton(
                        saver,
                        Modifier.align(Alignment.TopEnd).width(150.dp).offset { IntOffset(0, legendHeightPx + 8.dp.roundToPx()) },
                    )
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).fillMaxWidth().then(captured), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChartCard(shown, frame, Modifier.weight(1f).fillMaxWidth())
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            frame.series.forEachIndexed { i, sf -> SeriesReadout(i, sf, Modifier.width(150.dp)) }
                        }
                    }
                    SnapshotButton(saver, Modifier.fillMaxWidth())
                }
            }
        }
    }
    saver.Dialogs()
}

@Composable
private fun SnapshotButton(saver: CurveSnapshotSaver, modifier: Modifier) {
    SmallButton(stringResource(R.string.curve_fs_snapshot), saver::begin, modifier.testTag("curve-fs-snapshot"))
}

/** 横屏读数列底下给“快照”按钮留的高度（48dp 触控 + 间距）。 */
private val SnapshotSlot = 56.dp

@Composable
private fun ChartCard(config: CurveConfig, frame: CurveMath.Frame, modifier: Modifier) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    Box(
        modifier
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        CurveChart(config, frame, Modifier.fillMaxSize(), tag = "curve-fs-chart", detail = true)
    }
}

/** 右侧读数卡：线型 + 名称、当前值 22 号 mono、窗口内最小 / 最大。 */
@Composable
private fun SeriesReadout(index: Int, sf: CurveMath.SeriesFrame, modifier: Modifier) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    val point = sf.series.point
    fun fmt(v: Double?) = v?.let { ValueCodec.format(it, point.conversion.decimals) } ?: "—"
    Column(
        modifier
            .testTag("curve-fs-legend-$index")
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            LineSample(sf.series, colors.isDark)
            Text(point.name, color = colors.muted, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                fmt(sf.latest),
                style = Mono.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline().weight(1f, fill = false).testTag("curve-fs-value-$index"),
            )
            if (point.unitLabel.isNotBlank() && sf.latest != null) {
                Text(point.unitLabel, color = colors.muted, fontSize = 12.sp, modifier = Modifier.alignByBaseline())
            }
        }
        Text(
            stringResource(R.string.curve_fs_minmax, fmt(sf.min), fmt(sf.max)),
            style = Mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp), color = colors.dim, maxLines = 1,
            modifier = Modifier.padding(top = 2.dp).testTag("curve-fs-range-$index"),
        )
    }
}

/** 全屏页的时间窗芯片：组件配置的窗口（如果不是预设）+ 1 分 / 10 分 / 1 时（保留时长够才给）。 */
internal fun windowChoices(config: CurveConfig): List<Int> {
    val retentionSec = config.retentionMinutes * 60
    return (listOf(config.windowSec) + WindowPresets.filter { it <= retentionSec }).distinct().sorted()
}

@Composable
private fun windowChipText(sec: Int): String {
    val (n, unit) = CurveMath.windowLabelParts(sec)
    return stringResource(
        when (unit) {
            'h' -> R.string.curve_fs_w_h
            'm' -> R.string.curve_fs_w_m
            else -> R.string.curve_fs_w_s
        },
        n,
    )
}
