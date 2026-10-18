package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.panel.ErrorKind
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.application.panel.StateTone
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdLevel
import io.github.xingyingyuzhui.modbusmobile.application.panel.ValueCodec
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetWrites
import io.github.xingyingyuzhui.modbusmobile.application.modiconLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusSwitchVisual
import io.github.xingyingyuzhui.modbusmobile.ui.components.SmallButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.ToneBadge
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 开关 / 按钮卡能不能点：[enabled] 为 false 时控件置灰并在 meta 行写 [reason]；[busy] = 正在读整字 / 写入。
 * 传 null（编辑模式、向导预览）= 控件只显示、没有任何点击处理，不可能写设备。
 */
data class CardWrite(
    val enabled: Boolean,
    val reason: String?,
    val busy: Boolean,
    val onSwitch: (Boolean) -> Unit,
    val onButton: () -> Unit,
    /** 寄存器表某一行的 ✎（行号从 0 起）。 */
    val onRow: (Int) -> Unit = {},
)

/**
 * 面板组件卡（稿面 b2-1 `.w`）：圆角 16、1px 边、内边距 10/12（border-box，内容离外缘 13/11）、
 * 名称 12.5 muted + 右上 badge、值 mono 26/600 + 单位 12、meta mono 10.5 dim。
 * 质量态：过期 = 值 muted；超时 / 错误 = 值 dim + 红色删除线 + 红 badge + 失败说明（取代 meta）。
 */
@Composable
fun WidgetCard(
    widget: Widget,
    snapshot: PointSnapshot?,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    /** 向导里的预览卡用强调色描边（稿面 border-color:var(--amber)）。 */
    borderColor: Color? = null,
    /** 开关 / 按钮的写入入口；null = 不可操作（编辑模式 / 向导预览）。 */
    write: CardWrite? = null,
    /** 寄存器表要用：各行点位的读数（key = 行点位 id）。 */
    snapshots: Map<String, PointSnapshot> = emptyMap(),
) {
    val point = widget.point ?: return
    // 编辑模式（稿面 b2-2）：名称前加 ⠿，不显示 badge 和 meta（值还是实时的）。
    val look = rememberLook(widget, point, snapshot).let { if (editing) it.copy(badge = null, meta = "") else it }
    val name = (if (editing) "⠿ " else "") + widget.displayName(point)
    CardFrame(modifier, "panel-card-${widget.id}", borderColor) {
        when (widget.kind) {
            WidgetKinds.LAMP -> LampBody(widget, point, name, snapshot, look)
            WidgetKinds.STATUS_TEXT -> StatusBody(widget, point, name, snapshot, look)
            WidgetKinds.PROGRESS -> ProgressBody(widget, point, name, snapshot, look)
            WidgetKinds.GAUGE -> GaugeBody(widget, point, name, snapshot, look)
            WidgetKinds.SWITCH -> SwitchBody(widget, point, name, snapshot, look, write)
            WidgetKinds.BUTTON -> ButtonBody(widget, point, name, editing, write)
            WidgetKinds.REGISTER_TABLE -> TableBody(widget, name, editing, snapshots, write)
            else -> NumberBody(widget, point, name, snapshot, look)
        }
    }
}

@Composable
internal fun CardFrame(modifier: Modifier, tag: String, borderColor: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    Column(
        modifier
            .fillMaxSize()
            .testTag(tag)
            .background(colors.surface1, shape)
            .border(1.dp, borderColor ?: colors.line, shape)
            // 稿面 border-box：边框在内边距之外占位，Compose 的 border 不占位，补 1dp。
            .padding(1.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

/** 一张卡的“怎么画”：badge、值的颜色、是否划线、meta 文字。 */
internal data class Look(
    val badge: Pair<String, Color>?,
    val valueColor: Color,
    val strike: Boolean,
    val meta: String,
    val metaProblem: Boolean,
    val hasValue: Boolean,
)

@Composable
internal fun rememberLook(widget: Widget, point: PointDefinition, snapshot: PointSnapshot?): Look {
    val colors = LocalModbusColors.current
    val quality = snapshot?.quality ?: Quality.NO_DATA
    val hasValue = snapshot?.raw != null
    val addr = addressLabel(point)
    val badge: Pair<String, Color>? = when (quality) {
        Quality.OK -> when (snapshot?.level) {
            ThresholdLevel.ALERT -> stringResource(R.string.panel_badge_alert) to colors.error
            ThresholdLevel.WARN -> stringResource(R.string.panel_badge_warn) to colors.txText
            else -> stringResource(R.string.panel_badge_ok) to colors.rxLocal
        }
        Quality.STALE -> stringResource(R.string.panel_badge_stale) to colors.dropped
        Quality.TIMEOUT -> stringResource(R.string.panel_badge_timeout) to colors.error
        Quality.ERROR -> stringResource(R.string.panel_badge_error) to colors.error
        Quality.NO_DATA -> null
    }
    val valueColor = when (quality) {
        Quality.OK -> when (snapshot?.level) {
            ThresholdLevel.ALERT -> colors.error
            ThresholdLevel.WARN -> colors.txText
            else -> colors.text
        }
        Quality.STALE -> colors.muted
        else -> colors.dim
    }
    val failures = snapshot?.failures ?: 0
    val problem = when (quality) {
        Quality.OK -> null
        Quality.NO_DATA -> when (snapshot?.error?.kind) {
            null -> null
            else -> stringResource(R.string.panel_meta_failed, failures)
        }
        Quality.STALE -> stringResource(R.string.panel_meta_stale)
        Quality.TIMEOUT -> stringResource(R.string.panel_meta_timeout, failures)
        Quality.ERROR -> when (snapshot?.error?.kind) {
            ErrorKind.EXCEPTION -> stringResource(R.string.panel_meta_exception, snapshot.error.code ?: 0)
            ErrorKind.BAD_VALUE -> stringResource(R.string.panel_meta_bad_value)
            else -> stringResource(R.string.panel_meta_invalid)
        }
    }
    val okMeta = when (widget.kind) {
        WidgetKinds.PROGRESS -> "$addr · ${rangeLabel(point)}"
        else -> listOfNotNull(addr, snapshot?.updatedWallMs?.let(::clock)).joinToString(" · ")
    }
    val meta = when {
        problem != null -> problem
        quality == Quality.NO_DATA -> stringResource(R.string.panel_meta_waiting, addr)
        else -> okMeta
    }
    val strike = (quality == Quality.TIMEOUT || quality == Quality.ERROR) && hasValue
    return Look(badge, valueColor, strike, meta, problem != null && quality != Quality.NO_DATA, hasValue)
}

// ---- 各类型卡片 ----

@Composable
private fun NameRow(title: String, badge: Pair<String, Color>?, trailing: (@Composable () -> Unit)? = null) {
    val colors = LocalModbusColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title, color = colors.muted, fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing() else if (badge != null) ToneBadge(badge.first, badge.second)
    }
}

@Composable
private fun Meta(look: Look, topPadding: Int = 2) {
    if (look.meta.isEmpty()) return
    val colors = LocalModbusColors.current
    Text(
        look.meta,
        style = Mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
        color = if (look.metaProblem) colors.error else colors.dim,
        modifier = Modifier.padding(top = topPadding.dp).testTag("panel-meta"),
    )
}

@Composable
private fun NumberBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(name, look.badge)
    val text = if (snapshot?.raw == null) "—" else snapshot.text ?: flagText(snapshot)
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text,
            style = Mono.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
            color = look.valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .alignByBaseline()
                .testTag("panel-value-${widget.id}")
                .then(if (look.strike) Modifier.redStrike(colors.error) else Modifier),
        )
        if (point.unitLabel.isNotBlank() && snapshot?.raw != null) {
            Text(point.unitLabel, color = colors.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.alignByBaseline())
        }
    }
    Meta(look)
}

@Composable
private fun LampBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(name, look.badge)
    val known = snapshot?.raw != null
    val on = known && (snapshot?.engineering ?: 0.0) != 0.0
    val label = when {
        !known -> "—"
        on -> widget.onLabel.ifBlank { stringResource(R.string.panel_flag_on) }
        else -> widget.offLabel.ifBlank { stringResource(R.string.panel_flag_off) }
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Lamp(on = on && snapshot?.quality == Quality.OK, dim = on)
        Text(
            label,
            fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
            color = if (snapshot?.quality == Quality.OK) (if (on) colors.text else colors.muted) else look.valueColor,
            modifier = Modifier.testTag("panel-value-${widget.id}"),
        )
    }
    Meta(look, topPadding = 6)
}

/**
 * 开关卡：名称 + badge、当前状态文字、右侧 42×24 开关（稿面 .tg，点击区 ≥48×48）。
 * 点开关**不会**直接写：它只是请求“切到相反状态”，确认弹窗里才发（见 [PanelWriteController]）。
 * 状态未知 / 过期、非主站、正在读写时置灰；编辑模式 / 向导预览（[write] 为 null）只画不响应。
 */
@Composable
private fun SwitchBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look, write: CardWrite?) {
    val colors = LocalModbusColors.current
    NameRow(name, look.badge)
    val known = snapshot?.raw != null && snapshot.quality == Quality.OK
    val on = snapshot?.raw != null && (snapshot.engineering ?: 0.0) != 0.0
    val label = when {
        snapshot?.raw == null -> "—"
        on -> widget.onLabel.ifBlank { stringResource(R.string.panel_flag_on) }
        else -> widget.offLabel.ifBlank { stringResource(R.string.panel_flag_off) }
    }
    val usable = write != null && write.enabled && !write.busy && known
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
            color = if (known) (if (on) colors.text else colors.muted) else look.valueColor,
            modifier = Modifier.weight(1f).testTag("panel-value-${widget.id}"),
        )
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .alpha(if (usable || write == null) 1f else 0.45f)
                .testTag("panel-switch-${widget.id}")
                .then(
                    if (write != null) {
                        Modifier.toggleable(value = on, enabled = usable, role = Role.Switch, onValueChange = { write.onSwitch(it) })
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterEnd,
        ) { ModbusSwitchVisual(on) }
    }
    val note = when {
        write == null -> null
        write.busy -> stringResource(R.string.panel_write_busy)
        !write.enabled -> write.reason
        !known -> stringResource(R.string.panel_write_unknown_state)
        else -> null
    }
    if (note != null) {
        Text(
            note, style = Mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp), color = colors.dim,
            modifier = Modifier.padding(top = 2.dp).testTag("panel-write-note"),
        )
    } else {
        Meta(look)
    }
}

/**
 * 按钮卡：名称 + 整宽按钮（稿面 .btn.pri.xs，触控 ≥48dp）+ 一行说明“写什么、写到哪”（广播红色）。
 * 按钮的点位不轮询；点按钮只是请求写预设值，确认弹窗里才发。
 */
@Composable
private fun ButtonBody(widget: Widget, point: PointDefinition, name: String, editing: Boolean, write: CardWrite?) {
    val colors = LocalModbusColors.current
    NameRow(name, null)
    val broadcast = point.unitId == 0
    val label = widget.buttonLabel.ifBlank { stringResource(R.string.panel_button_default) }
    val usable = write != null && write.enabled && !write.busy
    Box(Modifier.fillMaxWidth().padding(top = 2.dp).testTag("panel-button-${widget.id}")) {
        if (write != null) {
            SmallButton(label, onClick = write.onButton, Modifier.fillMaxWidth(), primary = true, enabled = usable)
        } else {
            // 编辑模式 / 向导预览：只画样子（尺寸和真按钮一致），没有任何点击处理，也不挡住“点卡片打开向导”
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface3).testTag("panel-button-visual-${widget.id}"),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = colors.dim, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
    val value = widget.writeValue?.let { v ->
        if (point.area == RegisterArea.COIL) (if (v != 0.0) "ON" else "OFF") else ValueCodec.format(v, null).orEmpty()
    }.orEmpty()
    val target = stringResource(
        if (broadcast) R.string.panel_button_target_broadcast else R.string.panel_button_target,
        addressLabel(point), value, point.unitId,
    )
    val note = when {
        editing || write == null -> target
        write.busy -> stringResource(R.string.panel_write_busy)
        !write.enabled -> write.reason ?: target
        else -> target
    }
    Text(
        note, style = Mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
        color = if (broadcast && (write == null || write.enabled) && !editing) colors.error else colors.dim,
        modifier = Modifier.padding(top = 2.dp).testTag("panel-button-note-${widget.id}"),
    )
}

/**
 * 寄存器表卡（我自己的设计，稿面只有选类型页的一张缩略图）：名称 + 汇总 badge、表头（地址 | 值 · 类型 | HEX | ✎）、
 * 每行 = 地址（mono 13 + 参考号 10 dim）、值（mono 15）、HEX（mono 11 dim，线路字序）、可写时右侧 48dp 的 ✎。
 * 行高：只读 40、可写 48（✎ 触控 ≥48）。✎ 只是请求，点了先看写入帧、确认才写（见 [PanelWriteController]）。
 * 编辑模式只画摘要；向导预览（[write] 为 null）画出 ✎ 但不响应。
 */
@Composable
private fun TableBody(widget: Widget, name: String, editing: Boolean, snapshots: Map<String, PointSnapshot>, write: CardWrite?) {
    val colors = LocalModbusColors.current
    val rows = widget.tablePoints
    if (rows.isEmpty()) return
    val first = rows.first()
    val last = rows.last()
    // 范围写到最后一个寄存器（32 位行占 2 个）：HR100–HR107，而不是最后一行的起始地址
    val endLabel = addressLabel(last.copy(address = last.address + last.quantity - 1))
    val range = addressLabel(first) + if (endLabel != addressLabel(first)) "–$endLabel" else ""
    if (editing) {
        NameRow(name, null)
        Text(
            stringResource(
                R.string.panel_table_edit_summary, rows.size, addressLabel(first), endLabel,
                stringResource(if (widget.tableWritable) R.string.panel_table_mode_rw else R.string.panel_table_mode_ro),
            ),
            color = colors.dim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp).testTag("panel-table-summary-${widget.id}"),
        )
        return
    }
    val snaps = rows.map { snapshots[it.id] }
    val worst = snaps.withIndex().maxByOrNull { (_, s) -> qualityRank(s?.quality) }
    val look = rememberLook(widget, rows[worst?.index ?: 0], worst?.value)
    val badge = if (snaps.all { it?.raw == null && (it?.quality ?: Quality.NO_DATA) == Quality.NO_DATA }) null else look.badge
    NameRow(name, badge)
    val writable = widget.tableWritable
    val isBits = first.isBitArea
    val usable = write != null && write.enabled && !write.busy
    val unit = first.unitLabel.takeIf { it.isNotBlank() }
    val head = first.type.name + (unit?.let { " · $it" } ?: "")
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        val style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp, color = colors.dim)
        Text(stringResource(R.string.panel_table_head_addr), style = style, modifier = Modifier.width(TABLE_ADDR_W))
        Text(stringResource(R.string.panel_table_head_value, head), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!isBits) Text(stringResource(R.string.panel_table_head_hex), style = style, modifier = Modifier.width(TABLE_HEX_W))
        if (writable) Box(Modifier.width(TABLE_EDIT_W))
    }
    val rowHeight = if (writable) 48.dp else 40.dp
    rows.forEachIndexed { i, row ->
        val snap = snaps[i]
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = rowHeight).testTag("panel-row-${widget.id}-$i"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(TABLE_ADDR_W)) {
                Text(addressLabel(row), style = Mono.copy(fontSize = 13.sp, lineHeight = 16.sp), color = colors.text, maxLines = 1)
                Text(modiconLabel(row.area, row.address), style = Mono.copy(fontSize = 10.sp, lineHeight = 12.sp), color = colors.dim, maxLines = 1)
            }
            val quality = snap?.quality ?: Quality.NO_DATA
            val has = snap?.raw != null
            val color = when (quality) {
                Quality.OK -> when (snap?.level) {
                    ThresholdLevel.ALERT -> colors.error
                    ThresholdLevel.WARN -> colors.txText
                    else -> colors.text
                }
                Quality.STALE -> colors.muted
                else -> colors.dim
            }
            val text = when {
                !has -> "—"
                isBits -> if (snap?.engineering == 1.0) "ON" else "OFF"
                else -> snap?.text ?: "—"
            }
            val strike = (quality == Quality.TIMEOUT || quality == Quality.ERROR) && has
            // 删除线只画在文字上（外面的 Box 占满列宽，文字自己包内容）
            Box(Modifier.weight(1f)) {
                Text(
                    text, style = Mono.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), color = color,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("panel-row-value-${widget.id}-$i").then(if (strike) Modifier.redStrike(colors.error) else Modifier),
                )
            }
            if (!isBits) {
                val words = WidgetWrites.wordsOf(row, snap?.raw)
                Text(
                    words?.joinToString(" ") { "%04X".format(it) } ?: "—",
                    style = Mono.copy(fontSize = 11.sp, lineHeight = 14.sp), color = colors.dim, maxLines = 1,
                    modifier = Modifier.width(TABLE_HEX_W).testTag("panel-row-hex-${widget.id}-$i"),
                )
            }
            if (writable) {
                val desc = stringResource(R.string.panel_table_edit_desc, addressLabel(row))
                Box(
                    Modifier
                        .width(TABLE_EDIT_W).fillMaxHeight()
                        .alpha(if (usable || write == null) 1f else 0.45f)
                        .then(
                            if (write != null) {
                                Modifier.semantics { contentDescription = desc }
                                    .clickable(enabled = usable, role = Role.Button) { write.onRow(i) }
                            } else {
                                Modifier
                            },
                        )
                        .testTag("panel-row-edit-${widget.id}-$i"),
                    contentAlignment = Alignment.Center,
                ) { Text("✎", color = colors.accentText, fontSize = 17.sp) }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    val updated = snaps.mapNotNull { it?.updatedWallMs }.maxOrNull()
    val anyData = snaps.any { it?.raw != null }
    val meta = when {
        look.metaProblem -> look.meta
        !anyData -> stringResource(R.string.panel_meta_waiting, range)
        updated != null -> stringResource(R.string.panel_table_meta_time, first.unitId, addressLabel(first), endLabel, clock(updated))
        else -> stringResource(R.string.panel_table_meta, first.unitId, addressLabel(first), endLabel)
    }
    val note = when {
        write == null || !writable -> null
        write.busy -> stringResource(R.string.panel_write_busy)
        !write.enabled -> write.reason
        else -> null
    }
    Text(
        note ?: meta, style = Mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
        color = if (note == null && look.metaProblem) colors.error else colors.dim,
        modifier = Modifier.padding(top = 4.dp).testTag(if (note != null) "panel-table-note-${widget.id}" else "panel-table-meta-${widget.id}"),
    )
}

private val TABLE_ADDR_W = 72.dp
private val TABLE_HEX_W = 84.dp
private val TABLE_EDIT_W = 48.dp

/** 越大越“糟”：错误 > 超时 > 过期 > 正常 > 没数据，卡片 badge / 说明跟最糟的那一行走。 */
private fun qualityRank(q: Quality?): Int = when (q) {
    Quality.ERROR -> 4
    Quality.TIMEOUT -> 3
    Quality.STALE -> 2
    Quality.OK -> 1
    else -> 0
}

/** 14dp 灯，亮 = 绿 + 光晕（稿面 box-shadow 0 0 10px）；灭 / 无数据 = surface3；有值但读取失败（过期）= 绿色减淡，无光晕。 */
@Composable
private fun Lamp(on: Boolean, dim: Boolean) {
    val colors = LocalModbusColors.current
    val base = when {
        on -> colors.rxLocal
        dim -> colors.rxLocal.copy(alpha = 0.45f)
        else -> colors.surface3
    }
    Box(
        Modifier
            .size(14.dp)
            .drawBehind {
                if (on) {
                    drawCircle(
                        Brush.radialGradient(
                            0.45f to colors.rxLocal.copy(alpha = 0.55f),
                            1f to Color.Transparent,
                            center = center,
                            radius = 12.dp.toPx(),
                        ),
                        radius = 12.dp.toPx(),
                    )
                }
            }
            .clip(CircleShape)
            .background(base),
    )
}

@Composable
private fun StatusBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(name, look.badge)
    val key: Long? = when (val raw = snapshot?.raw) {
        is RawValue.Integer -> raw.bits
        is RawValue.Flag -> if (raw.value) 1L else 0L
        else -> null
    }
    val mapping = key?.let { k -> widget.states.firstOrNull { it.value == k } }
    val ok = snapshot?.quality == Quality.OK
    val (text, color) = when {
        snapshot?.raw == null -> "—" to look.valueColor
        mapping != null -> mapping.label to (if (ok) tone(colors, mapping.tone) else look.valueColor)
        key != null -> stringResource(R.string.panel_state_undefined, key.toInt()) to (if (ok) colors.muted else look.valueColor)
        else -> (snapshot.text ?: "—") to look.valueColor
    }
    Text(
        text,
        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp).testTag("panel-value-${widget.id}")
            .then(if (look.strike) Modifier.redStrike(colors.error) else Modifier),
    )
    val meta = if (ok && key != null) stringResource(R.string.panel_meta_state_value, addressLabel(point), key.toInt()) else look.meta
    if (look.meta.isNotEmpty()) Meta(look.copy(meta = meta))
}

@Composable
private fun ProgressBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    val min = point.rangeMin ?: 0.0
    val max = point.rangeMax ?: 100.0
    val value = snapshot?.engineering
    val fraction = if (value == null || snapshot.raw == null) 0f else ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
    val valueText = if (snapshot?.raw == null) "—" else (snapshot.text ?: "") + point.unitLabel.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    NameRow(name, look.badge) {
        Text(
            valueText,
            style = Mono.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = if (snapshot?.quality == Quality.OK) colors.txText else look.valueColor,
            modifier = Modifier.testTag("panel-value-${widget.id}")
                .then(if (look.strike) Modifier.redStrike(colors.error) else Modifier),
        )
    }
    val fill = when {
        snapshot?.quality != Quality.OK -> colors.dim
        snapshot.level == ThresholdLevel.ALERT -> colors.error
        else -> colors.tx
    }
    val barShape = RoundedCornerShape(4.dp)
    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(barShape).background(colors.surface2).testTag("panel-bar-${widget.id}")) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(barShape).background(fill))
        }
    }
    Meta(look, topPadding = 6)
}

/**
 * 仪表（半圆）：8dp 底轨 + 值弧（正常 = tx、预警 = 琥珀、告警 = 红、非正常数据 = 灰）；
 * 阈值分段画在底轨外侧一圈细带上（预警 / 告警区间，随量程换算，不被值弧盖住）；中间下方是当前值，两端是量程。
 * 值在量程外：弧停在端点（读数仍显示真实值）。没有数据时只画底轨和分段。
 */
@Composable
private fun GaugeBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(name, look.badge)
    val min = point.rangeMin ?: 0.0
    val max = point.rangeMax ?: 100.0
    val span = if (max > min) max - min else 1.0
    val value = snapshot?.engineering
    val hasValue = value != null && snapshot?.raw != null
    val fraction = if (!hasValue) 0f else ((value!! - min) / span).coerceIn(0.0, 1.0).toFloat()
    val ok = snapshot?.quality == Quality.OK
    val arcColor = when {
        !ok -> colors.dim
        snapshot?.level == ThresholdLevel.ALERT -> colors.error
        snapshot?.level == ThresholdLevel.WARN -> colors.txText
        else -> colors.tx
    }
    val track = colors.surface3
    val warn = colors.txText
    val alert = colors.error
    val bands = point.thresholds.filter { it.level != ThresholdLevel.NORMAL }.sortedBy { it.level.ordinal }
    val valueText = if (!hasValue) "—" else snapshot?.text ?: ""
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = Mono.copy(fontSize = 10.sp, lineHeight = 13.sp, color = colors.dim)
    val minLabel = ValueCodec.format(min, null).orEmpty()
    val maxLabel = ValueCodec.format(max, null).orEmpty()
    val labelBand = 15.dp
    val gaugeDesc = stringResource(R.string.panel_gauge_desc, minLabel, maxLabel)
    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(78.dp).testTag("panel-gauge-${widget.id}").semantics { contentDescription = gaugeDesc }) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val bandStroke = 3.dp.toPx()
            val bandGap = 4.dp.toPx()
            val outer = stroke / 2 + bandGap + bandStroke
            val baseY = size.height - labelBand.toPx() - stroke / 2f
            val radius = minOf(size.width / 2f - outer, baseY - bandStroke - bandGap - stroke / 2f).coerceAtLeast(1f)
            val center = Offset(size.width / 2f, baseY)
            fun arc(color: Color, from: Float, to: Float, r: Float, width: Float, round: Boolean) {
                if (to <= from) return
                drawArc(
                    color, startAngle = 180f + 180f * from, sweepAngle = 180f * (to - from), useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(width, cap = if (round) StrokeCap.Round else StrokeCap.Butt),
                )
            }
            arc(track, 0f, 1f, radius, stroke, round = true)
            bands.forEach { b ->
                val from = ((b.min ?: min) - min).div(span).coerceIn(0.0, 1.0).toFloat()
                val to = ((b.max ?: max) - min).div(span).coerceIn(0.0, 1.0).toFloat()
                arc(if (b.level == ThresholdLevel.ALERT) alert else warn, from, to, radius + stroke / 2 + bandGap + bandStroke / 2, bandStroke, round = false)
            }
            if (fraction > 0f) arc(arcColor, 0f, fraction, radius, stroke, round = true)
            // 量程两端：画在底轨两端的正下方
            val y = size.height - labelBand.toPx() + 2.dp.toPx()
            val l = measurer.measure(minLabel, labelStyle)
            val r = measurer.measure(maxLabel, labelStyle)
            drawText(l, topLeft = Offset((center.x - radius - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), y))
            drawText(r, topLeft = Offset((center.x + radius - r.size.width / 2f).coerceIn(0f, size.width - r.size.width), y))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = labelBand + 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                valueText,
                style = Mono.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                color = look.valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline().testTag("panel-value-${widget.id}")
                    .then(if (look.strike) Modifier.redStrike(colors.error) else Modifier),
            )
            if (point.unitLabel.isNotBlank() && hasValue) {
                Text(point.unitLabel, color = colors.muted, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.alignByBaseline())
            }
        }
    }
    Meta(look, topPadding = 4)
}

// ---- 工具 ----

internal fun Widget.displayName(point: PointDefinition) = title.ifBlank { point.name }

private fun tone(colors: ModbusColors, tone: StateTone): Color = when (tone) {
    StateTone.GREEN -> colors.rxLocal
    StateTone.AMBER -> colors.txText
    StateTone.RED -> colors.error
    StateTone.BLUE -> colors.foreign
    StateTone.GRAY -> colors.muted
}

@Composable
private fun flagText(snapshot: PointSnapshot): String =
    if (snapshot.engineering == 1.0) stringResource(R.string.panel_flag_on) else stringResource(R.string.panel_flag_off)

/** 稿面“超时”卡：值 dim，删除线是红色（Compose 的 TextDecoration 不能单独着色，自己画）。 */
private fun Modifier.redStrike(color: Color): Modifier = drawWithContent {
    drawContent()
    val y = size.height * 0.56f
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
}

internal fun addressLabel(point: PointDefinition): String {
    val prefix = when (point.area) {
        RegisterArea.COIL -> "C"
        RegisterArea.DISCRETE_INPUT -> "DI"
        RegisterArea.INPUT_REGISTER -> "IR"
        RegisterArea.HOLDING_REGISTER -> "HR"
    }
    return "$prefix${point.address}"
}

private fun rangeLabel(point: PointDefinition): String {
    val min = ValueCodec.format(point.rangeMin ?: 0.0, null)
    val max = ValueCodec.format(point.rangeMax ?: 100.0, null)
    return "$min–$max"
}

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

internal fun clock(wallMs: Long): String = ClockFormat.format(Instant.ofEpochMilli(wallMs).atZone(ZoneId.systemDefault()))
