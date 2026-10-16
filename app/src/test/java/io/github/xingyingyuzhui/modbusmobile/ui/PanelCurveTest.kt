package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.AxisRange
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveConfig
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveSeries
import io.github.xingyingyuzhui.modbusmobile.application.panel.LegendPosition
import io.github.xingyingyuzhui.modbusmobile.application.panel.LineStyle
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.application.panel.YAxis
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.curve.FixedCurveClock
import io.github.xingyingyuzhui.modbusmobile.ui.panel.EditActions
import io.github.xingyingyuzhui.modbusmobile.ui.panel.EditState
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelContent
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelEditor
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
import kotlin.math.sin

/** 曲线卡（P7）：面板里的曲线（标题 / 图例 / 双轴 / 缺测 / 游标 / 无障碍）、编辑模式里的曲线卡；渲染图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelCurveTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private val now = PanelFixtures.WALL_MS

    private fun curve(
        legend: LegendPosition = LegendPosition.TOP,
        cursor: Boolean = true,
        series: List<CurveSeries> = listOf(
            CurveSeries(PanelFixtures.temp, colorIndex = 0, axis = YAxis.LEFT),
            CurveSeries(PanelFixtures.pressure, colorIndex = 1, lineStyle = LineStyle.DASH, axis = YAxis.RIGHT),
        ),
    ) = Widget(
        "w-curve", WidgetKinds.CURVE, spanColumns = 2, spanRows = 2,
        curve = CurveConfig(series, windowSec = 60, legend = legend, cursor = cursor, left = AxisRange(), right = AxisRange()),
    )

    /** 60 秒、每秒一个样本：温度 20–26 摆动；压力 0.6–0.9；温度中间留一段 12 秒的缺测。 */
    private fun history(withGap: Boolean = true): SampleHistory {
        val h = SampleHistory()
        h.track(
            mapOf(
                "temp" to SampleHistory.Tracked(PanelFixtures.temp, 3_600_000, 20_000),
                "press" to SampleHistory.Tracked(PanelFixtures.pressure, 3_600_000, 20_000),
            ),
        )
        for (s in -75..0) {
            val t = now + s * 1000L
            if (!(withGap && s in -40..-28)) h.record("temp", t, 23 + 3 * sin(s / 8.0))
            h.record("press", t, 0.75 + 0.15 * sin(s / 11.0 + 1))
        }
        return h
    }

    private fun project(widget: Widget = curve()) = Project(
        "default", "x",
        listOf(Panel("p1", "机房 A", widgets = listOf(widget) + PanelFixtures.widgets.take(4))),
    )

    private fun show(
        widget: Widget = curve(),
        history: SampleHistory? = history(),
        theme: ThemeMode = ThemeMode.DARK,
        fontScale: Float = 1f,
        landscape: Boolean = false,
    ) {
        val settings = UiSettings(theme = theme)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalShellMode provides if (landscape) ShellMode.LANDSCAPE else ShellMode.PORTRAIT,
                LocalDockInsets provides if (landscape) DockInsets(start = 88.dp) else DockInsets(bottom = 76.dp),
                LocalDensity provides Density(base.density, fontScale),
            ) {
                FixedCurveClock(now) {
                    ModbusTheme(settings) {
                        Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                            PanelContent(
                                PanelUiState.Ready(project(widget), emptyList(), false), PanelFixtures.snapshots, PollStats(1000, 1012),
                                history = history,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showEdit(widget: Widget = curve(), fontScale: Float = 1f, theme: ThemeMode = ThemeMode.DARK) {
        val settings = UiSettings(theme = theme)
        val actions = EditActions({}, {}, { _, _ -> }, { _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalShellMode provides ShellMode.PORTRAIT,
                LocalDockInsets provides DockInsets(bottom = 76.dp),
                LocalDensity provides Density(base.density, fontScale),
            ) {
                FixedCurveClock(now) {
                    ModbusTheme(settings) {
                        Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                            PanelEditor(EditState(project(widget), "p1", false), PanelFixtures.snapshots, actions)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun desc(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()

    // ---- 内容 ----

    @Test fun titleNamesSeriesAndWindowAndLegendListsEachSeries() {
        show()
        compose.onNodeWithTag("curve-title-text-w-curve", useUnmergedTree = true).assertTextContains("供水温度 / 供水压力")
        compose.onNodeWithText(" · 1 分钟", useUnmergedTree = true).assertIsDisplayed()
        val title = bounds("curve-title-w-curve")
        val legend = bounds("curve-legend-w-curve")
        assertTrue("标题 + 图例放不下同一行时图例换行，不互相压住", legend.top >= title.bottom - 0.5.dp || legend.left >= title.right - 0.5.dp)
        compose.onNodeWithTag("curve-legend-w-curve", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("curve-chart-w-curve", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("有数据时不显示等待", 0, count("curve-waiting"))
    }

    @Test fun customTitleWinsOverSeriesNames() {
        show(curve().copy(title = "冷站趋势"))
        compose.onNodeWithTag("curve-title-text-w-curve", useUnmergedTree = true).assertTextContains("冷站趋势")
    }

    @Test fun veryLongTitleIsEllipsisedButTheWindowLabelStaysVisible() {
        show(curve().copy(title = "冷冻水系统供回水温度与压力趋势对比（含旁通阀开度与压缩机电流）"))
        compose.onNodeWithText(" · 1 分钟", useUnmergedTree = true).assertIsDisplayed()
        val card = bounds("panel-card-w-curve")
        assertTrue(bounds("curve-title-w-curve").right <= card.right)
    }

    @Test fun contentDescriptionSummarisesLatestValuesForScreenReaders() {
        show()
        val d = desc("curve-chart-w-curve")
        assertTrue(d, d.contains("1 分钟"))
        assertTrue(d, d.contains("供水温度 23.0") || d.contains("供水温度"))
        assertTrue(d, d.contains("℃"))
        assertTrue(d, d.contains("MPa"))
    }

    @Test fun emptyHistoryShowsWaitingNotAnEmptyShell() {
        show(history = SampleHistory())
        compose.onNodeWithTag("curve-waiting", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("curve-title-w-curve", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun nullHistoryAlsoShowsWaiting() {
        show(history = null)
        compose.onNodeWithTag("curve-waiting", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun legendCanBeBottomOrHidden() {
        show(curve(legend = LegendPosition.BOTTOM))
        val chart = bounds("curve-chart-w-curve")
        val legend = bounds("curve-legend-w-curve")
        assertTrue("底部图例在图下面", legend.top >= chart.bottom - 0.5.dp)
    }

    @Test fun hiddenLegendDrawsNoLegend() {
        show(curve(legend = LegendPosition.HIDDEN))
        assertEquals(0, count("curve-legend-w-curve"))
    }

    @Test fun threeSeriesTopLegendWrapsBelowTheTitle() {
        val third = CurveSeries(PanelFixtures.current.copy(id = "cur"), colorIndex = 2)
        val w = curve(series = curve().curve!!.series + third)
        show(w, history())
        val title = bounds("curve-title-w-curve")
        val legend = bounds("curve-legend-w-curve")
        assertTrue("三条以上图例换到标题下一行", legend.top >= title.bottom - 0.5.dp)
    }

    // ---- 版式 ----

    @Test fun curveCardSpansTwoColumnsAndTwoRowsAndSitsInTheGrid() {
        show()
        val c = bounds("panel-card-w-curve")
        val single = bounds("panel-card-w-temp")
        assertEquals(14f, c.left.value, 0.6f)
        assertEquals(360f - 14f, c.right.value, 0.6f)
        assertTrue("2×2 比普通卡高得多 ${c.height.value} vs ${single.height.value}", c.height.value >= 2 * single.height.value + 8f)
        assertTrue("2×2 绘图区 ≥160dp 高 ${bounds("curve-chart-w-curve").height.value}", bounds("curve-chart-w-curve").height.value >= 160f)
    }

    @Test fun curveCardWithOneRowStillKeepsAReadableChart() {
        show(curve().copy(spanRows = 1))
        assertTrue("图 ${bounds("curve-chart-w-curve").height.value}", bounds("curve-chart-w-curve").height.value >= 60f)
    }

    @Test fun largeFontKeepsTitleAndLegendInsideTheCard() {
        show(fontScale = 1.3f)
        val card = bounds("panel-card-w-curve")
        val title = bounds("curve-title-w-curve")
        val legend = bounds("curve-legend-w-curve")
        assertTrue(title.right <= card.right && title.left >= card.left)
        assertTrue(legend.right <= card.right && legend.left >= card.left)
        assertTrue("图仍有高度 ${bounds("curve-chart-w-curve").height.value}", bounds("curve-chart-w-curve").height.value >= 60f)
    }

    // ---- 游标 ----

    @Test fun tapOpensCursorWithTimeAndEveryValueThenSecondTapClosesIt() {
        show()
        assertEquals(0, count("curve-cursor"))
        compose.onNodeWithTag("curve-chart-w-curve", useUnmergedTree = true).performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onNodeWithTag("curve-cursor", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("curve-cursor-time", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(2, count("curve-cursor-0") + count("curve-cursor-1"))
        compose.onNodeWithTag("curve-chart-w-curve", useUnmergedTree = true).performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals("再点一下收起", 0, count("curve-cursor"))
    }

    @Test fun cursorOffSettingMeansTapDoesNothing() {
        show(curve(cursor = false))
        compose.onNodeWithTag("curve-chart-w-curve", useUnmergedTree = true).performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(0, count("curve-cursor"))
    }

    @Test fun cursorInTheGapShowsDashNotAnInventedValue() {
        show()
        // 缺测在 -41..-27 秒（最近样本都在 3 秒以外）：绘图区约占宽度 [0.1, 0.88]，-34 秒 ≈ 0.435
        compose.onNodeWithTag("curve-chart-w-curve", useUnmergedTree = true).performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * 0.435f, height / 2f))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("curve-cursor", useUnmergedTree = true).assertIsDisplayed()
        val texts = (0..1).map { i -> compose.onAllNodesWithTag("curve-cursor-$i", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") }
        assertTrue("温度在缺测段显示 —：$texts", texts[0] == "—")
        assertTrue("压力仍有值：$texts", texts[1]?.contains("MPa") == true)
    }

    // ---- 编辑模式 ----

    @Test fun editCardShowsNameSummaryAndLegendButNoLiveChart() {
        showEdit()
        compose.onNodeWithTag("panel-card-w-curve", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("⠿ 供水温度 / 供水压力", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2 个系列 · 1 分钟", substring = true, useUnmergedTree = true).assertIsDisplayed()
        assertEquals("编辑期间不画实时图", 0, count("curve-chart-w-curve"))
    }

    @Test fun editSizeButtonsOnCurveNeverOfferOneColumn() {
        showEdit()
        val c = bounds("edit-card-w-curve")
        assertEquals("曲线卡 2 列宽", 360f - 28f, c.width.value, 1f)
    }

    // ---- 渲染图 ----

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_curve_panel_dark() { show(); shot("curve_panel_dark.png") }

    @Test fun render_curve_panel_light() { show(theme = ThemeMode.LIGHT); shot("curve_panel_light.png") }

    @Test fun render_curve_panel_empty_dark() { show(history = SampleHistory()); shot("curve_panel_empty_dark.png") }

    @Test fun render_curve_panel_font13_dark() { show(fontScale = 1.3f); shot("curve_panel_font13_dark.png") }

    @Test fun render_curve_panel_cursor_dark() {
        show()
        compose.onNodeWithTag("curve-chart-w-curve", useUnmergedTree = true).performTouchInput { click(center) }
        compose.waitForIdle()
        shot("curve_panel_cursor_dark.png")
    }

    @Test fun render_curve_panel_four_series_bottom_legend_light() {
        val extra = listOf(
            CurveSeries(PanelFixtures.temp, 0), CurveSeries(PanelFixtures.pressure, 1, lineStyle = LineStyle.DASH, axis = YAxis.RIGHT),
            CurveSeries(PanelFixtures.valve, 2, lineStyle = LineStyle.DOT, fill = true),
            CurveSeries(PanelFixtures.current, 3, step = true, showPoints = true, axis = YAxis.RIGHT),
        )
        show(curve(legend = LegendPosition.BOTTOM, series = extra), theme = ThemeMode.LIGHT)
        shot("curve_panel_four_series_light.png")
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun render_curve_panel_dark_landscape() { show(landscape = true); shot("curve_panel_dark_landscape.png") }

    @Test fun render_curve_edit_dark() { showEdit(); shot("curve_edit_dark.png") }
}
