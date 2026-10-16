package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF121614)
val Panel = Color(0xFF1C2420)
val Line = Color(0xFF314038)
val Amber = Color(0xFFE2A23A)
val Foam = Color(0xFFE7E4D8)
val Muted = Color(0xFF9AA69A)
val Ok = Color(0xFF7DCEA0)
val Fault = Color(0xFFE07A5F)

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1408),
    background = Ink,
    onBackground = Foam,
    surface = Panel,
    onSurface = Foam,
    surfaceVariant = Color(0xFF24302B),
    onSurfaceVariant = Muted,
    error = Fault,
    outline = Line,
)

val Mono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
    color = Foam,
)

@Composable
fun ModbusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
