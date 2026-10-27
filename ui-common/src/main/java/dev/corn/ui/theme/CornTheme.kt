package dev.corn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/**
 * 用户可调的外观项。第一期只开放深色：[dark] 由外壳固定传 true，但浅色值和其余可调项都保留，
 * 第二期重新开放时不需要改结构。
 */
data class BenchAppearance(
    val dark: Boolean = true,
    /** App 字号倍率，与系统字号叠乘。 */
    val fontScale: Float = 1f,
    val density: BenchDensity = BenchDensity.COMFORTABLE,
)

val LocalBenchAppearance = staticCompositionLocalOf { BenchAppearance() }

/** Material 槽位全部映射到语义 token，避免落回 Material 默认的紫色调色板。 */
fun BenchColors.toMaterialScheme(): ColorScheme {
    val build: (Boolean) -> ColorScheme = { dark ->
        if (dark) darkColorScheme() else lightColorScheme()
    }
    return build(isDark).copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = accentText,
        secondary = success,
        onSecondary = onAccent,
        secondaryContainer = successContainer,
        onSecondaryContainer = success,
        tertiary = danger,
        onTertiary = onAccent,
        tertiaryContainer = dangerContainer,
        onTertiaryContainer = danger,
        error = danger,
        onError = onAccent,
        errorContainer = dangerContainer,
        onErrorContainer = danger,
        background = bg,
        onBackground = text,
        surface = surface1,
        onSurface = text,
        surfaceVariant = surface2,
        onSurfaceVariant = muted,
        surfaceTint = Color.Transparent,
        surfaceBright = surface1,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface1,
        surfaceContainer = surface1,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface3,
        inverseSurface = surface3,
        inverseOnSurface = text,
        inversePrimary = accent,
        outline = line,
        outlineVariant = line,
        scrim = scrim,
    )
}

val BenchShapes = Shapes(
    extraSmall = RoundedCornerShape(BenchRadius.xs),
    small = RoundedCornerShape(BenchRadius.s),
    medium = RoundedCornerShape(BenchRadius.m),
    large = RoundedCornerShape(BenchRadius.l),
    extraLarge = RoundedCornerShape(BenchRadius.xl),
)

val BenchTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
        labelSmall = labelSmall.copy(letterSpacing = 0.8.sp),
    )
}

/** 系统倍率 × App 倍率（不封顶，与 Corn Modbus 的现行规则一致）。 */
fun totalFontScale(system: Float, app: Float): Float = system * app

/**
 * Corn Bench 主题：提供语义色、间距、外观设置，并把 Material 主题映射到同一套 token。
 */
@Composable
fun CornTheme(
    appearance: BenchAppearance = BenchAppearance(),
    content: @Composable () -> Unit,
) {
    val colors = benchColors(appearance.dark)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalBenchColors provides colors,
        LocalBenchSpacing provides BenchSpacing.of(appearance.density),
        LocalBenchAppearance provides appearance,
        LocalDensity provides Density(density.density, totalFontScale(density.fontScale, appearance.fontScale)),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = BenchTypography,
            shapes = BenchShapes,
            content = content,
        )
    }
}
