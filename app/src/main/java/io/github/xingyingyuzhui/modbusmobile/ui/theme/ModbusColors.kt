package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor

data class ModbusColors(
    val isDark: Boolean,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val rowLine: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val tx: Color,
    val txText: Color,
    val rxLocal: Color,
    val error: Color,
    val foreign: Color,
    val echo: Color,
    val ambiguous: Color,
    val dropped: Color,
    val accent: Color,
    val onAccent: Color,
    val island: Color,
) {
    /**
     * 语义色浅底：稿面每个颜色单独取值（G#7）。
     * 深色 amber/green/red .14、blue .13、purple/pink .15、gray .18；浅色 amber .16、green .10、red/blue .09、purple/pink .10、gray .12。
     * 不是上面这些语义色的（比如用户选的强调色）按 amber 档。
     */
    fun wash(color: Color): Color {
        val opaque = color.copy(alpha = 1f)
        val alpha = when (opaque) {
            rxLocal -> if (isDark) 0.14f else 0.10f
            error -> if (isDark) 0.14f else 0.09f
            foreign -> if (isDark) 0.13f else 0.09f
            echo -> if (isDark) 0.15f else 0.10f
            ambiguous -> if (isDark) 0.15f else 0.10f
            dropped -> if (isDark) 0.18f else 0.12f
            else -> if (isDark) 0.14f else 0.16f
        }
        return color.copy(alpha = alpha)
    }

    val accentText: Color
        get() = if (!isDark && accent == Color(0xFFF5A524)) txText else accent
}

fun modbusColors(dark: Boolean, accent: AccentColor): ModbusColors {
    val accentColor = Color(accent.argb.toInt())
    val onAccent = Color(accent.onAccentArgb.toInt())
    return if (dark) {
        ModbusColors(
            isDark = true,
            bg = Color(0xFF0D1117),
            surface1 = Color(0xFF151B23),
            surface2 = Color(0xFF1C2430),
            surface3 = Color(0xFF253041),
            line = Color(0xFF2B3544),
            rowLine = Color(0xFF1E2631),
            text = Color(0xFFE6EDF3),
            muted = Color(0xFF8B98A8),
            dim = Color(0xFF5C6878),
            tx = Color(0xFFF5A524),
            txText = Color(0xFFF5A524),
            rxLocal = Color(0xFF3FB950),
            error = Color(0xFFF85149),
            foreign = Color(0xFF58A6FF),
            echo = Color(0xFFA371F7),
            ambiguous = Color(0xFFDB61A2),
            dropped = Color(0xFF6E7681),
            accent = accentColor,
            onAccent = onAccent,
            island = Color(0xFF1C2430).copy(alpha = 0.94f),
        )
    } else {
        ModbusColors(
            isDark = false,
            bg = Color(0xFFF4F6F9),
            surface1 = Color(0xFFFFFFFF),
            surface2 = Color(0xFFEEF1F5),
            surface3 = Color(0xFFE2E7EE),
            line = Color(0xFFDCE2EA),
            rowLine = Color(0xFFEDF0F4),
            text = Color(0xFF121A24),
            muted = Color(0xFF5A6574),
            dim = Color(0xFF8D97A5),
            tx = Color(0xFFF5A524),
            txText = Color(0xFFB76E00),
            rxLocal = Color(0xFF1A7F37),
            error = Color(0xFFCF222E),
            foreign = Color(0xFF0969DA),
            echo = Color(0xFF8250DF),
            ambiguous = Color(0xFFBF3989),
            dropped = Color(0xFF6E7781),
            accent = accentColor,
            onAccent = onAccent,
            island = Color.White.copy(alpha = 0.95f),
        )
    }
}

val LocalModbusColors = staticCompositionLocalOf { modbusColors(dark = true, AccentColor.AMBER) }
