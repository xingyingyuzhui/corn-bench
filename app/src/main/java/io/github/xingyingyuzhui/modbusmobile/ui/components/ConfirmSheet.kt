package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

/** 文字行框内垂直居中（默认是按上沿比例分配，CJK 字会比旁边的徽标偏低 1dp 左右）。 */
val CenteredText = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** 稿面遮罩：深色 rgba(1,3,6,.6)，浅色 rgba(10,20,35,.38)。 */
@Composable
fun confirmScrimColor(): Color =
    if (LocalModbusColors.current.isDark) Color(0x99010306) else Color(0x610A1423)

/**
 * 写入确认。所有会向线路发送写请求的入口都必须经过它。
 *
 * @param badge 标题右侧的灰色小标（例如 “Unit 1 · 地址 2 · 00003”）。
 * @param frame 将要发送的整帧；[frameHighlight] 是载荷字节区间（琥珀色），CRC 自动变暗。
 * @param warning 一行提示，前面带 ⚠。
 * @param content 标题与整帧之间的内容（当前值 → 新值）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    badge: String? = null,
    badgeKind: SemanticKind = SemanticKind.DROPPED,
    confirmDanger: Boolean = false,
    frame: ByteArray? = null,
    frameHighlight: IntRange? = null,
    frameCaption: String? = null,
    warning: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = LocalModbusColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.border(1.dp, colors.line, SheetShape),
        shape = SheetShape,
        containerColor = colors.surface1,
        scrimColor = confirmScrimColor(),
        dragHandle = { SheetHandle() },
    ) {
        ConfirmSheetBody(
            title, confirmLabel, dismissLabel, onConfirm, onDismiss,
            badge = badge,
            badgeKind = badgeKind,
            confirmDanger = confirmDanger,
            frame = frame,
            frameHighlight = frameHighlight,
            frameCaption = frameCaption,
            warning = warning,
            content = content,
        )
    }
}

/** 稿面 .grab：40×4，s3，圆角 2，上 10 下 12。 */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    Box(
        modifier
            .padding(top = 10.dp, bottom = 12.dp)
            .size(width = 40.dp, height = 4.dp)
            .background(colors.surface3, RoundedCornerShape(2.dp)),
    )
}

@Composable
fun ConfirmSheetBody(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeKind: SemanticKind = SemanticKind.DROPPED,
    confirmDanger: Boolean = false,
    frame: ByteArray? = null,
    frameHighlight: IntRange? = null,
    frameCaption: String? = null,
    warning: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = LocalModbusColors.current
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = colors.text,
                style = CenteredText.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f).testTag("confirm-title"),
            )
            if (badge != null) SemanticBadge(badgeKind, badge, Modifier.padding(start = 8.dp).testTag("confirm-badge"))
        }
        content()
        if (frame != null) {
            if (frameCaption != null) {
                Text(frameCaption, color = colors.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            HexFrame(frame, highlight = frameHighlight)
        }
        if (warning != null) {
            val lineHeight = with(LocalDensity.current) { 16.sp.toDp() }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 图标占第一行文字的行高并居中，不再用写死的 1dp 偏移（放大字体后会错位）。
                Box(Modifier.height(lineHeight), contentAlignment = Alignment.Center) { WarnIcon(colors.txText) }
                Text(
                    warning,
                    color = colors.muted,
                    style = CenteredText.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Box(Modifier.size(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // 主按钮 52 高，取消键也要 52（Secondary 默认最小 48，并排时会矮一截、文字不在同一水平线）。
            SecondaryButton(dismissLabel, onDismiss, modifier = Modifier.weight(1f), height = 52.dp)
            PrimaryButton(confirmLabel, onConfirm, modifier = Modifier.weight(1.6f), danger = confirmDanger)
        }
    }
}

@Preview(name = "深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SheetDark() = SheetPreview(true)

@Preview(name = "浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SheetLight() = SheetPreview(false)

@Composable
private fun SheetPreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        ConfirmSheetBody(
            title = stringResource(R.string.confirm_title_register),
            confirmLabel = stringResource(R.string.action_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {},
            onDismiss = {},
            badge = "Unit 1 · 地址 5 · 40006",
            frame = byteArrayOf(0x01, 0x06, 0x00, 0x05, 0x00, 0x7B, 0x98.toByte(), 0x2B),
            frameHighlight = 4..5,
            frameCaption = stringResource(R.string.confirm_frame_caption, 8, "FC06"),
            warning = stringResource(R.string.confirm_warning),
        )
    }
}

/**
 * “当前 → 新值”这一行。稿面 b1-3/b1-4：标签 11、值 mono 22、箭头 20 dim。
 *
 * 对齐规则（有测试断言）：
 * - 左右两列结构完全相同：标签行（高 14sp）+ 值行（高 28sp），所以两个值的基线、字号、顶部一致；
 * - 箭头占值行的高度并居中，和两个值同一条垂直中线（线圈时右侧是分段控件，值行取 48dp 高，三者仍同中线）；
 * - “来自 N 秒前……”“新值与……相同”放在整行下面，不放进任何一列，所以不会把某一列撑高。
 */
@Composable
fun ConfirmValueRow(
    leftLabel: String,
    leftValue: String,
    modifier: Modifier = Modifier,
    leftNote: String? = null,
    rightLabel: String? = null,
    rightValue: String? = null,
    rightControl: (@Composable () -> Unit)? = null,
    hint: String? = null,
) {
    val colors = LocalModbusColors.current
    val density = LocalDensity.current
    val labelHeight = with(density) { 14.sp.toDp() }
    val valueHeight = if (rightControl != null) 48.dp else with(density) { 28.sp.toDp() }
    val labelStyle = CenteredText.copy(fontSize = 11.sp, lineHeight = 14.sp)
    val valueStyle = Mono.copy(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        lineHeightStyle = CenteredText.lineHeightStyle,
        platformStyle = CenteredText.platformStyle,
    )
    Column(modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column {
                Text(leftLabel, color = colors.muted, style = labelStyle, modifier = Modifier.height(labelHeight).testTag("confirm-left-label"))
                Box(Modifier.height(valueHeight), contentAlignment = Alignment.CenterStart) {
                    Text(leftValue, style = valueStyle, color = colors.muted, modifier = Modifier.testTag("confirm-left-value"))
                }
            }
            Column {
                Spacer(Modifier.height(labelHeight))
                Box(Modifier.height(valueHeight).testTag("confirm-arrow"), contentAlignment = Alignment.Center) {
                    ArrowGlyph(colors.dim)
                }
            }
            if (rightControl != null) {
                Column(Modifier.weight(1f)) {
                    Spacer(Modifier.height(labelHeight))
                    Box(Modifier.height(valueHeight).fillMaxWidth(), contentAlignment = Alignment.Center) { rightControl() }
                }
            } else {
                Column {
                    Text(rightLabel.orEmpty(), color = colors.muted, style = labelStyle, modifier = Modifier.height(labelHeight).testTag("confirm-right-label"))
                    Box(Modifier.height(valueHeight), contentAlignment = Alignment.CenterStart) {
                        Text(rightValue.orEmpty(), style = valueStyle, color = colors.txText, modifier = Modifier.testTag("confirm-right-value"))
                    }
                }
            }
        }
        if (leftNote != null) {
            Text(leftNote, color = colors.dim, style = CenteredText.copy(fontSize = 11.sp, lineHeight = 14.sp), modifier = Modifier.padding(top = 6.dp).testTag("confirm-note"))
        }
        if (hint != null) {
            Text(hint, color = colors.muted, style = CenteredText.copy(fontSize = 11.sp, lineHeight = 14.sp), modifier = Modifier.padding(top = if (leftNote != null) 2.dp else 6.dp).testTag("confirm-hint"))
        }
    }
}

/** 画出来的 →（字体里的 → 字形大小和基线各家不同，不能保证落在数值的垂直中线上）。 */
@Composable
fun ArrowGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 18.dp, height = 12.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawLine(color, Offset(0f, h / 2), Offset(w - stroke / 2, h / 2), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.64f, h * 0.1f), Offset(w - stroke / 2, h / 2), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.64f, h * 0.9f), Offset(w - stroke / 2, h / 2), stroke, StrokeCap.Round)
    }
}
