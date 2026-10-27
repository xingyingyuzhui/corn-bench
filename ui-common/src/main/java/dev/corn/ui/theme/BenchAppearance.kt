package dev.corn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

/** 主题模式：跟随系统 / 强制浅色 / 强制深色。持久化用 [name]，不要用 ordinal。 */
enum class BenchThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 外壳级外观状态。
 *
 * 第二期起深浅色与强调色对用户开放，并且**由外壳统一持有**：两个模式共用一套外观，
 * 在上位机的设置页改完，切到烧录模式也是同一个主题。
 */
data class BenchAppearanceState(
    val themeMode: BenchThemeMode = BenchThemeMode.SYSTEM,
    val accent: BenchAccent = BenchAccent.DEFAULT,
) {
    /** 结合系统深浅色解析出实际是否为深色。 */
    @Composable
    fun isDark(): Boolean = when (themeMode) {
        BenchThemeMode.SYSTEM -> isSystemInDarkTheme()
        BenchThemeMode.LIGHT -> false
        BenchThemeMode.DARK -> true
    }
}

/**
 * 外观的读写入口。由 :app 用 `bench_ui` DataStore 实现，经 CompositionLocal 提供给两个 feature。
 *
 * 为什么放在 :ui-common：feature 不能依赖 :app，但又需要读写同一个外观状态；
 * 没有提供时（预览、单测、单独跑某个 feature）各模块退回自己的默认值。
 */
interface AppearanceController {
    val state: State<BenchAppearanceState>

    fun setThemeMode(mode: BenchThemeMode)

    fun setAccent(accent: BenchAccent)
}

val LocalAppearanceController = staticCompositionLocalOf<AppearanceController?> { null }
