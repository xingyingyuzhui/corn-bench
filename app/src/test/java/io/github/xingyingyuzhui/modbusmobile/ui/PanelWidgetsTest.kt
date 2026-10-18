package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelContent
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelUiState
import io.github.xingyingyuzhui.modbusmobile.ui.shell.DockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** 面板页（P4）：卡片网格、质量态、对齐 / 间距 / 触控尺寸逐项对稿，并输出 Roborazzi 渲染图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelWidgetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private val stats = PollStats(1000, 1012)
    private var paused = false
    private var toggled = 0
    private var selected: String? = null

    private fun show(
        theme: ThemeMode = ThemeMode.DARK,
        state: PanelUiState = PanelUiState.Ready(PanelFixtures.project(), emptyList(), false),
        snapshots: Map<String, PointSnapshot> = PanelFixtures.snapshots,
        stats: PollStats? = this.stats,
        role: Role? = Role.MASTER,
        landscape: Boolean = false,
        selectedId: String? = null,
    ) {
        val settings = UiSettings(theme = theme)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(role),
                LocalOpenConnection provides {},
                LocalShellMode provides if (landscape) ShellMode.LANDSCAPE else ShellMode.PORTRAIT,
                // 竖屏 dock 浮在底部（约 76dp 内容留白），横屏 rail 占左边 88dp。
                LocalDockInsets provides if (landscape) DockInsets(start = 88.dp) else DockInsets(bottom = 76.dp),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(
                            state, snapshots, stats, paused, selectedId,
                            onSelectPanel = { selected = it }, onTogglePause = { toggled++; paused = !paused },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    // ---- 内容 ----

    @Test fun allFourKindsRenderRealValuesAndMeta() {
        show()
        text("panel-value-w-temp").assertTextEquals("23.4")
        compose.onNodeWithText("℃", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("HR100 · 10:21:07", useUnmergedTree = true).assertIsDisplayed()
        text("panel-value-w-press").assertTextEquals("0.82")
        text("panel-value-w-comp").assertTextEquals("运行")
        text("panel-value-w-mode").assertTextEquals("制冷")
        compose.onNodeWithText("HR120 = 2", useUnmergedTree = true).assertIsDisplayed()
        text("panel-value-w-valve").assertTextEquals("62 %")
        compose.onNodeWithText("HR130 · 0–100", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun qualityStatesAreVisibleNotJustColors() {
        show()
        // 超时：值保留（上一次的 12.6）+ “超时”badge + “连续超时 3 次”
        text("panel-value-w-cur").assertTextEquals("12.6")
        compose.onNodeWithText("超时", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("连续超时 3 次", useUnmergedTree = true).assertIsDisplayed()
        // 阈值：0.82 ≥ 0.8 → 预警，其余正常
        compose.onNodeWithText("预警", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun staleKeepsValueAndSaysSo() {
        val stale = PanelFixtures.snapshots["temp"]!!.copy(
            quality = io.github.xingyingyuzhui.modbusmobile.application.panel.Quality.STALE, failures = 1,
        )
        show(snapshots = PanelFixtures.snapshots + ("temp" to stale))
        text("panel-value-w-temp").assertTextEquals("23.4")
        compose.onNodeWithText("过期", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("读取失败，显示上次的值", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun undefinedStateIsShownNotHidden() {
        val odd = PanelFixtures.snapshots["mode"]!!.copy(raw = io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue.Integer(7, false), engineering = 7.0)
        show(snapshots = PanelFixtures.snapshots + ("mode" to odd))
        text("panel-value-w-mode").assertTextEquals("未定义（7）")
    }

    @Test fun lampOffUsesOffLabel() {
        val off = PanelFixtures.snapshots["comp"]!!.copy(
            raw = io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue.Flag(false), engineering = 0.0,
        )
        show(snapshots = PanelFixtures.snapshots + ("comp" to off))
        text("panel-value-w-comp").assertTextEquals("停止")
    }

    @Test fun noDataShowsDashAndWaiting() {
        show(snapshots = emptyMap())
        text("panel-value-w-temp").assertTextEquals("—")
        compose.onNodeWithText("HR100 · 等待数据", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun progressBarFillMatchesValue() {
        show()
        val bar = bounds("panel-bar-w-valve")
        assertEquals("进度条高 8", 8f, bar.height.value, 0.6f)
        // 填充 = 62%：用整条宽度和填充节点比较
        val fill = compose.onNodeWithTag("panel-bar-w-valve", useUnmergedTree = true)
        assertTrue(bar.width.value > 100f)
        fill.assertIsDisplayed()
    }

    // ---- 版式（对稿）----

    @Test fun portraitGridIsTwoColumnsWithGap8AndEqualRowHeights() {
        show()
        val a = bounds("panel-card-w-temp")
        val b = bounds("panel-card-w-press")
        val c = bounds("panel-card-w-comp")
        assertEquals("同行等宽", a.width.value, b.width.value, 0.6f)
        assertEquals("列间距 8", 8f, (b.left - a.right).value, 0.6f)
        assertEquals("同行等高", a.height.value, b.height.value, 0.6f)
        assertEquals("行间距 8", 8f, (c.top - a.bottom).value, 0.6f)
        assertEquals("同行顶对齐", a.top.value, b.top.value, 0.6f)
        // 左右边距 16
        assertEquals(16f, a.left.value, 0.6f)
        assertEquals(360f - 16f, b.right.value, 0.6f)
    }

    @Test fun cardInnerPaddingMatchesDesign() {
        show()
        val card = bounds("panel-card-w-temp")
        val value = bounds("panel-value-w-temp")
        // border-box：内容离外缘 13（水平）；名称行在上边缘下 11
        assertEquals(13f, (value.left - card.left).value, 0.6f)
        val name = compose.onNodeWithText("供水温度", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(13f, (name.left - card.left).value, 0.6f)
        assertEquals(11f, (name.top - card.top).value, 3f)
    }

    @Test fun spanTwoCardIsTwoColumnsPlusGap() {
        val wide = PanelFixtures.widgets[0].copy(id = "w-wide", spanColumns = 2)
        val project = PanelFixtures.project().copy(
            panels = listOf(Panel("p1", "机房 A", widgets = listOf(wide) + PanelFixtures.widgets.drop(1))),
        )
        show(state = PanelUiState.Ready(project, emptyList(), false))
        val w = bounds("panel-card-w-wide")
        val single = bounds("panel-card-w-press")
        assertEquals(2 * single.width.value + 8f, w.width.value, 0.8f)
        assertEquals(16f, w.left.value, 0.6f)
    }

    @Test fun lampCardLampIs14dpAndLabelAligned() {
        show()
        val card = bounds("panel-card-w-comp")
        val label = bounds("panel-value-w-comp")
        // 灯 14 + 间距 10 → 文字左缘 = 内容左 13 + 14 + 10
        assertEquals(13f + 14f + 10f, (label.left - card.left).value, 1f)
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeIsFourColumnsWithoutSessionStrip() {
        show(landscape = true)
        val cards = PanelFixtures.widgets.map { bounds("panel-card-${it.id}") }
        val firstRowTop = cards[0].top
        assertEquals("横屏一行 4 张", 4, cards.count { kotlin.math.abs((it.top - firstRowTop).value) < 0.6f })
        assertEquals(0, compose.onAllNodesWithTag("panel-strip", useUnmergedTree = true).fetchSemanticsNodes().size)
        // 左边距 = dock 的 88
        assertEquals(88f, cards[0].left.value, 0.6f)
        assertEquals(8f, (cards[1].left - cards[0].right).value, 0.6f)
        compose.onNodeWithText("轮询 1012 ms", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 会话条 / 暂停 / 面板切换 ----

    @Test fun sessionStripShowsPollingAndPauseButtonIsTouchSized() {
        show()
        compose.onNodeWithText("主站 · 轮询中", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("设定 1000 ms · 实际 1012 ms", useUnmergedTree = true).assertIsDisplayed()
        val pause = compose.onNodeWithTag("panel-pause")
        pause.assertIsDisplayed()
        val touch = pause.fetchSemanticsNode().touchBoundsInRoot
        assertTrue("暂停按钮热区高 ≥48dp，实际 ${touch.height}", touch.height >= 47.5f * 1f * compose.density.density)
        pause.performClick()
        assertEquals(1, toggled)
    }

    @Test fun pausedStripSaysSoAndOffersResume() {
        paused = true
        show(stats = null)
        compose.onNodeWithText("主站 · 轮询已暂停", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("显示的是暂停前读到的值", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("继续", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun notMasterShowsIdleHintAndNoPauseButton() {
        show(role = Role.SLAVE, stats = null)
        compose.onNodeWithTag("panel-poll-hint").assertTextContains("需要主站连接", substring = true)
        assertEquals(0, compose.onAllNodesWithTag("panel-pause").fetchSemanticsNodes().size)
    }

    @Test fun multiplePanelsShowChipsAndSwitch() {
        val second = Panel("p2", "冷站", widgets = listOf(PanelFixtures.widgets[5].copy(id = "w-other")))
        val state = PanelUiState.Ready(PanelFixtures.project(second), emptyList(), false)
        show(state = state)
        compose.onNodeWithText("机房 A", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("冷站", useUnmergedTree = true).performClick()
        assertEquals("p2", selected)
        val chip = compose.onNodeWithText("冷站", useUnmergedTree = true)
        // 热区 ≥ 48dp 高
        val touch = compose.onAllNodes(androidx.compose.ui.test.hasText("冷站"), useUnmergedTree = false)
            .fetchSemanticsNodes().first().touchBoundsInRoot
        assertTrue("chip 热区高 ${touch.height}", touch.height >= 47.5f * compose.density.density)
        chip.assertIsDisplayed()
    }

    @Test fun selectedPanelShowsOnlyItsWidgets() {
        val second = Panel("p2", "冷站", widgets = listOf(PanelFixtures.widgets[5].copy(id = "w-other")))
        val state = PanelUiState.Ready(PanelFixtures.project(second), emptyList(), false)
        show(state = state, selectedId = "p2")
        compose.onNodeWithTag("panel-card-w-other", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("panel-card-w-temp", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun singlePanelHasNoChips() {
        show()
        assertEquals(0, compose.onAllNodesWithTag("panel-chips", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // ---- 渲染图（输出到 screenshots 目录，再复制到 docs/review/ui-pr-p4）----

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_panel_dark_portrait() { show(ThemeMode.DARK); shot("panel_widgets_dark_portrait.png") }

    @Test fun render_panel_light_portrait() { show(ThemeMode.LIGHT); shot("panel_widgets_light_portrait.png") }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun render_panel_dark_landscape() {
        show(ThemeMode.DARK, landscape = true, state = PanelUiState.Ready(PanelFixtures.project(Panel("p2", "冷站"), Panel("p3", "测试")), emptyList(), false))
        shot("panel_widgets_dark_landscape.png")
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun render_panel_light_landscape() { show(ThemeMode.LIGHT, landscape = true); shot("panel_widgets_light_landscape.png") }

    @Test fun render_panel_multi_dark_portrait() {
        val state = PanelUiState.Ready(PanelFixtures.project(Panel("p2", "冷站"), Panel("p3", "测试")), emptyList(), false)
        show(ThemeMode.DARK, state = state)
        shot("panel_widgets_multi_dark_portrait.png")
    }

    @Test fun render_panel_waiting_light_portrait() {
        show(ThemeMode.LIGHT, snapshots = emptyMap(), stats = null, role = Role.SLAVE)
        shot("panel_widgets_waiting_light_portrait.png")
    }
}
