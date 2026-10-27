package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.application.panel.ValueCodec
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.application.modiconLabel
import io.github.xingyingyuzhui.modbusmobile.ui.components.BoltIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChevronIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusSwitchVisual
import io.github.xingyingyuzhui.modbusmobile.ui.components.ToneBadge
import io.github.xingyingyuzhui.modbusmobile.ui.curve.LocalWallClock
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.elapsedText

// v3 向导（docs/design/panel-v3.html ③）的零件：步骤条、分组选类型、一行预览、可折叠块。

/** 顶部三段步骤条「类型 · 数据 · 显示」，代替右上角的“1 / 3”。改已有组件时从第 2 步开始，第 1 段画成已完成。 */
@Composable
internal fun StepBar(step: Int, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    val labels = listOf(R.string.panel_wiz_step_kind, R.string.panel_wiz_step_data, R.string.panel_wiz_step_display).map { stringResource(it) }
    val desc = stringResource(R.string.panel_wiz_step, step, labels[(step - 1).coerceIn(0, 2)])
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc }.testTag("wiz-step"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val n = i + 1
            val bar = when {
                n == step -> colors.accent
                n < step -> colors.accent.copy(alpha = 0.55f)
                else -> colors.surface3
            }
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(bar))
                Text(
                    label,
                    color = when {
                        n == step -> colors.text
                        n < step -> colors.muted
                        else -> colors.dim
                    },
                    fontSize = 11.sp, lineHeight = 15.sp,
                    fontWeight = if (n == step) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

/** 选类型（v3 3-1）：「显示读数」7 种 + 「控制设备 ⚡」2 种，紧凑横排卡（高 ≥62，44dp 示意图），一屏放下。 */
@Composable
internal fun GroupedKindPicker(selected: String?, onKind: (String) -> Unit) {
    val colors = LocalModbusColors.current
    GroupHeader(stringResource(R.string.panel_wiz_group_read), trailing = {
        Text(stringResource(R.string.panel_wiz_group_read_note), color = colors.muted, fontSize = 11.sp)
    })
    val read = listOf(WidgetKinds.NUMBER, WidgetKinds.CURVE, WidgetKinds.LAMP, WidgetKinds.STATUS_TEXT, WidgetKinds.PROGRESS, WidgetKinds.GAUGE)
    read.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { kind -> CompactKindCard(kind, kind == selected, writes = false, { onKind(kind) }, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
    CompactKindCard(WidgetKinds.REGISTER_TABLE, WidgetKinds.REGISTER_TABLE == selected, writes = false, { onKind(WidgetKinds.REGISTER_TABLE) }, Modifier.fillMaxWidth())
    GroupHeader(
        stringResource(R.string.panel_wiz_group_control),
        leading = { BoltIcon(colors.accentText) },
        trailing = { ToneBadge(stringResource(R.string.panel_wiz_group_control_tag), colors.txText, Modifier.testTag("wiz-group-control-tag")) },
        modifier = Modifier.padding(top = 6.dp).testTag("wiz-group-control"),
    )
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(WidgetKinds.SWITCH, WidgetKinds.BUTTON).forEach { kind ->
            CompactKindCard(kind, kind == selected, writes = true, { onKind(kind) }, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun GroupHeader(
    text: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalModbusColors.current
    Row(modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        leading?.invoke()
        Text(text, color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun CompactKindCard(kind: String, selected: Boolean, writes: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(14.dp)
    val (title, desc) = kindTexts(kind)
    Row(
        modifier
            .heightIn(min = KIND_CARD_MIN)
            .clip(shape)
            .background(colors.surface1)
            .border(
                if (selected) 2.dp else 1.dp,
                when {
                    selected -> colors.accent
                    writes -> colors.txText.copy(alpha = 0.35f)
                    else -> colors.line
                },
                shape,
            )
            .semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(onClick = onClick)
            .testTag("wiz-kind-$kind")
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface2), contentAlignment = Alignment.Center) {
            MiniIllustration(kind)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = colors.text, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(desc), color = colors.muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

internal val KIND_CARD_MIN = 62.dp

private fun kindTexts(kind: String): Pair<Int, Int> = when (kind) {
    WidgetKinds.CURVE -> R.string.panel_wiz_kind_curve to R.string.panel_wiz_kind_curve_desc
    WidgetKinds.LAMP -> R.string.panel_wiz_kind_lamp to R.string.panel_wiz_kind_lamp_desc
    WidgetKinds.STATUS_TEXT -> R.string.panel_wiz_kind_status to R.string.panel_wiz_kind_status_desc
    WidgetKinds.PROGRESS -> R.string.panel_wiz_kind_progress to R.string.panel_wiz_kind_progress_desc
    WidgetKinds.GAUGE -> R.string.panel_wiz_kind_gauge to R.string.panel_wiz_kind_gauge_desc
    WidgetKinds.SWITCH -> R.string.panel_wiz_kind_switch to R.string.panel_wiz_kind_switch_desc
    WidgetKinds.BUTTON -> R.string.panel_wiz_kind_button to R.string.panel_wiz_kind_button_desc
    WidgetKinds.REGISTER_TABLE -> R.string.panel_wiz_kind_table to R.string.panel_wiz_kind_table_desc
    else -> R.string.panel_wiz_kind_number to R.string.panel_wiz_kind_number_desc
}

/** 44dp 里的类型示意图（只是示意，不是数据）。 */
@Composable
private fun MiniIllustration(kind: String) {
    val colors = LocalModbusColors.current
    when (kind) {
        WidgetKinds.CURVE -> Canvas(Modifier.size(width = 34.dp, height = 22.dp)) {
            fun line(color: androidx.compose.ui.graphics.Color, ys: List<Float>) {
                val p = Path()
                ys.forEachIndexed { i, y -> val x = size.width * i / (ys.size - 1); if (i == 0) p.moveTo(x, size.height * y) else p.lineTo(x, size.height * y) }
                drawPath(p, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            line(colors.foreign, listOf(0.35f, 0.45f, 0.3f, 0.55f, 0.5f, 0.65f))
            line(colors.tx, listOf(0.75f, 0.55f, 0.65f, 0.3f, 0.35f, 0.12f))
        }
        WidgetKinds.LAMP -> Box(Modifier.size(14.dp).clip(CircleShape).background(colors.rxLocal))
        WidgetKinds.STATUS_TEXT -> Text("制冷", color = colors.foreign, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        WidgetKinds.PROGRESS -> Box(Modifier.width(32.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface3)) {
            Box(Modifier.fillMaxWidth(0.6f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(colors.accent))
        }
        WidgetKinds.GAUGE -> Canvas(Modifier.size(width = 34.dp, height = 22.dp)) {
            val stroke = 5.dp.toPx()
            val r = size.width / 2f - stroke
            val c = Offset(size.width / 2f, size.height - stroke / 2f)
            drawArc(colors.surface3, 180f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(colors.txText, 180f, 110f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        WidgetKinds.SWITCH -> Box(Modifier.scale(0.8f)) { ModbusSwitchVisual(true) }
        WidgetKinds.BUTTON -> Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(colors.wash(colors.accent))
                .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
        ) { Text("启动", color = colors.accentText, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        WidgetKinds.REGISTER_TABLE -> Text("0·236\n1·82\n2·500", style = Mono.copy(fontSize = 9.sp, lineHeight = 11.sp), color = colors.muted)
        else -> Text("23.4", style = Mono.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold), color = colors.text)
    }
}

/**
 * 第 2 步顶部的一行实时预览（v3 3-2）：“实时读数 · 预览”+ 换算后的值（原始值）+ 右侧“U1 · HR100 / N 秒前”。
 * 没连主站 / 还没读到时写“—”。只读不写（规范 39）。
 */
@Composable
internal fun PreviewBar(
    name: String,
    unitId: String,
    address: String,
    snapshot: PointSnapshot?,
    live: Boolean,
    addressLabel: String?,
    unitLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(14.dp)
    val shown = if (live) snapshot else null
    val value = when {
        shown?.raw == null -> "—"
        shown.raw is RawValue.Flag -> if ((shown.raw as RawValue.Flag).value) "ON" else "OFF"
        else -> shown.text ?: "—"
    }
    val raw = (shown?.raw as? RawValue.Integer)?.let { if (it.unsigned) it.toBigInteger().toString() else it.bits.toString() }
        ?: (shown?.raw as? RawValue.Real)?.let { ValueCodec.format(it.value, null) }
    val now = LocalWallClock.current()
    Row(
        modifier.fillMaxWidth().clip(shape).background(colors.surface1).border(1.dp, colors.accent, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp).testTag("wiz-preview-bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name.ifBlank { stringResource(R.string.panel_wiz_preview_live) }, color = colors.muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.testTag("wiz-preview-name"))
                ToneBadge(stringResource(R.string.panel_wiz_preview_badge), colors.txText, Modifier.testTag("wiz-preview-badge"))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = Mono.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold), color = colors.text, maxLines = 1, modifier = Modifier.testTag("wiz-preview-value"))
                if (shown?.raw != null && unitLabel.isNotBlank()) Text(" $unitLabel", color = colors.muted, fontSize = 12.sp)
                if (raw != null && raw != value) {
                    Text(
                        stringResource(R.string.panel_wiz_preview_raw, raw), color = colors.muted, fontSize = 12.sp,
                        modifier = Modifier.padding(start = LocalSpacing.current.xs).testTag("wiz-preview-raw"),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("U$unitId · ${addressLabel ?: address}", style = Mono.copy(fontSize = 11.sp, lineHeight = 15.sp), color = colors.dim, maxLines = 1)
            val at = shown?.updatedWallMs
            if (at != null) {
                Text(stringResource(R.string.panel_wiz_preview_age, elapsedText((now - at).coerceAtLeast(0))), style = Mono.copy(fontSize = 11.sp, lineHeight = 15.sp), color = colors.dim)
            }
        }
    }
}

/**
 * 可折叠的一行（v3「换算 × 0.1 + 0 ›」「轮询周期 1000 ms ›」）：收起时只有标题 + 摘要（热区 ≥48），点开显示 [content]。
 * [forceOpen]：里面有要显示的错误时自动展开（规范 63：错误要看得到）。
 */
@Composable
internal fun CollapsibleBlock(
    title: String,
    summary: String,
    tag: String,
    forceOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalModbusColors.current
    var open by rememberSaveable(tag) { mutableStateOf(false) }
    val expanded = open || forceOpen
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(colors.surface1).border(1.dp, colors.line, shape).testTag(tag),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    stateDescription = if (expanded) "已展开" else "已收起"
                }
                .clickable { open = !expanded }
                .padding(horizontal = 12.dp)
                .testTag("$tag-toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = colors.text, fontSize = 14.sp)
            Text(summary, style = Mono.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.muted, maxLines = 1, modifier = Modifier.weight(1f).testTag("$tag-summary"))
            ChevronIcon(colors.dim, expanded)
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** 地址框后面的“= 40101”（对照设备手册用）。 */
@Composable
internal fun modiconHint(area: io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea, address: String): String? =
    address.trim().toIntOrNull()?.takeIf { it in 0..65535 }?.let { stringResource(R.string.panel_wiz_address_modicon, modiconLabel(area, it)) }
