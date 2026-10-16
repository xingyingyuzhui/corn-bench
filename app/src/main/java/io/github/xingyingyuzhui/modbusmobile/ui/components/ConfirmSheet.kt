package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (badge != null) SemanticBadge(badgeKind, badge, Modifier.padding(start = 8.dp))
        }
        content()
        if (frame != null) {
            if (frameCaption != null) {
                Text(frameCaption, color = colors.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            HexFrame(frame, highlight = frameHighlight)
        }
        if (warning != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WarnIcon(colors.txText, Modifier.padding(top = 1.dp))
                Text(warning, color = colors.muted, fontSize = 12.sp)
            }
        } else {
            Box(Modifier.size(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(dismissLabel, onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton(confirmLabel, onConfirm, modifier = Modifier.weight(1.6f))
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
