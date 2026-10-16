package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.ui.components.ToneBadge
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 面板组件卡（稿面 b2-1 `.w`）：圆角 16、1px 边、内边距 10/12（border-box，内容离外缘 13/11）、
 * 名称 12.5 muted + 右上 badge、值 mono 26/600 + 单位 12、meta mono 10.5 dim。
 * 质量态：过期 = 值 muted；超时 / 错误 = 值 dim + 红色删除线 + 红 badge + 失败说明（取代 meta）。
 */
@Composable
fun WidgetCard(widget: Widget, snapshot: PointSnapshot?, modifier: Modifier = Modifier) {
    val point = widget.point ?: return
    val look = rememberLook(widget, point, snapshot)
    CardFrame(modifier, "panel-card-${widget.id}") {
        when (widget.kind) {
            WidgetKinds.LAMP -> LampBody(widget, point, snapshot, look)
            WidgetKinds.STATUS_TEXT -> StatusBody(widget, point, snapshot, look)
            WidgetKinds.PROGRESS -> ProgressBody(widget, point, snapshot, look)
            else -> NumberBody(widget, point, snapshot, look)
        }
    }
}

@Composable
private fun CardFrame(modifier: Modifier, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    Column(
        modifier
            .fillMaxSize()
            .testTag(tag)
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
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
    val colors = LocalModbusColors.current
    Text(
        look.meta,
        style = Mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
        color = if (look.metaProblem) colors.error else colors.dim,
        modifier = Modifier.padding(top = topPadding.dp).testTag("panel-meta"),
    )
}

@Composable
private fun NumberBody(widget: Widget, point: PointDefinition, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(widget.displayName(point), look.badge)
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
private fun LampBody(widget: Widget, point: PointDefinition, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(widget.displayName(point), look.badge)
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
private fun StatusBody(widget: Widget, point: PointDefinition, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(widget.displayName(point), look.badge)
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
    Meta(look.copy(meta = meta))
}

@Composable
private fun ProgressBody(widget: Widget, point: PointDefinition, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    val min = point.rangeMin ?: 0.0
    val max = point.rangeMax ?: 100.0
    val value = snapshot?.engineering
    val fraction = if (value == null || snapshot.raw == null) 0f else ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
    val valueText = if (snapshot?.raw == null) "—" else (snapshot.text ?: "") + point.unitLabel.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    NameRow(widget.displayName(point), look.badge) {
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

// ---- 工具 ----

private fun Widget.displayName(point: PointDefinition) = title.ifBlank { point.name }

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
