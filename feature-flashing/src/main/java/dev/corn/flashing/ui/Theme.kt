package dev.corn.flashing.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.corn.ui.theme.LocalBenchColors
import dev.corn.ui.theme.toMaterialScheme

/**
 * Charcoal + corn-yellow palette from the approved mockups (screens 01–23).
 *
 * Corn Bench：字段名沿用旧代码（352 处调用点不用改），值改由 :ui-common 的语义 token 提供。
 * 第二期起深浅色与强调色都开放，所以每个字段是 `@Composable` 只读 getter，读当前的 `LocalBenchColors`；
 * 深色 + 玉米时与 Corn Flashing 0.1.8 的字面值完全一致（`CornColorsMatchTokensTest` 锁定）。
 *
 * 注意：这些值**只能在 Compose 作用域里读**。枚举 / 顶层 val / 非 Composable 函数的默认参数不能用，
 * 需要颜色就把它改成在 Composable 里解析（`ChipTone.colors()` 就是这么做的）。
 */
object CornColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.bg
    val Surface1: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.surface1
    val Surface2: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.surface2
    val Surface3: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.surface3
    val Line: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.line
    val Text: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.text
    val Dim: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.muted
    val Mute: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.dim
    val Corn: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.accent
    val CornDeep: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.accentDeep
    val CornOn: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.onAccent
    val CornContainer: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.accentContainer
    val Leaf: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.success
    val LeafContainer: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.successContainer
    val Red: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.danger
    val RedContainer: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.dangerContainer
    val Blue: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.info
    val BlueContainer: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.infoContainer
    val NavBackground: Color @Composable @ReadOnlyComposable get() = LocalBenchColors.current.navBackground
}

private val CornTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
        labelSmall = labelSmall.copy(letterSpacing = 0.8.sp),
    )
}

val Mono = FontFamily.Monospace

/**
 * 烧录模式的 Material 主题。颜色来自外壳提供的 `LocalBenchColors`（深浅 / 强调色都由外壳决定），
 * 这里只负责映射和圆角，不再自带一套深色 scheme。
 */
@Composable
fun CornTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LocalBenchColors.current.toMaterialScheme(),
        typography = CornTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

/** Big monospace number used by counters, progress and serial ranges. */
val numericStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
