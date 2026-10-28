package dev.corn.flashing.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import dev.corn.flashing.FlashingFeature
import dev.corn.ui.theme.BenchAccent
import dev.corn.ui.theme.BenchAppearance
import dev.corn.ui.theme.CornTheme as BenchCornTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 烧录工作台（单路、未接探针的首屏）的像素基线：浅色 × 默认玉米 / 浅色 × 蓝，另留一张深色对照。
 * 用真实 [FlashViewModel] + [CornApp]（Robolectric 里没有 USB / 原生库，自然是“未就绪 + 连接引导”）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class FlashingBaselineTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun freshContainer() = FlashingFeature.resetForTest()
    @After fun dropContainer() = FlashingFeature.resetForTest()

    private fun workbench(name: String, appearance: BenchAppearance) {
        val vm = FlashViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            BenchCornTheme(appearance) { CornApp(vm) }
        }
        compose.waitForIdle()
        compose.onRoot().captureBaseline(name)
    }

    @Test fun workbench_light_corn() = workbench("flashing_workbench_light_corn", BenchAppearance(dark = false))
    @Test fun workbench_light_blue() = workbench("flashing_workbench_light_blue", BenchAppearance(dark = false, accent = BenchAccent.BLUE))
    @Test fun workbench_dark_corn() = workbench("flashing_workbench_dark_corn", BenchAppearance(dark = true))
}
