package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toPixelMap
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.FontScale
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegContainerRadius
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegContainerTag
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegGap
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegItemRadius
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegItemTag
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegPadding
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.shell.DockTag
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.shell.FloatingDock
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockBottomGap
import io.github.xingyingyuzhui.modbusmobile.ui.theme.IslandRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 老板反馈（2026-10-17）：分段滑块选中块圆角应 = 外层圆角 − 内边距（同心）；读取/写入分段与外层容器没对齐；
 * dock 阴影形状应与 dock 圆角一致、dock 离手势条太高。
 * 断言：布局位置（getBoundsInRoot）+ 像素（captureToImage）；Robolectric 密度取 xxhdpi（3×）让像素扫描有足够分辨率。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp-xxhdpi")
class SegmentedAndDockTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun radiiAreConcentric() {
        assertEquals(12f, SegContainerRadius.value, 0f)
        assertEquals("选中块圆角 = 外层圆角 − 内边距", (SegContainerRadius - SegPadding).value, SegItemRadius.value, 0f)
    }

    @Test fun textSegment_light() = textSegment(ThemeMode.LIGHT, FontScale.STANDARD, "seg_text_light")

    @Test fun textSegment_dark() = textSegment(ThemeMode.DARK, FontScale.STANDARD, "seg_text_dark")

    @Test fun textSegment_dark_font13() = textSegment(ThemeMode.DARK, FontScale.EXTRA, "seg_text_dark_font13")

    @Test fun roleSegment_light() = roleSegment(ThemeMode.LIGHT, FontScale.STANDARD, "seg_role_light")

    @Test fun roleSegment_dark_font13() = roleSegment(ThemeMode.DARK, FontScale.EXTRA, "seg_role_dark_font13")

    private fun textSegment(theme: ThemeMode, scale: FontScale, name: String) {
        show(theme, scale) {
            // 与工作台一致：读取/写入 150dp 宽 sm 档，下面一条整行宽的区域分段。
            SegmentedTextControl(listOf("读取", "写入"), "读取", {}, Modifier.width(150.dp), small = true) { it }
            SegmentedTextControl(listOf("线圈", "离散", "保持", "输入"), "线圈", {}) { it }
        }
        assertGeometry(index = 0, expectedWidth = 150.dp)
        assertGeometry(index = 1, expectedWidth = null)
        assertDiagonalGapIsConcentric(0)
        assertDiagonalGapIsConcentric(1)
        compose.onRoot().captureRoboImage("src/test/screenshots/segmented/$name.png")
    }

    private fun roleSegment(theme: ThemeMode, scale: FontScale, name: String) {
        show(theme, scale) {
            SegmentedControl(listOf("主站", "从站"), "主站", {}) { role ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(role, fontSize = 14.sp, color = LocalModbusColors.current.text)
                    Text(if (role == "主站") "主动读写外部从站" else "响应外部主站", fontSize = 11.sp, color = LocalModbusColors.current.dim)
                }
            }
        }
        assertGeometry(index = 0, expectedWidth = null)
        assertDiagonalGapIsConcentric(0)
        compose.onRoot().captureRoboImage("src/test/screenshots/segmented/$name.png")
    }

    /** 外层容器与项：四周内边距都是 3dp，项间距 3dp，项等高等宽，左右对称。 */
    private fun assertGeometry(index: Int, expectedWidth: Dp?) {
        val container = bounds(compose.onAllNodesWithTag(SegContainerTag)[index].getBoundsInRoot())
        val items = compose.onAllNodesWithTag(SegItemTag).fetchSemanticsNodes()
        // 属于这个容器的项：落在容器范围内
        val density = compose.activity.resources.displayMetrics.density
        val mine = (0 until items.size).map { compose.onAllNodesWithTag(SegItemTag)[it].getBoundsInRoot() }
            .map(::bounds)
            .filter { it.top >= container.top - 0.1.dp && it.bottom <= container.bottom + 0.1.dp && it.left >= container.left - 0.1.dp && it.right <= container.right + 0.1.dp }
        assertTrue("容器 $index 应有 ≥2 个项", mine.size >= 2)
        val pad = SegPadding
        assertClose("左内边距", mine.first().left - container.left, pad)
        assertClose("右内边距（与左对称）", container.right - mine.last().right, pad)
        mine.forEach {
            assertClose("上内边距", it.top - container.top, pad)
            assertClose("下内边距（与上对称）", container.bottom - it.bottom, pad)
        }
        mine.zipWithNext().forEach { (a, b) -> assertClose("项间距", b.left - a.right, SegGap) }
        val w = mine.first().right - mine.first().left
        mine.forEach { assertClose("项等宽", it.right - it.left, w) }
        if (expectedWidth != null) assertClose("容器宽度 = 稿面 150", container.right - container.left, expectedWidth)
        assertTrue(density > 2f)
    }

    /**
     * 同心：容器图像左上角 (0,0) 在圆角之外；沿 45° 对角线向内扫，先遇到容器底色，再遇到选中块（描边/底色）。
     * 同心圆弧在 45° 方向的间距 = 内边距（3dp）。
     */
    private fun assertDiagonalGapIsConcentric(index: Int) {
        val pm: PixelMap = compose.onAllNodesWithTag(SegContainerTag)[index].captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        val outside = pm[0, 0].toArgb()
        val container = pm[pm.width / 2, (3 * density).toInt() / 2].toArgb() // 容器顶边内边距带，容器底色
        // 选中块在每个容器里都是第一项（测试里 selected = 第一个或指定），取左上角方向扫描
        var d1 = -1
        var d2 = -1
        var k = 0
        while (k < pm.height / 2 && k < pm.width / 2) {
            val c = pm[k, k].toArgb()
            if (d1 < 0 && dist(c, outside) > dist(container, outside) / 2) d1 = k
            if (d1 >= 0 && d2 < 0 && k > d1 + 2 && dist(c, container) > 24) d2 = k
            k++
        }
        assertTrue("容器边缘应被找到（d1=$d1）", d1 > 0)
        assertTrue("选中块边缘应被找到（d2=$d2）", d2 > d1)
        val gapDp = (d2 - d1) * sqrt(2f) / density
        assertEquals("45° 方向容器边与选中块边的间距应 ≈ 内边距 ${SegPadding.value}dp，实际 $gapDp", SegPadding.value, gapDp, 0.8f)
    }

    @Test
    fun dockGapTargetsEightToTwelveDpAboveTheGestureBar() {
        // 手势导航：inset 24dp（手势条顶部在 inset 上沿下方约 12dp，实机截图量出）
        val gestureBarTopBelowInsetTop = 12.dp
        val gesture = dockBottomGap(24.dp) + gestureBarTopBelowInsetTop
        assertTrue("手势导航下 dock 到手势条上沿 ${gesture.value}dp 应在 8–12dp", gesture.value in 8f..12f)
        // 三键导航：inset 48dp，dock 离导航栏上沿 8dp
        assertEquals(8f, dockBottomGap(48.dp).value, 0f)
        // 没有 inset：保留一点底边距
        assertEquals(12f, dockBottomGap(0.dp).value, 0f)
    }

    @Test fun dockShadow_light() = dockShadow(ThemeMode.LIGHT, "dock_light")

    @Test fun dockShadow_dark() = dockShadow(ThemeMode.DARK, "dock_dark")

    /**
     * 阴影形状 = dock 圆角：在 dock 外接矩形的左下角外侧（离圆弧有距离的位置）阴影应明显比正下方同样距离处浅。
     * 如果阴影是直角矩形（或圆角不同），两处深浅差不多。
     */
    private fun dockShadow(theme: ThemeMode, name: String) {
        val settings = UiSettings(theme = theme)
        compose.setContent {
            ModbusTheme(settings) {
                Box(Modifier.fillMaxSize().background(Color(0xFFA0A8B4)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    FloatingDock(current = Destination.Workbench.route, onSelect = {})
                }
            }
        }
        compose.waitForIdle()
        val density = compose.activity.resources.displayMetrics.density
        val dock = compose.onNodeWithTag(DockTag).getBoundsInRoot()
        val root = compose.onRoot().captureToImage().toPixelMap()
        fun px(x: Dp, y: Dp) = root[(x.value * density).toInt(), (y.value * density).toInt()].toArgb()
        val bg = px(2.dp, 2.dp)
        // 正下方 3dp 处
        val below = px((dock.left + dock.right) / 2, dock.bottom + 1.dp)
        // 左下角外接矩形角点向内 3dp、向外 0（即 bbox 的左下角附近，在圆弧之外）
        val corner = px(dock.left + 3.dp, dock.bottom + 1.dp)
        val belowDark = dist(below, bg)
        val cornerDark = dist(corner, bg)
        assertTrue("正下方应有阴影（与背景差 $belowDark）", belowDark >= 5)
        assertTrue("圆角外侧阴影应比正下方浅（角点 $cornerDark < 正下方 $belowDark）", cornerDark < belowDark * 0.8)
        compose.onRoot().captureRoboImage("src/test/screenshots/segmented/$name.png")
    }

    private fun dist(a: Int, b: Int): Int =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            abs((a and 0xFF) - (b and 0xFF))

    private fun bounds(b: DpRect) = b

    private fun assertClose(message: String, actual: Dp, expected: Dp, tolerance: Dp = 0.6.dp) {
        assertEquals(message, expected.value, actual.value, tolerance.value)
    }

    private fun show(theme: ThemeMode, scale: FontScale, content: @Composable () -> Unit) {
        val settings = UiSettings(theme = theme, fontScale = scale)
        compose.setContent {
            ModbusTheme(settings) {
                Box(Modifier.fillMaxSize().background(LocalModbusColors.current.surface1)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
                }
            }
        }
        compose.waitForIdle()
    }
}
