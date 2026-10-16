package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

@Composable
fun HexFrameText(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    highlight: IntRange? = null,
    crcBytes: Int = 2,
    style: TextStyle = Mono,
) {
    val colors = LocalModbusColors.current
    val crcStart = (bytes.size - crcBytes).coerceAtLeast(0)
    val text = buildAnnotatedString {
        bytes.forEachIndexed { index, byte ->
            val token = "%02X".format(byte.toInt() and 0xFF)
            val style = when {
                highlight != null && index in highlight -> SpanStyle(color = colors.txText)
                index >= crcStart && bytes.size >= crcBytes -> SpanStyle(color = colors.dim)
                else -> SpanStyle(color = colors.text)
            }
            withStyle(style) { append(token) }
            if (index != bytes.lastIndex) append(" ")
        }
    }
    Text(text, modifier = modifier, style = style)
}

/**
 * 稿面 .frame：底色 bg、1px line 边框、圆角 12、内边距 10/12、字 14.5、字距 .3；
 * [compact] = 工作台写入卡里的预览条（字 12、内边距 8/10）。
 */
@Composable
fun HexFrame(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    highlight: IntRange? = null,
    compact: Boolean = false,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .background(colors.bg, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 10.dp),
    ) {
        HexFrameText(
            bytes,
            highlight = highlight,
            style = Mono.copy(fontSize = if (compact) 12.sp else 14.5.sp, letterSpacing = 0.3.sp),
        )
    }
}

@Preview(name = "深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HexDark() = HexPreview(true)

@Preview(name = "浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun HexLight() = HexPreview(false)

@Composable
private fun HexPreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        HexFrameText(
            byteArrayOf(0x01, 0x06, 0x00, 0x00, 0x00, 0x7B, 0xC8.toByte(), 0x3B),
            highlight = 2..3,
        )
    }
}
