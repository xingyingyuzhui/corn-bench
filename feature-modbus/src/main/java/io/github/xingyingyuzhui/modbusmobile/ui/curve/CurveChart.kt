package io.github.xingyingyuzhui.modbusmobile.ui.curve

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.Role
import io.github.xingyingyuzhui.modbusmobile.ui.components.ExpandIcon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveConfig
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveMath
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveSeries
import io.github.xingyingyuzhui.modbusmobile.application.panel.GapStyle
import io.github.xingyingyuzhui.modbusmobile.application.panel.LegendPosition
import io.github.xingyingyuzhui.modbusmobile.application.panel.LineStyle
import io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory
import io.github.xingyingyuzhui.modbusmobile.application.panel.ValueCodec
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.YAxis
import io.github.xingyingyuzhui.modbusmobile.ui.panel.CardFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.JetBrainsMono
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 墙钟（毫秒）。测试里换成固定值，曲线就是确定的画面。 */
val LocalWallClock = staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }

/** 曲线是否按节拍重绘；截图 / 单测里关掉，避免无限的 delay 循环。 */
val LocalCurveTicker = staticCompositionLocalOf { true }

const val CURVE_TICK_MS = 250L

/** 8 色色板（稿面 b2-6）：橙 / 蓝 / 绿 / 红 / 紫 / 品红 / 青 / 灰。深色底用亮一档。 */
fun seriesColor(index: Int, dark: Boolean): Color {
    val i = index.mod(CurveSeries.COLOR_COUNT)
    return if (dark) {
        listOf(0xFFF5A623, 0xFF4DA3FF, 0xFF3FB950, 0xFFF85149, 0xFFA371F7, 0xFFDB61A2, 0xFF39C5BB, 0xFF8B949E)[i].toInt().let(::Color)
    } else {
        listOf(0xFFF5A623, 0xFF0969DA, 0xFF1A7F37, 0xFFD1242F, 0xFF8250DF, 0xFFBF3989, 0xFF0F8F8F, 0xFF6E7781)[i].toInt().let(::Color)
    }
}

/** 当前墙钟毫秒，页面在前台时按 [CURVE_TICK_MS] 刷新（≤ 4 fps，稿面要求 ≤ 10 fps）。 */
@Composable
fun rememberCurveNow(): Long {
    val clock = LocalWallClock.current
    val ticking = LocalCurveTicker.current
    var now by remember { mutableLongStateOf(clock()) }
    if (ticking) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    now = clock()
                    delay(CURVE_TICK_MS)
                }
            }
        }
    }
    return now
}

@Composable
internal fun windowLabel(windowSec: Int): String {
    val (n, unit) = CurveMath.windowLabelParts(windowSec)
    return stringResource(
        when (unit) {
            'h' -> R.string.curve_window_hours
            'm' -> R.string.curve_window_minutes
            else -> R.string.curve_window_seconds
        },
        n,
    )
}

// ---- 面板上的曲线卡 ----

/** 面板里的曲线卡（稿面 b2-1）：标题 “温度 / 压力 · 1 分钟” + 图例 + 绘图区（4 条网格线、双 Y 轴、缺测斜纹、游标）。 */
@Composable
fun CurveWidgetCard(
    widget: Widget,
    history: SampleHistory?,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    /** 非空时卡片右上角画 ⤢（≥48dp 触控），点它进曲线全屏。编辑模式 / 截图夹具不传。 */
    onFullscreen: (() -> Unit)? = null,
) {
    val config = widget.curve ?: return
    val now = rememberCurveNow()
    val frame = remember(config, now, history) {
        CurveMath.frame(config, now) { id, from -> history?.read(id, from) ?: SampleHistory.Slice.EMPTY }
    }
    CurveCardBody(widget.displayTitle, widget.id, config, frame, modifier, borderColor, onFullscreen)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurveCardBody(
    title: String,
    id: String,
    config: CurveConfig,
    frame: CurveMath.Frame,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    onFullscreen: (() -> Unit)? = null,
    /** 向导预览卡：名称行右侧的“预览”徽标（规范 65），面板里的真卡片不传。 */
    previewBadge: String? = null,
) {
    val colors = LocalModbusColors.current
    Box(modifier) {
    CardFrame(Modifier, "panel-card-$id", borderColor) {
        // v3：标题（过长只截名称，“· 1 分钟”保留）单独一行，⤢ 在卡内右上；图例另起一行并带当前值。
        Row(
            Modifier.fillMaxWidth().padding(end = if (onFullscreen != null) 30.dp else 0.dp).testTag("curve-title-$id"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, color = colors.muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                // 有预览徽标时名称占满剩余宽度，徽标才能贴到名称行最右
                modifier = (if (previewBadge != null) Modifier.weight(1f) else Modifier.weight(1f, fill = false))
                    .testTag("curve-title-text-$id"),
            )
            Text(" · ${windowLabel(config.windowSec)}", color = colors.muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, softWrap = false)
            if (previewBadge != null) {
                // 规范 65：预览徽标放在名称行最右（稿面 3-4：“实时预览 · 1 分钟” + 右侧“预览”）
                io.github.xingyingyuzhui.modbusmobile.ui.components.ToneBadge(
                    previewBadge, colors.txText, Modifier.testTag("wiz-preview-badge"),
                )
            }
        }
        if (config.legend == LegendPosition.TOP) {
            CurveLegend(config, Modifier.padding(top = 4.dp).testTag("curve-legend-$id"), frame)
        }
        CurveChart(
            config, frame,
            Modifier.weight(1f).fillMaxWidth().heightIn(min = CHART_MIN_HEIGHT).padding(top = 4.dp),
            tag = "curve-chart-$id",
        )
        if (config.legend == LegendPosition.BOTTOM) {
            CurveLegend(config, Modifier.padding(top = 4.dp).testTag("curve-legend-$id"), frame)
        }
    }
    if (onFullscreen != null) {
        val label = stringResource(R.string.curve_open_fullscreen)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onFullscreen)
                .semantics { contentDescription = label }
                .testTag("curve-expand-$id"),
            contentAlignment = Alignment.Center,
        ) { ExpandIcon(colors.muted) }
    }
    }
}

/** 编辑模式里的曲线卡（v3 编辑态）：不画实时图（编辑期间不读数 / 不重绘），只给 ⠿ 名称 + 尺寸、系列摘要和图例。 */
@Composable
fun CurveEditCard(widget: Widget, modifier: Modifier = Modifier) {
    val config = widget.curve ?: return
    val colors = LocalModbusColors.current
    CardFrame(modifier, "panel-card-${widget.id}", fill = colors.surface2) {
        Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            io.github.xingyingyuzhui.modbusmobile.ui.panel.GripDots(colors.dim)
            Text(
                widget.displayTitle, color = colors.text, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            io.github.xingyingyuzhui.modbusmobile.ui.panel.EditSizeTag(stringResource(R.string.panel_edit_size_short, widget.spanColumns, widget.spanRows))
        }
        Text(
            stringResource(R.string.curve_edit_summary, config.series.size, windowLabel(config.windowSec)),
            color = colors.dim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp),
        )
        CurveLegend(config, Modifier.padding(top = 6.dp))
    }
}

private val CHART_MIN_HEIGHT = 96.dp

/** 图例：线样本（实 / 虚 / 点）+ 名称 11.5sp；给了 [frame] 时名称后面跟该系列的最新值（mono 粗，没有就不写）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurveLegend(config: CurveConfig, modifier: Modifier = Modifier, frame: CurveMath.Frame? = null) {
    val colors = LocalModbusColors.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        config.series.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LineSample(s, colors.isDark)
                Text(s.point.name, color = colors.muted, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp))
                val latest = frame?.series?.getOrNull(i)?.latest
                if (latest != null && latest.isFinite()) {
                    Text(
                        ValueCodec.format(latest, s.point.conversion.decimals).orEmpty(),
                        style = Mono.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.text, maxLines = 1, modifier = Modifier.testTag("curve-legend-value-$i"),
                    )
                }
            }
        }
    }
}

@Composable
fun LineSample(series: CurveSeries, dark: Boolean, modifier: Modifier = Modifier) {
    val color = seriesColor(series.colorIndex, dark)
    androidx.compose.foundation.Canvas(modifier.size(width = 14.dp, height = 8.dp)) {
        val y = size.height / 2
        drawLine(
            color, Offset(0f, y), Offset(size.width, y),
            strokeWidth = series.width.dp.toPx().coerceAtLeast(1f),
            cap = if (series.lineStyle == LineStyle.DOT) StrokeCap.Round else StrokeCap.Butt,
            pathEffect = dashFor(series, this),
        )
    }
}

private fun dashFor(series: CurveSeries, scope: DrawScope): PathEffect? = with(scope) {
    when (series.lineStyle) {
        LineStyle.SOLID -> null
        LineStyle.DASH -> PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
        LineStyle.DOT -> PathEffect.dashPathEffect(floatArrayOf(0.1f, 2.2.dp.toPx() + series.width.dp.toPx()))
    }
}

// ---- 绘图区 ----

private class Geometry(
    val width: Float,
    val height: Float,
    val padLeft: Float,
    val padRight: Float,
    val padTop: Float,
    val padBottom: Float,
    val frame: CurveMath.Frame,
) {
    val plotW get() = (width - padLeft - padRight).coerceAtLeast(1f)
    val plotH get() = (height - padTop - padBottom).coerceAtLeast(1f)
    fun x(t: Long): Float = padLeft + ((t - frame.fromMs).toDouble() / (frame.toMs - frame.fromMs).coerceAtLeast(1)).toFloat() * plotW
    fun y(v: Double, axis: CurveMath.Axis): Float = padTop + plotH - ((v - axis.min) / axis.span).toFloat() * plotH
    fun timeAt(px: Float): Long = frame.fromMs + (((px - padLeft) / plotW).coerceIn(0f, 1f) * (frame.toMs - frame.fromMs)).toLong()
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/**
 * 绘图区。游标：点一下 / 横向拖动放下游标（竖虚线 + 读数框），再点一下收起；游标固定在“时间”上，
 * 窗口滚动时它跟着数据走，滚出窗口就消失。竖向滑动不被抢（交给外层滚动）。
 */
@Composable
fun CurveChart(
    config: CurveConfig,
    frame: CurveMath.Frame,
    modifier: Modifier = Modifier,
    tag: String = "curve-chart",
    initialCursorMs: Long? = null,
    /** 全屏：每条网格线都有刻度、底部有时间轴、缺测段标“超时 N s”。 */
    detail: Boolean = false,
) {
    val colors = LocalModbusColors.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(colors) { TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, color = colors.dim) }
    var cursorMs by remember { mutableStateOf(initialCursorMs) }
    val leftAxis = frame.left
    val rightAxis = frame.right
    fun labels(axis: CurveMath.Axis?) = axis?.let {
        if (detail) {
            (0..CurveMath.GRID_INTERVALS).map { i -> CurveMath.axisLabel(it.max - it.span * i / CurveMath.GRID_INTERVALS, it.step) }
        } else {
            listOf(CurveMath.axisLabel(it.max, it.step), CurveMath.axisLabel(it.min, it.step))
        }
    }
    val leftLabels = labels(leftAxis)
    val rightLabels = labels(rightAxis)
    fun labelWidth(list: List<String>?) = list?.maxOfOrNull { measurer.measure(it, labelStyle).size.width }?.let { with(density) { it.toDp() + 6.dp } } ?: 2.dp
    val padLeft = with(density) { labelWidth(leftLabels).toPx() }
    val padRight = with(density) { labelWidth(rightLabels).toPx() }
    val padTop = with(density) { 6.dp.toPx() }
    val padBottom = with(density) { (if (detail) 20.dp else 6.dp).toPx() }
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val gapText: (Long) -> String = if (detail) { ms -> resources.getString(R.string.curve_gap_label, (ms + 500) / 1000) } else { _ -> "" }
    val summary = curveSummary(config, frame)

    // 不用 BoxWithConstraints：它是 SubcomposeLayout，面板网格按行取固有高度时会直接崩。
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.testTag(tag).semantics { contentDescription = summary }.onSizeChanged { sizePx = it }) {
        val widthPx = sizePx.width.toFloat()
        val heightPx = sizePx.height.toFloat()
        val geo = Geometry(widthPx, heightPx, padLeft, padRight, padTop, padBottom, frame)
        val latestGeo by rememberUpdatedState(geo)
        val canCursor = config.cursor && frame.hasData
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (canCursor) {
                        Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { p -> cursorMs = if (cursorMs != null) null else latestGeo.timeAt(p.x) })
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { p -> cursorMs = latestGeo.timeAt(p.x) },
                                    onHorizontalDrag = { change, _ -> cursorMs = latestGeo.timeAt(change.position.x) },
                                )
                            }
                    } else {
                        Modifier
                    },
                ),
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                if (sizePx == IntSize.Zero) return@Canvas
                drawChart(config, geo, colors, measurer, labelStyle, leftLabels, rightLabels, detail, gapText)
                val t = cursorMs
                if (canCursor && t != null && t >= frame.fromMs && t <= frame.toMs) {
                    val x = geo.x(t)
                    drawLine(
                        colors.dim, Offset(x, padTop), Offset(x, padTop + geo.plotH), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    )
                }
            }
            if (!frame.hasData) {
                Text(
                    stringResource(R.string.curve_waiting), color = colors.dim, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center).testTag("curve-waiting"),
                )
            }
            val t = cursorMs
            if (canCursor && t != null && t >= frame.fromMs && t <= frame.toMs && sizePx != IntSize.Zero) {
                val x = geo.x(t)
                CursorReadout(
                    config, frame, t,
                    Modifier.align(Alignment.TopStart).offset {
                        // 光标在右半边时读数框放它左边，反之放右边，免得被切
                        val tooltipW = with(density) { 132.dp.roundToPx() }
                        val left = if (x > widthPx / 2) (x - tooltipW - 8.dp.toPx()).toInt() else (x + 8.dp.toPx()).toInt()
                        IntOffset(left.coerceIn(0, (widthPx - tooltipW).toInt().coerceAtLeast(0)), 2.dp.roundToPx())
                    },
                )
            }
        }
    }
}

@Composable
private fun CursorReadout(config: CurveConfig, frame: CurveMath.Frame, timeMs: Long, modifier: Modifier) {
    val colors = LocalModbusColors.current
    val values = remember(frame, timeMs) { CurveMath.cursor(frame, timeMs) }
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .widthIn(min = 100.dp, max = 132.dp)
            .background(colors.surface2, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("curve-cursor"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(timeFormat.format(Instant.ofEpochMilli(timeMs)), color = colors.muted, fontSize = 10.5.sp, fontFamily = JetBrainsMono, modifier = Modifier.testTag("curve-cursor-time"))
        values.forEachIndexed { i, cv ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(seriesColor(cv.series.colorIndex, colors.isDark)))
                Text(
                    cv.series.point.name, color = colors.muted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    cursorText(cv),
                    color = colors.text, fontSize = 10.5.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, maxLines = 1,
                    modifier = Modifier.testTag("curve-cursor-$i"),
                )
            }
        }
    }
}

private fun cursorText(cv: CurveMath.CursorValue): String {
    val v = cv.value ?: return "—"
    val text = ValueCodec.format(v, cv.series.point.conversion.decimals) ?: return "—"
    val unit = cv.series.point.unitLabel
    return if (unit.isEmpty()) text else "$text $unit"
}

@Composable
private fun curveSummary(config: CurveConfig, frame: CurveMath.Frame): String {
    val parts = frame.series.joinToString("；") { sf ->
        val v = sf.latest?.let { ValueCodec.format(it, sf.series.point.conversion.decimals) }
        val unit = sf.series.point.unitLabel
        sf.series.point.name + " " + (if (v == null) "—" else if (unit.isEmpty()) v else "$v $unit")
    }
    return stringResource(R.string.curve_cd, windowLabel(config.windowSec), parts)
}

private fun DrawScope.drawChart(
    config: CurveConfig,
    geo: Geometry,
    colors: ModbusColors,
    measurer: androidx.compose.ui.text.TextMeasurer,
    labelStyle: TextStyle,
    leftLabels: List<String>?,
    rightLabels: List<String>?,
    detail: Boolean,
    gapText: (Long) -> String,
) {
    val frame = geo.frame
    // 网格：4 条等分横线
    if (config.grid) {
        val stroke = 1.dp.toPx()
        for (i in 0..CurveMath.GRID_INTERVALS) {
            val y = geo.padTop + geo.plotH * i / CurveMath.GRID_INTERVALS
            drawLine(colors.line, Offset(geo.padLeft, y), Offset(geo.padLeft + geo.plotW, y), strokeWidth = stroke)
        }
    }
    // 坐标轴刻度：最上 / 最下两个，颜色跟这条轴上第一个系列走
    fun axisLabels(axis: CurveMath.Axis?, labels: List<String>?, side: YAxis) {
        if (axis == null || labels == null) return
        val color = frame.series.firstOrNull { it.series.axis == side }?.series?.colorIndex?.let { seriesColor(it, colors.isDark) } ?: colors.dim
        val style = labelStyle.copy(color = color)
        labels.forEachIndexed { i, text ->
            val layout = measurer.measure(text, style)
            val y = geo.padTop + geo.plotH * (i.toFloat() / (labels.size - 1).coerceAtLeast(1)) - layout.size.height / 2f
            val x = if (side == YAxis.LEFT) geo.padLeft - layout.size.width - 4.dp.toPx() else geo.padLeft + geo.plotW + 4.dp.toPx()
            drawText(layout, topLeft = Offset(x, y.coerceIn(0f, size.height - layout.size.height)))
        }
    }
    axisLabels(frame.left, leftLabels, YAxis.LEFT)
    axisLabels(frame.right, rightLabels, YAxis.RIGHT)
    if (detail) {
        // 底部时间轴：起点 / 中点 / 终点
        val style = labelStyle.copy(color = colors.dim)
        val y = geo.padTop + geo.plotH + 4.dp.toPx()
        listOf(0f, 0.5f, 1f).forEach { f ->
            val layout = measurer.measure(timeFormat.format(Instant.ofEpochMilli(frame.fromMs + ((frame.toMs - frame.fromMs) * f).toLong())), style)
            val x = (geo.padLeft + geo.plotW * f - layout.size.width * f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, y))
        }
    }

    clipRect(geo.padLeft, geo.padTop - 2.dp.toPx(), geo.padLeft + geo.plotW, geo.padTop + geo.plotH + 2.dp.toPx()) {
        if (config.gaps == GapStyle.HATCH) {
            val hatch = colors.error.copy(alpha = 0.22f)
            val seen = HashSet<LongRange>()
            for (sf in frame.series) for (gap in sf.gaps) {
                if (!seen.add(gap)) continue
                val x0 = geo.x(gap.first)
                val x1 = geo.x(gap.last)
                if (x1 - x0 < 1f) continue
                if (detail && x1 - x0 > 48.dp.toPx()) {
                    val layout = measurer.measure(gapText(gap.last - gap.first), labelStyle.copy(color = colors.error))
                    drawText(layout, topLeft = Offset(x0 + 4.dp.toPx(), geo.padTop + 2.dp.toPx()))
                }
                clipRect(x0, geo.padTop, x1, geo.padTop + geo.plotH) {
                    val step = 6.dp.toPx()
                    var x = x0 - geo.plotH
                    while (x < x1) {
                        drawLine(hatch, Offset(x, geo.padTop + geo.plotH), Offset(x + geo.plotH, geo.padTop), strokeWidth = 1.dp.toPx())
                        x += step
                    }
                }
            }
        }
        for (sf in frame.series) {
            val axis = (if (sf.series.axis == YAxis.LEFT) frame.left else frame.right) ?: continue
            drawSeries(sf, axis, geo, seriesColor(sf.series.colorIndex, colors.isDark))
        }
    }
}

private fun DrawScope.drawSeries(sf: CurveMath.SeriesFrame, axis: CurveMath.Axis, geo: Geometry, color: Color) {
    val s = sf.series
    val widthPx = s.width.dp.toPx()
    val baseline = geo.padTop + geo.plotH
    for (seg in sf.segments) {
        if (seg.size == 0) continue
        if (seg.size == 1) {
            drawCircle(color, widthPx * 1.2f + 1.dp.toPx(), Offset(geo.x(seg.times[0]), geo.y(seg.values[0], axis)))
            continue
        }
        val line = Path()
        for (i in 0 until seg.size) {
            val x = geo.x(seg.times[i])
            val y = geo.y(seg.values[i], axis)
            if (i == 0) line.moveTo(x, y)
            else if (s.step) {
                line.lineTo(x, geo.y(seg.values[i - 1], axis))
                line.lineTo(x, y)
            } else line.lineTo(x, y)
        }
        if (s.fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(geo.x(seg.times[seg.size - 1]), baseline)
                lineTo(geo.x(seg.times[0]), baseline)
                close()
            }
            drawPath(area, color.copy(alpha = 0.16f))
        }
        drawPath(
            line, color,
            style = Stroke(width = widthPx, cap = if (s.lineStyle == LineStyle.DOT) StrokeCap.Round else StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = dashFor(s, this)),
        )
        if (s.showPoints && seg.size <= MAX_DOTS) {
            for (i in 0 until seg.size) drawCircle(color, widthPx + 1.5.dp.toPx(), Offset(geo.x(seg.times[i]), geo.y(seg.values[i], axis)))
        }
    }
}

private const val MAX_DOTS = 400

/** 不依赖 Compose 的 [CompositionLocalProvider] 小封装：给截图 / 测试固定时间并关掉重绘节拍。 */
@Composable
fun FixedCurveClock(nowMs: Long, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWallClock provides { nowMs }, LocalCurveTicker provides false, content = content)
}
