package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings

val LocalUiSettings = staticCompositionLocalOf { UiSettings() }

@Composable
fun ModbusTheme(
    settings: UiSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = modbusColors(dark, settings.accent)
    val density = LocalDensity.current
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface1,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.muted,
            error = colors.error,
            onError = colors.text,
            outline = colors.line,
            outlineVariant = colors.rowLine,
            // Material 组件（菜单、对话框、底部弹层、Snackbar）按层级取这些槽位，全部映射到我们的 token，
            // 否则会落回 Material 默认的浅紫色调色板。
            surfaceTint = Color.Transparent,
            surfaceBright = colors.surface1,
            surfaceDim = colors.bg,
            surfaceContainerLowest = colors.bg,
            surfaceContainerLow = colors.surface1,
            surfaceContainer = colors.surface1,
            surfaceContainerHigh = colors.surface2,
            surfaceContainerHighest = colors.surface3,
            inverseSurface = colors.surface3,
            inverseOnSurface = colors.text,
            inversePrimary = colors.accent,
            secondary = colors.muted,
            onSecondary = colors.surface1,
            secondaryContainer = colors.wash(colors.accent),
            onSecondaryContainer = colors.accentText,
            primaryContainer = colors.wash(colors.accent),
            onPrimaryContainer = colors.accentText,
            scrim = Color(0x99010306),
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface1,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.muted,
            error = colors.error,
            onError = colors.surface1,
            outline = colors.line,
            outlineVariant = colors.rowLine,
            // Material 组件（菜单、对话框、底部弹层、Snackbar）按层级取这些槽位，全部映射到我们的 token，
            // 否则会落回 Material 默认的浅紫色调色板。
            surfaceTint = Color.Transparent,
            surfaceBright = colors.surface1,
            surfaceDim = colors.bg,
            surfaceContainerLowest = colors.bg,
            surfaceContainerLow = colors.surface1,
            surfaceContainer = colors.surface1,
            surfaceContainerHigh = colors.surface2,
            surfaceContainerHighest = colors.surface3,
            inverseSurface = colors.surface3,
            inverseOnSurface = colors.text,
            inversePrimary = colors.accent,
            secondary = colors.muted,
            onSecondary = colors.surface1,
            secondaryContainer = colors.wash(colors.accent),
            onSecondaryContainer = colors.accentText,
            primaryContainer = colors.wash(colors.accent),
            onPrimaryContainer = colors.accentText,
            scrim = Color(0x99010306),
        )
    }
    CompositionLocalProvider(
        LocalModbusColors provides colors,
        LocalUiSettings provides settings,
        LocalSpacing provides ModbusSpacing(settings.density.scale),
        LocalDensity provides Density(density.density, density.fontScale * settings.fontScale.multiplier),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
