package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.xingyingyuzhui.modbusmobile.application.FontScale
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归锁：`ModbusTheme` 不能把 App 字号叠两次。
 *
 * 曾经写过 `LocalDensity provides Density(system, totalFontScale(system, app))`，
 * 外面再套一层 :ui-common 的 `CornTheme`（它自己会乘一次），1.3 的实际字号会变成 1.69。
 * 现在字号只在 `CornTheme` 里叠一次；这里断言 1.15 / 1.3 就是最终倍率。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "notnight")
class ModbusThemeDensityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capturedFontScale(scale: FontScale): Float {
        var seen = -1f
        compose.setContent {
            ModbusTheme(UiSettings(theme = ThemeMode.DARK, fontScale = scale)) {
                seen = LocalDensity.current.fontScale
            }
        }
        compose.waitForIdle()
        return seen
    }

    @Test
    fun appFontScaleIsAppliedExactlyOnce() {
        val system = compose.activity.resources.configuration.fontScale
        assertEquals(system * 1.15f, capturedFontScale(FontScale.LARGE), 0.01f)
    }

    @Test
    fun extraFontScaleIsNotSquared() {
        val system = compose.activity.resources.configuration.fontScale
        val captured = capturedFontScale(FontScale.EXTRA)
        assertEquals(system * 1.3f, captured, 0.01f)
        // 叠两次会是 system*1.69；留一点余量防止浮点误差误判。
        assertEquals(false, kotlin.math.abs(captured - system * 1.69f) < 0.01f)
    }
}
