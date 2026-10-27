package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.ui.components.PauseIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.PlayIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.TextLink
import io.github.xingyingyuzhui.modbusmobile.ui.curve.LocalCurveTicker
import io.github.xingyingyuzhui.modbusmobile.ui.curve.LocalWallClock
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import kotlinx.coroutines.delay

// v3 面板页头（docs/design/panel-v3.html ①）：原来的“标题 / 会话条 / 面板 chips”三段压成
// 标题行 + 一行“面板页签 · 轮询胶囊”。横屏把它们收进同一行。

/** 轮询胶囊显示什么。 */
internal enum class PollPillState {
    /** 主站轮询中，有实际周期。 */
    POLLING,

    /** 主站在跑、还没有第一轮统计。 */
    STARTING,
    PAUSED,

    /** 主站在跑，但当前工程没有要读的点位（只有按钮）。 */
    MASTER,
    SLAVE,
    OFFLINE,
}

internal fun pollPillState(masterRunning: Boolean, slaveRunning: Boolean, anyPolled: Boolean, stats: PollStats?, paused: Boolean): PollPillState = when {
    masterRunning && !anyPolled -> PollPillState.MASTER
    masterRunning && paused -> PollPillState.PAUSED
    masterRunning && stats != null -> PollPillState.POLLING
    masterRunning -> PollPillState.STARTING
    slaveRunning -> PollPillState.SLAVE
    else -> PollPillState.OFFLINE
}

/**
 * 面板页签（原 chips）：文字页签，选中的加粗 + 3dp 琥珀下划线；每个页签热区高 48。
 * 只有一个面板时也显示它的名字。放不下时横向滚动。
 */
@Composable
internal fun PanelTabs(panels: List<Panel>, current: Panel?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    val desc = stringResource(R.string.panel_chips_description)
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = desc }
            .testTag("panel-tabs"),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        panels.forEach { panel ->
            val selected = panel.id == current?.id
            val accent = colors.accent
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .selectable(selected = selected, role = Role.Tab) { onSelect(panel.id) }
                    .drawBehind {
                        if (selected) {
                            val h = 3.dp.toPx()
                            drawRoundRect(accent, Offset(0f, size.height - h - 6.dp.toPx()), Size(size.width, h), CornerRadius(h / 2))
                        }
                    }
                    .testTag("panel-tab-${panel.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    panel.name,
                    color = if (selected) colors.text else colors.muted,
                    fontSize = 14.5.sp, lineHeight = 20.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 轮询胶囊：可见高 34、圆角 17、surface1 + 描边；左边状态点 + mono 12 文字，能暂停时右边是 28dp 的 ⏸ / ▶ 圆钮。
 * 整个胶囊就是按钮（热区补到 48）：轮询中 / 已暂停 → 暂停 / 继续；未连接 → 去连接页；从站 / 只有按钮 → 不可点。
 */
@Composable
internal fun PollPill(
    state: PollPillState,
    stats: PollStats?,
    onTogglePause: () -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val text = when (state) {
        PollPillState.POLLING -> stringResource(R.string.panel_pill_polling, (stats?.actualMs ?: stats?.configuredMs ?: 0).toInt())
        PollPillState.STARTING -> stringResource(R.string.panel_pill_starting)
        PollPillState.PAUSED -> stringResource(R.string.panel_pill_paused)
        PollPillState.MASTER -> stringResource(R.string.panel_pill_master)
        PollPillState.SLAVE -> stringResource(R.string.panel_pill_slave)
        PollPillState.OFFLINE -> stringResource(R.string.panel_pill_offline)
    }
    val desc = when (state) {
        PollPillState.POLLING -> stringResource(
            R.string.panel_pill_cd_polling, (stats?.actualMs ?: stats?.configuredMs ?: 0).toInt(), (stats?.configuredMs ?: 0).toInt(),
        )
        PollPillState.PAUSED -> stringResource(R.string.panel_pill_cd_paused)
        PollPillState.OFFLINE -> stringResource(R.string.panel_pill_cd_offline)
        PollPillState.SLAVE -> stringResource(R.string.panel_pill_cd_slave)
        else -> text
    }
    val pausable = state == PollPillState.POLLING || state == PollPillState.STARTING || state == PollPillState.PAUSED
    val paused = state == PollPillState.PAUSED
    val dot: Color = when (state) {
        PollPillState.POLLING, PollPillState.STARTING, PollPillState.MASTER -> colors.rxLocal
        PollPillState.PAUSED -> colors.accent
        PollPillState.SLAVE -> colors.foreign
        PollPillState.OFFLINE -> colors.dim
    }
    val shape = RoundedCornerShape(17.dp)
    val action: (() -> Unit)? = when {
        pausable -> onTogglePause
        state == PollPillState.OFFLINE -> onOpenConnection
        else -> null
    }
    val actionLabel = when {
        pausable -> stringResource(if (paused) R.string.panel_resume_cd else R.string.panel_pause_cd)
        else -> null
    }
    Box(
        modifier
            .then(if (action != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(desc, actionLabel).joinToString("，")
                if (action != null) role = Role.Button
            }
            .then(if (action != null) Modifier.clickable(onClick = action) else Modifier)
            .testTag(if (pausable) "panel-pause" else "panel-pill"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(34.dp)
                .clip(shape)
                .background(colors.surface1)
                .border(1.dp, if (paused) colors.txText.copy(alpha = 0.5f) else colors.line, shape)
                .padding(start = 11.dp, end = if (pausable) 3.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier.size(8.dp).drawBehind {
                    if (state != PollPillState.OFFLINE) drawCircle(dot.copy(alpha = 0.18f), radius = size.minDimension / 2 + 3.dp.toPx())
                }.clip(CircleShape).background(dot),
            )
            Text(
                text,
                style = Mono.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = if (paused) colors.accentText else colors.muted,
                maxLines = 1,
                modifier = Modifier.testTag("panel-pill-text"),
            )
            if (pausable) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(colors.surface2), contentAlignment = Alignment.Center) {
                    if (paused) PlayIcon(colors.text, size = 13.dp) else PauseIcon(colors.text, size = 13.dp)
                }
            }
        }
    }
}

/**
 * 空面板（v3 1-5）：居中插画 + 标题 + 说明 + 三步引导（真实的下一步，不是示例卡）+ 「＋ 添加组件」主按钮。
 * 第 2 步在没以主站连接时给「去连接 ›」，连上了显示已完成。[onAdd] 为 null（只读 / 没有仓库）时不画按钮。
 */
@Composable
internal fun PanelEmptyState(masterRunning: Boolean, onAdd: (() -> Unit)?, onOpenConnection: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    BoxWithConstraints(modifier.testTag("panel-empty")) {
        val minHeight = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minHeight).padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(colors.surface1)
                    .border(1.dp, colors.line, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) { EmptyPanelGlyph(colors.accent) }
            Text(
                stringResource(R.string.panel_empty_title), color = colors.text, fontSize = 18.sp, lineHeight = 24.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                stringResource(R.string.panel_empty_body), color = colors.muted, fontSize = 13.sp, lineHeight = 20.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 14.dp, end = 14.dp),
            )
            Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EmptyStep(1, stringResource(R.string.panel_empty_step_add), done = false)
                EmptyStep(
                    2,
                    stringResource(if (masterRunning) R.string.panel_empty_step_connected else R.string.panel_empty_step_connect),
                    done = masterRunning,
                    action = if (masterRunning) null else ({ TextLink(stringResource(R.string.panel_empty_go_connect), onOpenConnection, Modifier.testTag("panel-empty-connect"), color = colors.accentText) }),
                )
                EmptyStep(3, stringResource(R.string.panel_empty_step_poll), done = false)
            }
            if (onAdd != null) {
                PrimaryButton(
                    stringResource(R.string.panel_add_widget_plus), onAdd,
                    Modifier.fillMaxWidth().padding(top = 16.dp).testTag("panel-empty-add"),
                )
            }
        }
    }
}

@Composable
private fun EmptyStep(n: Int, text: String, done: Boolean, action: (@Composable () -> Unit)? = null) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(colors.surface1).border(1.dp, colors.line, shape)
            .padding(start = 12.dp, end = 4.dp).testTag("panel-empty-step-$n"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (done) colors.wash(colors.rxLocal) else colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done) "✓" else n.toString(), style = Mono.copy(fontSize = 11.5.sp, lineHeight = 14.sp), color = if (done) colors.rxLocal else colors.muted)
        }
        Text(text, color = colors.text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        action?.invoke()
    }
}

/** 面板图标（四块圆角矩形，右下那块虚线 = 待添加），画在 44dp 里。 */
@Composable
private fun EmptyPanelGlyph(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(44.dp)) {
        val u = size.minDimension / 24f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * u)
        val dashed = androidx.compose.ui.graphics.drawscope.Stroke(
            1.6f * u, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.5f * u, 2.5f * u)),
        )
        fun box(x: Float, y: Float, w: Float, h: Float, style: androidx.compose.ui.graphics.drawscope.Stroke) =
            drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(2f * u), style = style)
        box(3f, 3f, 8f, 10f, stroke)
        box(13f, 3f, 8f, 6f, stroke)
        box(13f, 11f, 8f, 10f, dashed)
        box(3f, 15f, 8f, 6f, stroke)
    }
}

/**
 * 面板页的“现在”：有过期卡时每秒走一次（“12 秒前的值”跟着变），没有就停；测试里 [LocalCurveTicker] 为 false 时不走，
 * 只在 [key]（读数）变化时重取一次时钟。
 */
@Composable
internal fun rememberPanelNow(ticking: Boolean, key: Any?): Long {
    val clock = LocalWallClock.current
    val allowed = LocalCurveTicker.current
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(key) { now = clock() }
    if (ticking && allowed) {
        LaunchedEffect(Unit) {
            while (true) {
                now = clock()
                delay(1_000)
            }
        }
    }
    return now
}
