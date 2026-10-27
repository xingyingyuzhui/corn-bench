package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointError
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.ErrorKind
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdBand
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdLevel
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
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

/** 仪表卡（P8a）：半圆值弧 + 量程 + 阈值分段；只读，没有任何写入入口。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelGaugeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private fun gaugePoint(id: String, base: PointDefinition, min: Double, max: Double, bands: List<ThresholdBand> = emptyList()) =
        base.copy(id = id, rangeMin = min, rangeMax = max, thresholds = bands)

    private val press = gaugePoint(
        "g-press", PanelFixtures.pressure, 0.0, 1.2,
        listOf(ThresholdBand(ThresholdLevel.WARN, min = 0.8, max = 1.0), ThresholdBand(ThresholdLevel.ALERT, min = 1.0)),
    )
    private val temp = gaugePoint("g-temp", PanelFixtures.temp, -10.0, 50.0)
    private val valve = gaugePoint("g-valve", PanelFixtures.valve, 0.0, 100.0)
    private val cur = gaugePoint("g-cur", PanelFixtures.current, 0.0, 30.0)

    private val widgets = listOf(
        Widget("g1", WidgetKinds.GAUGE, point = temp),
        Widget("g2", WidgetKinds.GAUGE, point = press),
        Widget("g3", WidgetKinds.GAUGE, point = valve),
        Widget("g4", WidgetKinds.GAUGE, point = cur),
    )

    private fun ok(p: PointDefinition, raw: Long, eng: Double, text: String, level: ThresholdLevel = ThresholdLevel.NORMAL) =
        PointSnapshot(p.id, Quality.OK, RawValue.Integer(raw, false), eng, text, level, 1_000, PanelFixtures.WALL_MS)

    private val snaps = mapOf(
        "g-temp" to ok(temp, 234, 23.4, "23.4"),
        "g-press" to ok(press, 82, 0.82, "0.82", ThresholdLevel.WARN),
        "g-cur" to PointSnapshot(
            "g-cur", Quality.TIMEOUT, RawValue.Integer(126, false), 12.6, "12.6", ThresholdLevel.NORMAL, 1_000, PanelFixtures.WALL_MS,
            failures = 3, error = PointError(ErrorKind.TIMEOUT),
        ),
    )

    private fun show(
        snapshots: Map<String, PointSnapshot> = snaps,
        theme: ThemeMode = ThemeMode.DARK,
        columns: Int = 2,
        fontScale: Float = 1f,
    ) {
        val project = Project("default", "x", listOf(Panel("p1", "机房 A", columns = columns, widgets = widgets)))
        compose.setContent {
            val base = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalShellMode provides ShellMode.PORTRAIT,
                LocalDockInsets provides DockInsets(bottom = 76.dp),
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale),
            ) {
                ModbusTheme(UiSettings(theme = theme)) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(PanelUiState.Ready(project, emptyList(), false), snapshots, PollStats(1000, 1012))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test fun showsValueUnitRangeEndsAndBadges() {
        show()
        compose.onNodeWithTag("panel-value-g1", useUnmergedTree = true).assertTextEquals("23.4")
        compose.onNodeWithTag("panel-value-g2", useUnmergedTree = true).assertTextEquals("0.82")
        fun desc(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        assertEquals("仪表，量程 -10 到 50", desc("panel-gauge-g1"))
        assertEquals("仪表，量程 0 到 1.2", desc("panel-gauge-g2"))
        compose.onNodeWithText("预警", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("℃", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("HR100", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun noDataShowsDashAndNoArcOrFakeZero() {
        show()
        compose.onNodeWithTag("panel-value-g3", useUnmergedTree = true).assertTextEquals("—")
        compose.onNodeWithText("HR130 · 等待数据", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun timeoutKeepsLastValueAndSaysSo() {
        show()
        compose.onNodeWithTag("panel-value-g4", useUnmergedTree = true).assertTextEquals("12.6")
        compose.onNodeWithText("连续超时 3 次", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun gaugeCardsAreEqualHeightInTheirRowAndInsideTheScreen() {
        show()
        val a = bounds("panel-card-g1")
        val b = bounds("panel-card-g2")
        assertEquals(a.top.value, b.top.value, 0.6f)
        assertEquals(a.height.value, b.height.value, 0.6f)
        assertEquals(16f, a.left.value, 0.6f)
        assertEquals(360f - 16f, b.right.value, 0.6f)
        assertTrue("半圆图高度 ${bounds("panel-gauge-g1").height.value}", bounds("panel-gauge-g1").height.value >= 60f)
    }

    @Test fun gaugeCardsAreReadOnlyHaveNoClickOrWriteEntry() {
        show()
        listOf("g1", "g2").forEach { id ->
            val n = compose.onNodeWithTag("panel-card-$id", useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("仪表卡没有点击动作", !n.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick))
        }
        assertEquals(0, count("curve-expand-g1"))
    }

    @Test fun largeFontKeepsValueInsideTheCard() {
        show(fontScale = 1.3f)
        val card = bounds("panel-card-g2")
        val value = bounds("panel-value-g2")
        assertTrue(value.left >= card.left && value.right <= card.right)
    }

    @Test fun threeColumnsStillFits() {
        show(columns = 3)
        val c = bounds("panel-card-g1")
        assertTrue("三列下卡宽 ${c.width.value}", c.width.value in 80f..120f)
        assertTrue(bounds("panel-value-g1").right <= c.right)
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_gauge_panel_dark() { show(); shot("gauge_panel_dark.png") }

    @Test fun render_gauge_panel_light() { show(theme = ThemeMode.LIGHT); shot("gauge_panel_light.png") }

    @Test fun render_gauge_panel_alert_dark() {
        val alert = ok(press, 105, 1.05, "1.05", ThresholdLevel.ALERT)
        show(snaps + ("g-press" to alert))
        shot("gauge_panel_alert_dark.png")
    }
}
