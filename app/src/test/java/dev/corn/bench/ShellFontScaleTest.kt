package dev.corn.bench

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.corn.ui.mode.BenchMode
import dev.corn.ui.theme.BenchAccent
import io.github.xingyingyuzhui.modbusmobile.application.FontScale
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 评审 E P2-7：外壳那层 `CornTheme` 的 fontScale 必须是 1。
 * 在真实外壳（`BenchRoot`）里套真实的 `ModbusTheme`：外壳内容里是系统倍率，进到模式主题后是系统 × App 倍率（只乘一次）。
 * 系统字号故意设成 1.2：外壳若也乘 App 倍率，这里会变成 1.2 × 1.3 × 1.3。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], fontScale = 1.2f)
class ShellFontScaleTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shellLayerKeepsSystemScaleAndModeThemeMultipliesOnce() {
        var shell = -1f
        var mode = -1f
        compose.setContent {
            BenchRoot(
                modeStore = MemoryModeStore(BenchMode.MODBUS),
                appearanceController = FakeAppearance(),
                modbusContent = {
                    shell = LocalDensity.current.fontScale
                    ModbusTheme(UiSettings(fontScale = FontScale.EXTRA)) { mode = LocalDensity.current.fontScale }
                },
            )
        }
        compose.waitForIdle()
        val system = compose.activity.resources.configuration.fontScale
        assertEquals(1.2f, system, 0.001f)
        assertEquals("外壳这一层不能乘 App 字号", system, shell, 0.001f)
        assertEquals("App 字号只乘一次", system * 1.3f, mode, 0.001f)
    }

    @Test
    fun shellAppearanceAlwaysHasUnitFontScale() {
        BenchAccent.entries.forEach { accent ->
            assertEquals(1f, shellAppearance(dark = true, accent = accent).fontScale)
            assertEquals(1f, shellAppearance(dark = false, accent = accent).fontScale)
        }
    }
}
