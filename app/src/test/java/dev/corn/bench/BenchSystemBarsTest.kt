package dev.corn.bench

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import dev.corn.ui.theme.CornTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 外壳系统栏：系统是浅色时，炭黑底上的状态栏 / 导航栏图标必须是浅色（isAppearanceLight* = false）。
 *
 * 合并成一个 Activity 后，原来烧录模式自己设的「固定浅色图标」没有了；外壳若只调 `enableEdgeToEdge()`
 * （跟系统 uiMode 走），系统浅色时图标会变深，压在 #121212 上几乎看不见。
 * 这里先模拟 MainActivity 的默认调用，再断言 [BenchSystemBars] 把它改回浅色图标。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "notnight")
class BenchSystemBarsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun darkShellUsesLightIconsEvenWhenSystemIsLight() {
        compose.activity.enableEdgeToEdge()
        compose.waitForIdle()
        val window = compose.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertTrue("前提：系统浅色时默认 enableEdgeToEdge 会给深色图标", controller.isAppearanceLightStatusBars)

        compose.setContent { CornTheme { BenchSystemBars() } }
        compose.waitForIdle()
        assertFalse("深色外壳下状态栏应为浅色图标", controller.isAppearanceLightStatusBars)
        assertFalse("深色外壳下导航栏应为浅色图标", controller.isAppearanceLightNavigationBars)
    }
}
