package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.BenchAppearance
import dev.corn.ui.theme.BenchDensity
import dev.corn.ui.theme.BenchThemeMode
import dev.corn.ui.theme.LocalAppearanceController
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor
import io.github.xingyingyuzhui.modbusmobile.application.DensityMode
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings

val LocalUiSettings = staticCompositionLocalOf { UiSettings() }

/**
 * D3 待 Xing 拍板：App 字号倍率与系统倍率叠乘（最高 1.69×）是否封顶。
 * 默认方案：不封顶（null）；ux 建议 1.5f。拍板后只改这一处。
 *
 * 第一期起真正的叠乘在 :ui-common 的 CornTheme（`totalFontScale`，同样不封顶）；
 * 这个函数保留给旧测试引用。
 */
val FONT_SCALE_CAP: Float? = null

/** 系统倍率 × App 倍率，按 [FONT_SCALE_CAP] 封顶（null = 不封顶，保持叠乘）。 */
fun totalFontScale(system: Float, app: Float): Float {
    val total = system * app
    return FONT_SCALE_CAP?.let { minOf(total, it) } ?: total
}

/** [UiSettings.theme] 在给定系统深浅色下的实际深浅（[ThemeMode.SYSTEM] 跟随系统）。 */
fun isDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * 上位机模式的主题入口。颜色、字号来自 :ui-common 的语义 token；
 * 这个函数只负责把 Corn Modbus 的 [UiSettings] 翻译成 [BenchAppearance]，再套 ui-common 的 `CornTheme`。
 *
 * 第二期起深浅色 / 强调色对用户开放，并且由**外壳**统一持有（`LocalAppearanceController`）：
 * 外壳在时以它为准（这样切到烧录模式是同一套外观）；不在时（预览 / 单测 / 单独跑本模块）
 * 退回 [UiSettings.theme] 与 [UiSettings.accent]，旧截图的写法继续可用。
 */
@Composable
fun ModbusTheme(
    settings: UiSettings,
    content: @Composable () -> Unit,
) {
    val shellAppearance = LocalAppearanceController.current
    val shell = shellAppearance?.state?.value
    val dark = if (shell != null) {
        shell.isDark()
    } else {
        isDarkTheme(settings.theme, isSystemInDarkTheme())
    }
    val accent = shell?.accent ?: settings.accent.toBenchAccent()
    CompositionLocalProvider(
        LocalModbusColors provides modbusColors(dark, accent),
        LocalUiSettings provides settings,
        // Modbus 的间距刻度（group 16 / card 12）与 ui-common 默认（12 / 14）不同；
        // 第一期先保留 Modbus 自己的间距，避免所有页面的卡片节奏一起变。
        LocalSpacing provides ModbusSpacing.of(settings.density),
    ) {
        // 字号只在这里叠一次：CornTheme 读系统 fontScale × appearance.fontScale。
        // 不要再自己 provides LocalDensity，否则 App 字号会被乘两次（1.3 → 1.69）。
        dev.corn.ui.theme.CornTheme(
            appearance = BenchAppearance(
                dark = dark,
                accent = accent,
                fontScale = settings.fontScale.multiplier,
                density = when (settings.density) {
                    DensityMode.COMFORTABLE -> BenchDensity.COMFORTABLE
                    DensityMode.COMPACT -> BenchDensity.COMPACT
                },
            ),
            content = content,
        )
    }
}

/** Corn Modbus 的强调色枚举 → :ui-common 的品牌强调色。两边名字一一对应，未知时退回玉米。 */
fun AccentColor?.toBenchAccent(): BenchAccent = when (this) {
    AccentColor.BLUE -> BenchAccent.BLUE
    AccentColor.GREEN -> BenchAccent.GREEN
    AccentColor.TEAL -> BenchAccent.TEAL
    AccentColor.PURPLE -> BenchAccent.PURPLE
    AccentColor.AMBER, null -> BenchAccent.CORN
}

/** 旧 `ThemeMode` → 外壳的 [BenchThemeMode]（持久化名字相同）。 */
fun ThemeMode.toBenchThemeMode(): BenchThemeMode = when (this) {
    ThemeMode.SYSTEM -> BenchThemeMode.SYSTEM
    ThemeMode.LIGHT -> BenchThemeMode.LIGHT
    ThemeMode.DARK -> BenchThemeMode.DARK
}

fun BenchThemeMode.toThemeMode(): ThemeMode = when (this) {
    BenchThemeMode.SYSTEM -> ThemeMode.SYSTEM
    BenchThemeMode.LIGHT -> ThemeMode.LIGHT
    BenchThemeMode.DARK -> ThemeMode.DARK
}

/** 外壳的强调色 → Corn Modbus 旧枚举，给色板组件（`ColorSwatchRow`）用。 */
fun BenchAccent.toAccentColor(): AccentColor = when (this) {
    BenchAccent.CORN -> AccentColor.AMBER
    BenchAccent.BLUE -> AccentColor.BLUE
    BenchAccent.GREEN -> AccentColor.GREEN
    BenchAccent.TEAL -> AccentColor.TEAL
    BenchAccent.PURPLE -> AccentColor.PURPLE
}
