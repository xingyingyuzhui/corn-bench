package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.corn.ui.theme.BenchAppearance
import dev.corn.ui.theme.BenchDensity
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
 * 上位机模式的主题入口。Corn Bench 第一期起，颜色、字号来自 :ui-common 的语义 token；
 * 这个函数只负责把 Corn Modbus 的 [UiSettings] 翻译成 [BenchAppearance]，再套 ui-common 的 `CornTheme`。
 *
 * 浅色分支保留（“可调项隐藏不删”）：第一期 App 外壳固定传深色，设置页也不显示主题 / 强调色两项；
 * 旧预览与截图测试仍可直接传浅色设置。[UiSettings.accent] 不再生效（强调色固定为玉米色）。
 */
@Composable
fun ModbusTheme(
    settings: UiSettings,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(settings.theme, isSystemInDarkTheme())
    CompositionLocalProvider(
        LocalModbusColors provides modbusColors(dark),
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
