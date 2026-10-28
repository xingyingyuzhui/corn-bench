package dev.corn.flashing.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.corn.flashing.R
import dev.corn.flashing.core.BoardOutcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Building blocks that mirror the mockup CSS classes in gen.py (.card, .chip, .btn, .kv, .lbl …).

@Composable
fun T(
    text: String, color: Color = CornColors.Text, size: TextUnit = 13.sp, weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false, modifier: Modifier = Modifier, align: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(text, modifier, color = color, fontSize = size, fontWeight = weight, textAlign = align, maxLines = maxLines,
        style = TextStyle(fontFamily = if (mono) Mono else null, lineHeight = if (lineHeight == TextUnit.Unspecified) size * 1.35f else lineHeight))
}

@Composable
fun Ico(icon: ImageVector, color: Color = CornColors.Dim, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Icon(icon, null, modifier.size(size), tint = color)
}

@Composable
fun CornCard(
    modifier: Modifier = Modifier,
    color: Color = CornColors.Surface1,
    border: Color = CornColors.Line,
    padding: Dp = 14.dp,
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color)
            .border(1.dp, border, RoundedCornerShape(18.dp)).padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing), content = content,
    )
}

/**
 * 语义色调。第二期起颜色随主题（深浅 / 强调色）变化，所以枚举只保留语义名，
 * 具体颜色在 Composable 里用 [colors] 解析；枚举常量本身不再持有写死的颜色。
 */
enum class ChipTone { NEUTRAL, OK, WARN, BAD, INFO }

/** 返回 (前景, 浅底) 两个颜色。只能在 Compose 作用域里调用。 */
@Composable
@ReadOnlyComposable
fun ChipTone.colors(): Pair<Color, Color> = when (this) {
    ChipTone.NEUTRAL -> CornColors.Dim to CornColors.Surface2
    ChipTone.OK -> CornColors.Leaf to CornColors.LeafContainer
    ChipTone.WARN -> CornColors.Corn to CornColors.CornContainer
    ChipTone.BAD -> CornColors.Red to CornColors.RedContainer
    ChipTone.INFO -> CornColors.Blue to CornColors.BlueContainer
}

/** `.chip`: 24 high, pill, 11.5 / 600, optional dot or leading icon. */
@Composable
fun Chip(text: String, tone: ChipTone = ChipTone.NEUTRAL, dot: Boolean = false, icon: ImageVector? = null,
         modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val (fg, bg) = tone.colors()
    Row(
        modifier.height(24.dp).clip(RoundedCornerShape(99.dp)).background(bg)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        icon?.let { Ico(it, fg, 12.dp) }
        T(text, fg, 11.5.sp, FontWeight.SemiBold, maxLines = 1)
    }
}

/** Format badge: HEX corn · BIN blue · ELF leaf. */
@Composable
fun FormatBadge(format: String, width: Dp? = null) {
    val tone = when (format) { "HEX" -> ChipTone.WARN; "BIN" -> ChipTone.INFO; else -> ChipTone.OK }
    val (fg, bg) = tone.colors()
    Box(Modifier.let { if (width != null) it.width(width) else it }.clip(RoundedCornerShape(6.dp)).background(bg)
        .padding(horizontal = 7.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
        T(format, fg, 10.5.sp, FontWeight.Bold, mono = true)
    }
}

/** 「模拟」 marker for screens whose feature is not wired to hardware yet. */
@Composable
fun SimMark() = Chip("模拟", ChipTone.INFO)

@Composable
fun CornButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, danger: Boolean = false, enabled: Boolean = true,
    height: Int = 52, icon: ImageVector? = null, subtitle: String? = null, radius: Int = 16,
    textColor: Color? = null,
) {
    val shape = RoundedCornerShape(radius.dp)
    val bg = when { !enabled -> CornColors.Surface2; primary -> CornColors.Corn; danger -> Color.Transparent; else -> CornColors.Surface2 }
    val fg = textColor ?: when { !enabled -> CornColors.Mute; primary -> CornColors.CornOn; danger -> CornColors.Red; else -> CornColors.Text }
    Box(
        modifier.height(height.dp).clip(shape).background(bg)
            .let { if (danger) it.border(1.5.dp, CornColors.Red.copy(alpha = .55f), shape) else it }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon?.let { Ico(it, fg, if (height >= 60) 22.dp else 18.dp) }
                T(label, fg, if (height >= 60) 18.sp else 15.sp, FontWeight.Bold, maxLines = 1)
            }
            subtitle?.let { T(it, fg.copy(alpha = .75f), 11.5.sp, FontWeight.Medium) }
        }
    }
}

/** 36×36 rounded icon button (`.iconbtn`). */
@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit, color: Color = CornColors.Text) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(CornColors.Surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Ico(icon, color, 18.dp) }
}

@Composable
fun AppIcon(size: Dp = 30.dp) {
    Image(painterResource(R.drawable.corn_logo), null, Modifier.size(size))
}

/** `.top`: app icon + h1 + sub, right slot. */
@Composable
fun TopBar(title: String, sub: String, right: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // 只有模式根页用 TopBar：在 Corn Bench 外壳里这里是模式切换器（玉米 + ▾，48dp 热区），否则是原来的图标。
        val switcher = dev.corn.ui.mode.LocalModeSwitcher.current
        if (switcher != null) dev.corn.ui.mode.BrandSwitcher(switcher) else AppIcon()
        // 标题 + 副标题合成一个 heading 节点（读屏念“烧录，离线烧录 · …”）；左边图标没有外壳时只是装饰。
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            T(title, size = 19.sp, weight = FontWeight.Bold, maxLines = 1)
            T(sub, CornColors.Dim, 11.5.sp, maxLines = 1)
        }
        right()
    }
}

/** back_top: back iconbtn + 17sp title + sub, right slot. */
@Composable
fun BackTop(title: String, sub: String? = null, onBack: () -> Unit, right: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBtn(Lucide.Back, onBack)
        Column(Modifier.weight(1f)) {
            T(title, size = 17.sp, weight = FontWeight.Bold, maxLines = 1)
            sub?.let { T(it, CornColors.Dim, 11.5.sp, maxLines = 1) }
        }
        right()
    }
}

@Composable
fun CornBar(progress: Float, modifier: Modifier = Modifier, height: Int = 6, color: Color = CornColors.Corn) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(99.dp)).background(CornColors.Surface3)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(RoundedCornerShape(99.dp)).background(color))
    }
}

/** `.lbl`: 11sp, uppercase, mute, letter-spacing .8. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = CornColors.Mute, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}

/** `.kv` row: 7dp vertical padding, line separator. */
@Composable
fun KeyValue(label: String, value: String, mono: Boolean = false, valueColor: Color = CornColors.Text, last: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            T(label, CornColors.Dim, 13.sp, modifier = Modifier.padding(end = 12.dp))
            Spacer(Modifier.weight(1f))
            T(value, valueColor, if (mono) 12.sp else 13.sp, FontWeight.Medium, mono = mono, align = TextAlign.End)
        }
        if (!last) DividerLine()
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(CornColors.Line))
}

/** `.seg`: s2 track radius 12, active s3. */
@Composable
fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 38.dp) {
    Row(modifier.height(height).clip(RoundedCornerShape(12.dp)).background(CornColors.Surface2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp))
                .background(if (active) CornColors.Surface3 else Color.Transparent).clickable { onSelect(index) },
                contentAlignment = Alignment.Center) {
                T(label, if (active) CornColors.Text else CornColors.Dim, 13.sp, if (active) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, enabled: Boolean = true, min: Int = 1, max: Int = 100_000) {
    Row(Modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(CornColors.Surface2),
        verticalAlignment = Alignment.CenterVertically) {
        StepperButton("−", enabled && value > min) { onChange(value - 1) }
        T("$value", size = 15.sp, weight = FontWeight.Bold, mono = true, modifier = Modifier.widthIn(min = 44.dp), align = TextAlign.Center)
        StepperButton("+", enabled && value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        T(label, if (enabled) CornColors.Text else CornColors.Mute, 18.sp, FontWeight.Bold)
    }
}

/** 40×24 toggle (`.tog`). */
@Composable
fun CornToggle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)? = null) {
    Box(Modifier.size(40.dp, 24.dp).clip(RoundedCornerShape(99.dp)).background(if (checked) CornColors.Corn else CornColors.Surface3)
        .let { m -> onCheckedChange?.let { m.clickable { it(!checked) } } ?: m }.padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(if (checked) CornColors.CornOn else CornColors.Dim))
    }
}

/** Dashed-border card (`.card` with border-style dashed). */
@Composable
fun DashedNote(modifier: Modifier = Modifier, border: Color = CornColors.Line, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().drawBehind {
            drawRoundRect(border, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                cornerRadius = CornerRadius(18.dp.toPx()))
        }.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), content = content,
    )
}

/** `.banner`: tinted card with an icon, title and body. */
@Composable
fun Banner(title: String, body: String? = null, tone: ChipTone = ChipTone.BAD, icon: ImageVector = Lucide.Alert,
           action: (@Composable () -> Unit)? = null) {
    val (fg, bg) = tone.colors()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, fg.copy(alpha = .25f), RoundedCornerShape(16.dp))
        .padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ico(icon, fg, 18.dp, Modifier.padding(top = 1.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(title, fg, 13.5.sp, FontWeight.Bold)
            body?.let { T(it, CornColors.Dim, 12.sp) }
        }
        action?.invoke()
    }
}

/** Numbered troubleshooting hint; a done hint shows a leaf check. */
@Composable
fun HintRow(index: String, title: String, subtitle: String, done: Boolean = false, last: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (done) CornColors.Leaf else CornColors.Surface3), contentAlignment = Alignment.Center) {
                if (done) Ico(Lucide.Check, CornColors.CornOn, 13.dp) else T(index, CornColors.Dim, 11.sp, FontWeight.Bold, mono = true)
            }
            Column(Modifier.weight(1f)) {
                T(title, if (done) CornColors.Dim else CornColors.Text, 13.5.sp, FontWeight.SemiBold)
                T(subtitle, CornColors.Mute, 11.5.sp)
            }
        }
        if (!last) DividerLine()
    }
}

/** Big circular illustration (results, empty states). */
@Composable
fun RoundIcon(icon: ImageVector, color: Color, background: Color, size: Int = 64, iconSize: Int = 28, ring: Boolean = false) {
    Box(Modifier.size((size + if (ring) 26 else 0).dp).clip(CircleShape).background(if (ring) background else Color.Transparent),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(size.dp).clip(CircleShape).background(if (ring) color else background), contentAlignment = Alignment.Center) {
            Ico(icon, if (ring) CornColors.CornOn else color, iconSize.dp)
        }
    }
}

/** Empty-state illustration: 96 ring with a 64 rounded box. */
@Composable
fun Illo(icon: ImageVector, color: Color? = null) {
    Box(Modifier.size(96.dp).clip(CircleShape).background(CornColors.Surface2), contentAlignment = Alignment.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(CornColors.Surface3), contentAlignment = Alignment.Center) {
            Ico(icon, color ?: CornColors.Corn, 30.dp)
        }
    }
}

/** Red 135° stripes used for reserved areas (`.hatch`). 颜色随主题，所以是 @Composable。 */
@Composable
fun Modifier.hatch(): Modifier {
    // drawBehind 的 lambda 不是 Composable，先把颜色读出来再进绘制作用域。
    val hatchColor = CornColors.Red
    return drawBehind {
        drawRect(hatchColor.copy(alpha = .10f))
        clipRect {
            val step = 7.dp.toPx()
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(hatchColor.copy(alpha = .45f), Offset(x, size.height), Offset(x + size.height, 0f), 2.dp.toPx())
                x += step
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String, hatch: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).let { if (hatch) it.hatch() else it.background(color) })
        T(label, CornColors.Dim, 11.sp)
    }
}

@Composable
fun StatCell(value: String, label: String, color: Color = CornColors.Text, size: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        T(value, color, size, FontWeight.Bold, mono = true)
        T(label, CornColors.Dim, 11.sp)
    }
}

@Composable
fun VDivider(height: Dp = 30.dp) { Box(Modifier.width(1.dp).height(height).background(CornColors.Line)) }

/** Row with title, subtitle and trailing control (settings / rules). */
@Composable
fun SRow(title: String, sub: String? = null, last: Boolean = false, onClick: (() -> Unit)? = null,
         leading: (@Composable () -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Column {
        Row(Modifier.fillMaxWidth().let { m -> onClick?.let { m.clickable(onClick = it) } ?: m }.padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                T(title, size = 14.sp, weight = FontWeight.SemiBold)
                sub?.let { T(it, CornColors.Dim, 11.5.sp) }
            }
            trailing()
        }
        if (!last) DividerLine()
    }
}

@Composable
fun Chev() = Ico(Lucide.Chev, CornColors.Mute, 16.dp)

/** Square checkbox used in the station grid. */
@Composable
fun CheckBox(checked: Boolean) {
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (checked) CornColors.Corn else Color.Transparent)
        .let { if (!checked) it.border(1.5.dp, CornColors.Mute, RoundedCornerShape(6.dp)) else it }, contentAlignment = Alignment.Center) {
        if (checked) Ico(Lucide.Check, CornColors.CornOn, 13.dp)
    }
}

/** Ring progress (screen 02): 180dp, r74, stroke 12. */
@Composable
fun Ring(progress: Float, size: Dp = 180.dp, color: Color? = null, content: @Composable BoxScope.() -> Unit) {
    // Canvas 的绘制 lambda 不是 Composable，颜色先在 Composable 作用域里取好。
    val track = CornColors.Surface3
    val arc = color ?: CornColors.Corn
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = size.toPx() / 2 - 74.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(arc, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        content()
    }
}

/** Last 30 boards, 15 per row, older cells fade out (screen 13). */
@Composable
fun OutcomeStrip(outcomes: List<BoardOutcome>, modifier: Modifier = Modifier) {
    val cells = outcomes.takeLast(30)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (0 until 2).forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 15).forEach { c ->
                    val i = r * 15 + c
                    val outcome = cells.getOrNull(i)
                    Box(Modifier.weight(1f).height(18.dp)
                        .alpha(if (outcome != null) 0.35f + 0.65f * (i + 1) / cells.size else 1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(outcome?.let { outcomeColor(it) } ?: CornColors.Surface2))
                }
            }
        }
    }
}

@Composable
@ReadOnlyComposable
fun outcomeColor(outcome: BoardOutcome): Color = when (outcome) {
    BoardOutcome.SUCCEEDED -> CornColors.Leaf
    BoardOutcome.FAILED -> CornColors.Red
    BoardOutcome.INTERRUPTED -> CornColors.Mute
    BoardOutcome.SKIPPED_DUPLICATE -> CornColors.Corn
}

fun statusLabel(status: String) = when (status) {
    "SIMULATED" -> "成功"
    "SUCCEEDED" -> "成功"
    "FAILED" -> "失败"
    "CANCELLED" -> "中止"
    "INTERRUPTED" -> "中断"
    "SKIPPED" -> "跳过重复"
    "RUNNING" -> "执行中"
    else -> "排队中"
}

@Composable
@ReadOnlyComposable
fun statusColor(status: String): Color = when (status) {
    "SIMULATED", "SUCCEEDED" -> CornColors.Leaf
    "FAILED" -> CornColors.Red
    "CANCELLED", "INTERRUPTED" -> CornColors.Dim
    "SKIPPED" -> CornColors.Corn
    else -> CornColors.Mute
}

fun statusTone(status: String) = when (status) {
    "SIMULATED", "SUCCEEDED" -> ChipTone.OK
    "FAILED" -> ChipTone.BAD
    "CANCELLED", "INTERRUPTED", "SKIPPED" -> ChipTone.WARN
    else -> ChipTone.NEUTRAL
}

fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
fun formatHm(millis: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
fun formatDateTime(millis: Long): String = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(millis))
fun formatDate(millis: Long): String = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(millis))

fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    return when {
        minutes < 1 -> "${seconds} 秒"
        minutes < 60 -> "${minutes} 分 ${seconds % 60} 秒"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分"
    }
}

fun secs(millis: Long): String = "%.1f s".format(millis / 1000f)
fun percent(value: Float?): String = value?.let { "${(it * 1000).toInt() / 10f}%" } ?: "—"
fun shortSha(sha: String) = if (sha.length > 12) sha.take(4) + "…" + sha.takeLast(4) else sha
fun kb(bytes: Long) = if (bytes < 1024) "$bytes B" else "%.1f KB".format(bytes / 1024f)

/** UID shown in 4-char groups like the mockup (35FF D605 …). */
fun shortUid(uid: String?) = when {
    uid == null -> "—"
    else -> uid.removePrefix("DEMO-").chunked(4).joinToString(" ")
}
