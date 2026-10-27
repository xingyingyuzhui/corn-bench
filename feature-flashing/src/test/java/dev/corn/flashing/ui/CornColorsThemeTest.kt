package dev.corn.flashing.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.BenchAppearance
import dev.corn.ui.theme.CornTheme as BenchCornTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 第二期的关键回归：烧录模式的颜色**必须随外壳主题解析**。
 *
 * 第一期 `CornColors` 是写死深色的 object；如果改回那样，浅色主题下烧录页会是黑底，
 * 而单测（纯 JVM 比对 token）发现不了——只有真的在 Compose 里读一遍才能抓到。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class CornColorsThemeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Snapshot {
        var bg: Color? = null
        var text: Color? = null
        var accent: Color? = null
        var surface1: Color? = null
    }

    private fun capture(appearance: BenchAppearance): Snapshot {
        val snapshot = Snapshot()
        compose.setContent {
            BenchCornTheme(appearance) {
                ReadColors(snapshot)
            }
        }
        compose.waitForIdle()
        return snapshot
    }

    @Composable
    private fun ReadColors(snapshot: Snapshot) {
        // 这里读 CornColors 就等于烧录页读它：同一个 CompositionLocal、同一套 getter。
        snapshot.bg = CornColors.Background
        snapshot.text = CornColors.Text
        snapshot.accent = CornColors.Corn
        snapshot.surface1 = CornColors.Surface1
        Text("probe")
    }

    @Test
    fun darkThemeIsStillTheLegacyCharcoal() {
        val dark = capture(BenchAppearance(dark = true))
        assertEquals(Color(0xFF121212), dark.bg)
        assertEquals(Color(0xFFF3EFE4), dark.text)
        assertEquals(Color(0xFFF7C948), dark.accent)
    }

    @Test
    fun lightThemeIsActuallyLight() {
        val light = capture(BenchAppearance(dark = false))
        assertNotEquals("浅色下不能再是炭黑", Color(0xFF121212), light.bg)
        assertTrue("浅色背景应比深色亮", light.bg!!.luminance() > 0.5f)
        assertTrue("浅色正文应是深色字", light.text!!.luminance() < 0.2f)
    }

    @Test
    fun accentFollowsTheShellSetting() {
        val blue = capture(BenchAppearance(dark = true, accent = BenchAccent.BLUE))
        assertNotEquals("强调色应随外壳变化", Color(0xFFF7C948), blue.accent)
    }
}
