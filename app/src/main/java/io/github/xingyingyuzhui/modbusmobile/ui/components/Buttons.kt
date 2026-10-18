package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusMotion
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ButtonRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.SmallRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSize
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusType
import io.github.xingyingyuzhui.modbusmobile.ui.theme.StrokeControl

/*
 * 按钮（设计规范_v2 §6.0 状态矩阵、§6.1、§7.2；ux 尺寸标注 v1 §0.1 §1–§3）。T-20261018-and-07（CL-02）：
 * - loading：宽度锁定为加载前的宽度；内容换进度圈 + 进行时文案；不可点；stateDescription = 进行时文案（G-01）。
 *   显示时机（§7.2）：300ms 内结束的不显示进度圈；一旦显示至少 500ms；出现 / 消失用 motion.standard 淡入淡出。
 *   loading = true 那一刻起就不可点（不等进度圈出现），连点只触发 1 次（A2）。
 * - 禁用：不用整体 alpha；主按钮 surface3 + dim，次按钮边框 line + 文字 dim，semantics disabled（G-20）。
 * - 按下：ripple；主按钮（含 danger、SmallButton primary）再叠 8% 黑（深色）/ 8% 白（浅色），不缩放不位移。
 * - 聚焦：1.5dp accent.border 环画在外框内侧，圆角同组件；底色是 accent 的按钮在环内再留 2dp bg 色间隔。
 * - danger：dangerFill + onDanger（§4.3）。
 * 新参数都有默认值，现有调用不改也能编译。
 */

/**
 * 按钮尺寸（§6.1、尺寸标注 v1 §1 §3）。都不随密度变（§2.3：紧凑只改组件之间的间距）。
 * 间距类取自 tokens_v2 spacing 的标准档（page 16 / card 12 / s 8 / xs 4）；最小宽 88 / 64 和聚焦间隔 2 在 tokens_v2 里没有对应 token，
 * 按规范数值只在这里定义一次（改动说明里列出，建议 ux 下一版 tokens 补上）。
 */
internal object ButtonMetrics {
    val paddingH: Dp = ModbusSpacing.Comfortable.page
    val paddingHCompact: Dp = ModbusSpacing.Comfortable.card
    val paddingHSmall: Dp = ModbusSpacing.Comfortable.card
    val minWidth: Dp = 88.dp
    val minWidthCompact: Dp = 64.dp
    val minWidthSmall: Dp = MinTouch
    /** 进度圈和文字的间距：主 / 次按钮 8，SmallButton 4。 */
    val progressGap: Dp = ModbusSpacing.Comfortable.s
    val progressGapSmall: Dp = ModbusSpacing.Comfortable.xs
    /** 底色是 accent 时，聚焦环和外框之间的 bg 色间隔。 */
    val focusGap: Dp = 2.dp
}

/** §7.2：在途不到这么久就结束的，不显示进度圈。 */
const val LOADING_SHOW_DELAY_MS = 300L

/** §7.2：进度圈一旦出现，至少显示这么久（避免一闪而过）。 */
const val LOADING_MIN_SHOW_MS = 500L

/** §6.0：主按钮按下时叠加层的不透明度（8%）。 */
private const val PRESS_OVERLAY_ALPHA = 0.08f

/**
 * loading 的显示时机（§7.2），纯协程逻辑，方便纯 JVM 单测：
 * [loading] 变 true 后过 [showDelayMs] 还在加载才报 true；报 true 之后至少过 [minShowMs] 才会报 false。
 * 进度圈显示期间 loading 先变 false 又变 true，保持显示，不闪。
 */
internal suspend fun gateLoading(
    loading: Flow<Boolean>,
    showDelayMs: Long = LOADING_SHOW_DELAY_MS,
    minShowMs: Long = LOADING_MIN_SHOW_MS,
    onShown: (Boolean) -> Unit,
) = coroutineScope {
    var shown = false
    var hold: Job? = null
    loading.collectLatest { busy ->
        if (busy) {
            if (!shown) {
                delay(showDelayMs)
                shown = true
                onShown(true)
                hold = launch { delay(minShowMs) }
            }
        } else if (shown) {
            hold?.join()
            shown = false
            onShown(false)
        }
    }
}

/** 进度圈此刻是否显示（[gateLoading] 的 Compose 包装）。 */
@Composable
private fun rememberLoadingShown(loading: Boolean): Boolean {
    var shown by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(loading)
    LaunchedEffect(Unit) { gateLoading(snapshotFlow { latest }) { shown = it } }
    return shown
}

/** 加载时锁定宽度：记下最近一次非加载状态的宽度，[busy] 期间固定为它（尺寸标注 §1：宽度锁定为加载前的宽度）。 */
@Composable
private fun Modifier.lockWidthWhile(busy: Boolean): Modifier {
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    return this
        .onSizeChanged { if (!busy) width = it.width }
        .then(if (busy && width > 0) Modifier.width(with(density) { width.toDp() }) else Modifier)
}

/** 按钮内的进度圈（§7.2 / size.progress 16，SmallButton 12；线宽 2）。 */
@Composable
internal fun ButtonProgress(color: Color, size: Dp = ModbusSize.progress) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        strokeWidth = ModbusSize.progressStroke,
    )
}

/**
 * 聚焦环（§6.0）：键盘 / 读屏聚焦时，在外框内侧画 1.5dp accent.border，圆角 [radius]。
 * [gapColor] 非空（底色是 accent 的按钮）时，环内再画 2dp 的间隔色，让环看得出来。画在内容之上，不改尺寸。
 */
@Composable
internal fun Modifier.focusRing(interaction: MutableInteractionSource, radius: Dp, gapColor: Color? = null): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    if (!focused) return this
    val ring = LocalModbusColors.current.accentBorder
    return drawWithContent {
        drawContent()
        val w = StrokeControl.toPx()
        val r = radius.toPx()
        drawRoundRect(
            ring,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius((r - w / 2).coerceAtLeast(0f)),
            style = Stroke(w),
        )
        if (gapColor != null) {
            val g = ButtonMetrics.focusGap.toPx()
            val inset = w + g / 2
            drawRoundRect(
                gapColor,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f)),
                style = Stroke(g),
            )
        }
    }
}

/** 主按钮按下叠加层：深色叠 8% 黑，浅色叠 8% 白（§6.0 / 尺寸标注 §1）。 */
@Composable
private fun Modifier.pressOverlay(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    if (!pressed) return this
    val dark = LocalModbusColors.current.isDark
    return background((if (dark) Color.Black else Color.White).copy(alpha = PRESS_OVERLAY_ALPHA))
}

/** 按钮的通用语义：角色 + 禁用 + 在途状态（§11.2“在途：stateDescription 正在写入”）。 */
private fun Modifier.buttonSemantics(enabled: Boolean, loading: Boolean, loadingText: String): Modifier =
    semantics {
        role = Role.Button
        if (!enabled) disabled()
        if (loading) stateDescription = loadingText
    }

/** 按钮内容：正常 = 图标（可选）+ 文字；进度圈显示时 = 进度圈 + [progressGap] + 进行时文案，整体居中。 */
@Composable
private fun ButtonContent(
    text: String,
    shown: Boolean,
    loadingText: String,
    color: Color,
    style: TextStyle,
    progressSize: Dp = ModbusSize.progress,
    progressGap: Dp = ButtonMetrics.progressGap,
    icon: (@Composable () -> Unit)? = null,
) {
    Crossfade(targetState = shown, animationSpec = tween(ModbusMotion.STANDARD), label = "button-loading") { busy ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                ButtonProgress(color, progressSize)
                Spacer(Modifier.width(progressGap))
            } else if (icon != null) {
                icon()
                // 图标（≤16dp）和文字：space.xs（tokens_v2 spacing.xs 用途）。
                Spacer(Modifier.width(ModbusSpacing.Comfortable.xs))
            }
            Text(
                if (busy) loadingText else text,
                color = color,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 主按钮。[compact] = 紧凑档：视觉高 44（触控补到 48）、左右 12、最小宽 64、字 buttonCompact 14/20。默认高 52、左右 16、最小宽 88、字 button 16/24。
 * [loading] 时显示 [loadingText]（默认“正在处理…”；写入请传 R.string.btn_loading_write“正在写入…”）。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    /** 危险操作（广播写入、删除）：dangerFill + onDanger。 */
    danger: Boolean = false,
    loading: Boolean = false,
    loadingText: String? = null,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(ButtonRadius)
    val interaction = remember { MutableInteractionSource() }
    val busyText = loadingText ?: stringResource(R.string.btn_loading_generic)
    val shown = rememberLoadingShown(loading)
    val busy = loading || shown
    // loading 时保持填充色（不是禁用态），只是不可点。
    val fill = when {
        !enabled -> colors.surface3
        danger -> colors.dangerFill
        else -> colors.accent
    }
    val content = when {
        !enabled -> colors.dim
        danger -> colors.onDanger
        else -> colors.onAccent
    }
    Box(
        modifier
            .lockWidthWhile(shown)
            .fillMaxWidth()
            .then(if (compact) Modifier.minimumInteractiveComponentSize() else Modifier)
            .height(if (compact) ModbusSize.buttonCompact else ModbusSize.button)
            .widthIn(min = if (compact) ButtonMetrics.minWidthCompact else ButtonMetrics.minWidth)
            .focusRing(interaction, ButtonRadius, gapColor = if (enabled) colors.bg else null)
            .clip(shape)
            .background(fill)
            .pressOverlay(interaction)
            .buttonSemantics(enabled, busy, busyText)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled && !busy,
                onClick = onClick,
            )
            .padding(horizontal = if (compact) ButtonMetrics.paddingHCompact else ButtonMetrics.paddingH),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(text, shown, busyText, content, if (compact) ModbusType.buttonCompact else ModbusType.button)
    }
}

/**
 * 次按钮：透明底 + 1.5dp line 边框（内侧）+ text 文字；尺寸同 [PrimaryButton]。[icon] 画在文字前；
 * [active] 表示开关已开（选中态：accent wash 底 + accent.border + accent.text）。聚焦时 accent.border 环替换 line（同位置同粗细）。
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    fontSize: TextUnit? = null,
    active: Boolean = false,
    /** 固定高度（和同一行的主按钮等高，例如确认弹层的 52）。 */
    height: Dp? = null,
    loading: Boolean = false,
    loadingText: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(ButtonRadius)
    val interaction = remember { MutableInteractionSource() }
    val busyText = loadingText ?: stringResource(R.string.btn_loading_generic)
    val shown = rememberLoadingShown(loading)
    val busy = loading || shown
    val on = active && enabled
    val baseStyle = if (compact) ModbusType.buttonCompact else ModbusType.button
    Box(
        modifier
            .lockWidthWhile(shown)
            .fillMaxWidth()
            .then(
                when {
                    height != null -> Modifier.height(height)
                    compact -> Modifier.minimumInteractiveComponentSize().height(ModbusSize.buttonCompact)
                    else -> Modifier.height(ModbusSize.button)
                },
            )
            .widthIn(min = if (compact) ButtonMetrics.minWidthCompact else ButtonMetrics.minWidth)
            .focusRing(interaction, ButtonRadius)
            .clip(shape)
            .background(if (on) colors.wash(colors.accent) else Color.Transparent)
            // 禁用：边框 line、文字 dim（§6.1，原只变文字）
            .border(StrokeControl, if (on) colors.accentBorder else colors.line, shape)
            .buttonSemantics(enabled, busy, busyText)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled && !busy,
                onClick = onClick,
            )
            .padding(horizontal = if (compact) ButtonMetrics.paddingHCompact else ButtonMetrics.paddingH),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(
            text = text,
            shown = shown,
            loadingText = busyText,
            color = if (!enabled) colors.dim else if (on) colors.accentText else colors.text,
            style = if (fontSize != null) baseStyle.copy(fontSize = fontSize) else baseStyle,
            icon = icon,
        )
    }
}

/**
 * 小按钮：视觉高 32、最小宽 48、左右 12、圆角 radius.small 8、labelStrong 12/16 SemiBold；
 * 触控用 minimumInteractiveComponentSize 宽高都补到 48。primary = accent 底；其它 = surface2 底（§6.1 v2，原透明 + 描边）。
 */
@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 强调色填充（页头的“完成”）。 */
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String? = null,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(SmallRadius)
    val interaction = remember { MutableInteractionSource() }
    val busyText = loadingText ?: stringResource(R.string.btn_loading_generic)
    val shown = rememberLoadingShown(loading)
    val busy = loading || shown
    val fill = when {
        primary -> if (enabled) colors.accent else colors.surface3
        else -> colors.surface2
    }
    val content = when {
        !enabled -> colors.dim
        primary -> colors.onAccent
        else -> colors.text
    }
    Box(
        modifier
            .lockWidthWhile(shown)
            .minimumInteractiveComponentSize()
            .height(ModbusSize.buttonSmall)
            .widthIn(min = ButtonMetrics.minWidthSmall)
            .focusRing(interaction, SmallRadius, gapColor = if (primary && enabled) colors.bg else null)
            .clip(shape)
            .background(fill)
            .then(if (primary) Modifier.pressOverlay(interaction) else Modifier)
            .buttonSemantics(enabled, busy, busyText)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled && !busy,
                onClick = onClick,
            )
            .padding(horizontal = ButtonMetrics.paddingHSmall),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(
            text, shown, busyText, content,
            ModbusType.labelStrong,
            progressSize = ModbusSize.progressSmall,
            progressGap = ButtonMetrics.progressGapSmall,
        )
    }
}

/**
 * 行内的文字链接（“添加”“清空”）。视觉是一行 labelStrong 12/16，内边距 0，触控区至少 48×48，文字靠右。
 * 禁用 = dim + 不可点 + semantics disabled（G-21）。按下是 48 的圆形 ripple；聚焦在 48 触控盒外沿画 1.5dp 环，圆角 8。
 * 默认颜色仍是 muted（现有调用不变）；规范默认 accent.text 的地方由调用方传 [color]。
 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalModbusColors.current.muted,
    enabled: Boolean = true,
) {
    val colors = LocalModbusColors.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .sizeIn(minWidth = MinTouch, minHeight = MinTouch)
            .focusRing(interaction, SmallRadius)
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(bounded = false, radius = MinTouch / 2),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(text, color = if (enabled) color else colors.dim, style = ModbusType.labelStrong)
    }
}

// ---- 预览：每种按钮 默认 / 禁用 / 加载 / 选中(active) / danger，亮、暗、大字体 1.3x 各一（§6.0；按下、聚焦需真机截图） ----

@Preview(name = "按钮 深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonsDark() = ButtonStatesPreview(dark = true)

@Preview(name = "按钮 浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun ButtonsLight() = ButtonStatesPreview(dark = false)

@Preview(name = "按钮 浅色 1.3x", fontScale = 1.3f)
@Composable
private fun ButtonsLarge() = ButtonStatesPreview(dark = false)

@Composable
private fun ButtonStatesPreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        val colors = LocalModbusColors.current
        val spacing = LocalSpacing.current
        val write = stringResource(R.string.btn_preview_write)
        val writing = stringResource(R.string.btn_loading_write)
        Column(
            Modifier.background(colors.bg).padding(spacing.page),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            PrimaryButton(write, {})
            PrimaryButton(write, {}, enabled = false)
            PrimaryButton(write, {}, loading = true, loadingText = writing)
            PrimaryButton(stringResource(R.string.btn_preview_broadcast), {}, danger = true)
            PrimaryButton(write, {}, compact = true)
            SecondaryButton(write, {})
            SecondaryButton(write, {}, enabled = false)
            SecondaryButton(write, {}, loading = true, loadingText = writing)
            SecondaryButton(write, {}, active = true)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                SmallButton(write, {})
                SmallButton(write, {}, primary = true)
                SmallButton(write, {}, enabled = false)
                SmallButton(write, {}, loading = true, loadingText = writing)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                TextLink(write, {}, color = colors.accentText)
                TextLink(write, {}, enabled = false)
            }
        }
    }
}
