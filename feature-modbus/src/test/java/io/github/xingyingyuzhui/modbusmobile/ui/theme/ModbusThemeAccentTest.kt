package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.corn.ui.theme.AppearanceController
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.BenchAppearanceState
import dev.corn.ui.theme.BenchThemeMode
import dev.corn.ui.theme.LocalAppearanceController
import dev.corn.ui.theme.LocalBenchColors
import dev.corn.ui.theme.benchColors
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归锁（P2 像素基线发现）：上位机页面读的 `LocalModbusColors` 必须跟强调色走。
 * 第二期只改了外观页与 `CornTheme`，`ModbusTheme` 仍按 `modbusColors(dark)` 提供颜色，选了蓝色上位机还是玉米色。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ModbusThemeAccentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Shell(accent: BenchAccent) : AppearanceController {
        override val state: State<BenchAppearanceState> =
            mutableStateOf(BenchAppearanceState(BenchThemeMode.LIGHT, accent))
        override fun setThemeMode(mode: BenchThemeMode) = Unit
        override fun setAccent(accent: BenchAccent) = Unit
    }

    @Test
    fun shellAccentReachesModbusColors() {
        var modbus: Color? = null
        var bench: Color? = null
        compose.setContent {
            CompositionLocalProvider(LocalAppearanceController provides Shell(BenchAccent.BLUE)) {
                // 旧设置里是 AMBER：有外壳时必须以外壳为准。
                ModbusTheme(UiSettings(theme = ThemeMode.DARK, accent = AccentColor.AMBER)) {
                    modbus = LocalModbusColors.current.accent
                    bench = LocalBenchColors.current.accent
                }
            }
        }
        compose.waitForIdle()
        assertEquals(benchColors(dark = false, accent = BenchAccent.BLUE).accent, modbus)
        assertEquals("两套 Local 必须一致", bench, modbus)
    }

    @Test
    fun settingsAccentIsUsedWithoutShell() {
        var modbus: Color? = null
        compose.setContent {
            ModbusTheme(UiSettings(theme = ThemeMode.LIGHT, accent = AccentColor.GREEN)) {
                modbus = LocalModbusColors.current.accent
            }
        }
        compose.waitForIdle()
        assertEquals(benchColors(dark = false, accent = BenchAccent.GREEN).accent, modbus)
    }
}
