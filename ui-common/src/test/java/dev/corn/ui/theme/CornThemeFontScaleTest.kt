package dev.corn.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 评审 E P2-7：外壳的 `CornTheme` 套模式自己的 `CornTheme` 时，App 字号只能乘一次。
 * 外层传 1、内层传 1.3 → 系统 × 1.3；两层都 ≠1 → `check` 失败（不再悄悄变成 1.69）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], fontScale = 1.2f)
class CornThemeFontScaleTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val system get() = compose.activity.resources.configuration.fontScale

    @Test
    fun outerOneInnerAppScaleMultipliesOnce() {
        var outer = -1f
        var inner = -1f
        compose.setContent {
            CornTheme(BenchAppearance(fontScale = 1f)) {
                outer = LocalDensity.current.fontScale
                CornTheme(BenchAppearance(fontScale = 1.3f)) { inner = LocalDensity.current.fontScale }
            }
        }
        compose.waitForIdle()
        assertEquals(1.2f, system, 0.001f)
        assertEquals(system, outer, 0.001f)
        assertEquals(system * 1.3f, inner, 0.001f)
    }

    @Test
    fun innerOneKeepsOuterScale() {
        var inner = -1f
        compose.setContent {
            CornTheme(BenchAppearance(fontScale = 1.15f)) {
                CornTheme(BenchAppearance(fontScale = 1f)) { inner = LocalDensity.current.fontScale }
            }
        }
        compose.waitForIdle()
        assertEquals(system * 1.15f, inner, 0.001f)
    }

    @Test
    fun bothLayersScalingFailsLoudly() {
        val error = assertThrows(IllegalStateException::class.java) {
            compose.setContent {
                CornTheme(BenchAppearance(fontScale = 1.3f)) {
                    CornTheme(BenchAppearance(fontScale = 1.3f)) { }
                }
            }
            compose.waitForIdle()
        }
        assert(error.message!!.contains("乘了两次"))
    }
}
