package io.github.xingyingyuzhui.modbusmobile.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 系统浅色 + App 手动深色时，状态栏图标必须是浅色（isAppearanceLightStatusBars = false），否则深色图标压在深色背景上看不见。
 * 反过来系统深色 + App 手动浅色时要用深色图标。去掉 [SystemBarsFollowTheme] 后图标只跟系统走，这里会失败。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SystemBarsThemeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun lightIcons(): Pair<Boolean, Boolean> {
        val window = compose.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        return controller.isAppearanceLightStatusBars to controller.isAppearanceLightNavigationBars
    }

    @Test
    @Config(qualifiers = "notnight")
    fun systemLightAppDarkUsesLightIcons() {
        var theme by mutableStateOf(ThemeMode.DARK)
        compose.setContent { ModbusTheme(UiSettings(theme = theme)) { SystemBarsFollowTheme() } }
        compose.waitForIdle()
        val (status, nav) = lightIcons()
        assertFalse("深色 App 主题下状态栏应为浅色图标", status)
        assertFalse("深色 App 主题下导航栏应为浅色图标", nav)

        // 运行中切回浅色，图标跟着变深。
        theme = ThemeMode.LIGHT
        compose.waitForIdle()
        assertTrue("浅色 App 主题下状态栏应为深色图标", lightIcons().first)
    }

    @Test
    @Config(qualifiers = "night")
    fun systemDarkAppLightUsesDarkIcons() {
        compose.setContent { ModbusTheme(UiSettings(theme = ThemeMode.LIGHT)) { SystemBarsFollowTheme() } }
        compose.waitForIdle()
        assertTrue("浅色 App 主题下状态栏应为深色图标", lightIcons().first)
    }

    /**
     * Corn Bench 第一期起，上位机模式不再有自己的 Activity：入口 Composable 是 `ModbusEntry`，
     * 它必须在外壳的 CornTheme 里调用 [SystemBarsFollowTheme]（否则系统栏图标还是跟系统走）。
     */
    @Test
    fun modbusEntryAppliesIt() {
        val source = File("src/main/java/io/github/xingyingyuzhui/modbusmobile/ModbusEntry.kt").readText()
        assertTrue("ModbusEntry 的 ModbusTheme 里要调用 SystemBarsFollowTheme()", source.contains("SystemBarsFollowTheme()"))
    }
}
