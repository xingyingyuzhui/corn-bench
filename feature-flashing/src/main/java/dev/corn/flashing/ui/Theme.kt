package dev.corn.flashing.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.corn.ui.theme.benchColors

/**
 * Charcoal + corn-yellow palette from the approved mockups (screens 01–23).
 * Corn Bench：字段与旧值完全相同，但值改由 :ui-common 的语义 token 提供（[benchColors] 深色一档），
 * 不再写字面色值；`CornColorsMatchTokensTest` 逐项锁定旧值，保证第一期视觉零变化。
 */
object CornColors {
    private val t = benchColors(dark = true)
    val Background = t.bg
    val Surface1 = t.surface1
    val Surface2 = t.surface2
    val Surface3 = t.surface3
    val Line = t.line
    val Text = t.text
    val Dim = t.muted
    val Mute = t.dim
    val Corn = t.accent
    val CornDeep = t.accentDeep
    val CornOn = t.onAccent
    val CornContainer = t.accentContainer
    val Leaf = t.success
    val LeafContainer = t.successContainer
    val Red = t.danger
    val RedContainer = t.dangerContainer
    val Blue = t.info
    val BlueContainer = t.infoContainer
    val NavBackground = t.navBackground
}

private val CornScheme = darkColorScheme(
    primary = CornColors.Corn,
    onPrimary = CornColors.CornOn,
    primaryContainer = CornColors.CornContainer,
    onPrimaryContainer = CornColors.Corn,
    secondary = CornColors.Leaf,
    onSecondary = CornColors.CornOn,
    secondaryContainer = CornColors.LeafContainer,
    onSecondaryContainer = CornColors.Leaf,
    tertiary = CornColors.Red,
    onTertiary = CornColors.CornOn,
    tertiaryContainer = CornColors.RedContainer,
    onTertiaryContainer = CornColors.Red,
    error = CornColors.Red,
    onError = CornColors.CornOn,
    errorContainer = CornColors.RedContainer,
    onErrorContainer = CornColors.Red,
    background = CornColors.Background,
    onBackground = CornColors.Text,
    surface = CornColors.Surface1,
    onSurface = CornColors.Text,
    surfaceVariant = CornColors.Surface2,
    onSurfaceVariant = CornColors.Dim,
    surfaceContainer = CornColors.Surface1,
    surfaceContainerHigh = CornColors.Surface2,
    surfaceContainerHighest = CornColors.Surface3,
    outline = CornColors.Line,
    outlineVariant = CornColors.Line,
    scrim = benchColors(dark = true).scrim,
)

private val CornTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
        labelSmall = labelSmall.copy(letterSpacing = 0.8.sp),
    )
}

val Mono = FontFamily.Monospace

@Composable
fun CornTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CornScheme,
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
