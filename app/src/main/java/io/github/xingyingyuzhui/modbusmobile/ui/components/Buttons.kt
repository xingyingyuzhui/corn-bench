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
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ButtonRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.FieldRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSize
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusType
import io.github.xingyingyuzhui.modbusmobile.ui.theme.StrokeControl

/*
 * 按钮（设计规范_v1 §6.0 状态矩阵、§6.1）。T-20261018-and-07（CL-02）：
 * - loading：宽度不变，内容换 16dp 进度圈 + 进行时文案，不可点，stateDescription = 进行时文案（G-01）。
 * - 禁用：不用整体 alpha；主按钮 surface3 + dim，次按钮边框 line + 文字 dim，semantics disabled（G-20）。
 * - 聚焦：1.5dp accent.border 外描边，圆角同组件（§6.0）。
 * - danger：dangerFill + onDanger（§4.3，原 error 直填 + 白字）。
 * 新参数都有默认值，现有调用不改也能编译、外观不变（除规范明确修改的 danger / 禁用 / 字号档）。
 */

/** 按钮内的进度圈（§7.2 / §8.1 size.progress）。 */
@Composable
internal fun ButtonProgress(color: Color, size: Dp = ModbusSize.progress) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        strokeWidth = ModbusSize.progressStroke,
    )
}

/** 聚焦描边：键盘 / 读屏聚焦时 1.5dp accent.border（§6.0）。 */
@Composable
private fun Modifier.focusRing(interaction: MutableInteractionSource, shape: Shape): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    val colors = LocalModbusColors.current
    return if (focused) this.border(StrokeControl, colors.accentBorder, shape) else this
}

/** 按钮的通用语义：角色 + 禁用 + 在途状态（§11.2“在途：stateDescription 正在写入”）。 */
private fun Modifier.buttonSemantics(enabled: Boolean, loading: Boolean, loadingText: String): Modifier =
    semantics {
        role = Role.Button
        if (!enabled) disabled()
        if (loading) stateDescription = loadingText
    }

/** 按钮内容：正常 = 文字；loading = 进度圈 + 进行时文案（同一行、居中，宽度由外层决定不变）。 */
@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
    loadingText: String,
    color: Color,
    style: TextStyle,
    progressSize: Dp = ModbusSize.progress,
    icon: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (loading) {
            ButtonProgress(color, progressSize)
            Spacer(Modifier.width(spacing.gapS))
        } else if (icon != null) {
            icon()
            Spacer(Modifier.width(spacing.s))
        }
        Text(
            if (loading) loadingText else text,
            color = color,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 主按钮。[compact] = 紧凑档：高 44、字 14。默认高 52、字 16。
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
            .fillMaxWidth()
            .then(if (compact) Modifier.minimumInteractiveComponentSize() else Modifier)
            .height(if (compact) ModbusSize.buttonCompact else ModbusSize.button)
            .focusRing(interaction, shape)
            .clip(shape)
            .background(fill)
            .buttonSemantics(enabled, loading, busyText)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .padding(horizontal = LocalSpacing.current.card),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(text, loading, busyText, content, if (compact) ModbusType.buttonCompact else ModbusType.button)
    }
}

/**
 * 次按钮。[compact] = 高 44。[icon] 画在文字前；[active] 表示开关已开（强调色描边 + 浅底）。
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
    val on = active && enabled
    val baseStyle = if (compact) ModbusType.buttonCompact else ModbusType.button
    Box(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    height != null -> Modifier.height(height)
                    compact -> Modifier.minimumInteractiveComponentSize().height(ModbusSize.buttonCompact)
                    else -> Modifier.height(ModbusSize.button)
                },
            )
            .focusRing(interaction, shape)
            .clip(shape)
            .background(if (on) colors.wash(colors.accent) else colors.surface1)
            // 禁用：边框 line、文字 dim（§6.1，原只变文字）
            .border(StrokeControl, if (on) colors.accentBorder else colors.line, shape)
            .buttonSemantics(enabled, loading, busyText)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .padding(horizontal = LocalSpacing.current.card),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(
            text = text,
            loading = loading,
            loadingText = busyText,
            color = if (!enabled) colors.dim else if (on) colors.accentText else colors.text,
            style = if (fontSize != null) baseStyle.copy(fontSize = fontSize) else baseStyle,
            icon = icon,
        )
    }
}

/** 小按钮：视觉高 32、label 12 SemiBold、圆角 10；触控用 minimumInteractiveComponentSize 补到 48。 */
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
    val shape = RoundedCornerShape(FieldRadius)
    val interaction = remember { MutableInteractionSource() }
    val busyText = loadingText ?: stringResource(R.string.btn_loading_generic)
    val background = when {
        primary -> Modifier.background(if (enabled) colors.accent else colors.surface3)
        // 禁用：底 surface2（§6.1，原无变化）；默认外观（透明 + 描边）不变。
        !enabled -> Modifier.background(colors.surface2).border(StrokeControl, colors.line, shape)
        else -> Modifier.border(StrokeControl, colors.line, shape)
    }
    val content = when {
        !enabled -> colors.dim
        primary -> colors.onAccent
        else -> colors.text
    }
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .height(ModbusSize.buttonSmall)
            .focusRing(interaction, shape)
            .clip(shape)
            .then(background)
            .buttonSemantics(enabled, loading, busyText)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .padding(horizontal = LocalSpacing.current.card),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(
            text, loading, busyText, content,
            ModbusType.label.copy(fontWeight = FontWeight.SemiBold),
            progressSize = ModbusSize.progressSmall,
        )
    }
}

/** 行内的文字链接（“添加”“清空”）。视觉是一行小字，触控区域至少 48×48，文字靠右。禁用 = dim + 不可点。 */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalModbusColors.current.muted,
    enabled: Boolean = true,
) {
    val colors = LocalModbusColors.current
    Box(
        modifier
            .sizeIn(minWidth = MinTouch, minHeight = MinTouch)
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = LocalSpacing.current.gapS),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(text, color = if (enabled) color else colors.dim, style = ModbusType.label.copy(fontWeight = FontWeight.SemiBold))
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
            verticalArrangement = Arrangement.spacedBy(spacing.gapS),
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
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.gapS)) {
                SmallButton(write, {})
                SmallButton(write, {}, primary = true)
                SmallButton(write, {}, enabled = false)
                SmallButton(write, {}, loading = true, loadingText = writing)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.gapS)) {
                TextLink(write, {}, color = colors.accentText)
                TextLink(write, {}, enabled = false)
            }
        }
    }
}
