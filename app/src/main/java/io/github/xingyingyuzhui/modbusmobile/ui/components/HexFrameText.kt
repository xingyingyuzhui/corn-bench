package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
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
    Text(text, modifier = modifier, style = Mono)
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
