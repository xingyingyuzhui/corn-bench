package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelContent
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelUiState
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelWriteController
import io.github.xingyingyuzhui.modbusmobile.ui.shell.DockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** 开关 / 按钮卡（P8b1）：状态、触控 ≥48dp、禁用原因、广播红字、点开关只是请求（不直接写）、渲染图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelWriteCardsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private fun p(id: String, name: String, unit: Int, area: RegisterArea, address: Int, type: DataType, bit: Int = 0) =
        PointDefinition(id, name, unit, area, address, type, bit = bit)

    private val widgets = listOf(
        Widget("s-on", WidgetKinds.SWITCH, point = p("p1", "压缩机", 1, RegisterArea.COIL, 0, DataType.BOOL), onLabel = "运行", offLabel = "停止"),
        Widget("s-off", WidgetKinds.SWITCH, point = p("p2", "电加热", 1, RegisterArea.HOLDING_REGISTER, 140, DataType.BIT, bit = 3)),
        Widget("s-unknown", WidgetKinds.SWITCH, point = p("p3", "旁通阀", 1, RegisterArea.COIL, 2, DataType.BOOL)),
        Widget("s-stale", WidgetKinds.SWITCH, point = p("p4", "风机", 1, RegisterArea.COIL, 3, DataType.BOOL)),
        Widget("b-coil", WidgetKinds.BUTTON, point = p("p5", "启动压缩机", 1, RegisterArea.COIL, 5, DataType.BOOL), buttonLabel = "启动", writeValue = 1.0),
        Widget("b-bcast", WidgetKinds.BUTTON, point = p("p6", "全部停机", 0, RegisterArea.COIL, 9, DataType.BOOL), buttonLabel = "全停", writeValue = 0.0),
        Widget("b-reg", WidgetKinds.BUTTON, point = p("p7", "设定值", 1, RegisterArea.HOLDING_REGISTER, 20, DataType.U16), buttonLabel = "设为 500", writeValue = 500.0),
        Widget("b-plain", WidgetKinds.BUTTON, point = p("p8", "复位", 1, RegisterArea.COIL, 7, DataType.BOOL), writeValue = 1.0),
    )

    private fun ok(id: String, on: Boolean, quality: Quality = Quality.OK) =
        PointSnapshot(id, quality, RawValue.Flag(on), if (on) 1.0 else 0.0, null, updatedAtMs = 1_000, updatedWallMs = PanelFixtures.WALL_MS)

    private val snaps = mapOf(
        "p1" to ok("p1", true),
        "p2" to ok("p2", false),
        "p4" to ok("p4", true, Quality.STALE),
    )

    private lateinit var writer: PanelWriteController
    private lateinit var session: PanelFakeSession

    private fun show(
        role: Role? = Role.MASTER,
        theme: ThemeMode = ThemeMode.DARK,
        fontScale: Float = 1f,
        columns: Int = 2,
        snapshots: Map<String, PointSnapshot> = snaps,
    ) {
        session = PanelFakeSession(role)
        val project = Project("default", "x", listOf(Panel("p1", "机房 A", columns = columns, widgets = widgets)))
        compose.setContent {
            val base = androidx.compose.ui.platform.LocalDensity.current
            writer = PanelWriteController(session, rememberCoroutineScope())
            CompositionLocalProvider(
                LocalSession provides session,
                LocalOpenConnection provides {},
                LocalShellMode provides ShellMode.PORTRAIT,
                LocalDockInsets provides DockInsets(bottom = 76.dp),
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale),
            ) {
                ModbusTheme(UiSettings(theme = theme)) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(PanelUiState.Ready(project, emptyList(), false), snapshots, PollStats(1000, 1012), writer = writer)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
    private fun touch(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot
    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private val dp get() = compose.density.density
    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun scrollTo(tag: String) = runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }

    @Test fun switchesShowStateLabelsAndUnknownIsADash() {
        show()
        assertEquals("运行", text("panel-value-s-on"))
        assertEquals("关", text("panel-value-s-off"))
        assertEquals("—", text("panel-value-s-unknown"))
        assertEquals("过期的状态仍显示上一次的值", "开", text("panel-value-s-stale"))
    }

    @Test fun touchTargetsAreAtLeast48dp() {
        show()
        for (id in listOf("s-on", "s-off", "s-unknown", "s-stale")) {
            val t = touch("panel-switch-$id")
            assertTrue("开关 $id 触控 ${t.width / dp}×${t.height / dp}", t.width >= 47.5f * dp && t.height >= 47.5f * dp)
        }
        for (id in listOf("b-coil", "b-bcast", "b-reg", "b-plain")) {
            scrollTo("panel-button-$id")
            val t = touch("panel-button-$id")
            assertTrue("按钮 $id 触控高 ${t.height / dp}", t.height >= 47.5f * dp)
            assertTrue("按钮 $id 触控宽 ${t.width / dp}", t.width >= 47.5f * dp)
        }
    }

    @Test fun unknownAndStaleSwitchesAreDisabledAndSayWhy() {
        show()
        compose.onNodeWithTag("panel-switch-s-unknown", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("panel-switch-s-stale", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("panel-switch-s-on", useUnmergedTree = true).assertIsEnabled()
        compose.onNodeWithTag("panel-switch-s-unknown", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("panel-switch-s-stale", useUnmergedTree = true).performClick()
        assertNull("读不到当前状态时点开关什么都不会发生", writer.pending)
    }

    @Test fun tappingASwitchOnlyRequestsAConfirmation() {
        show()
        compose.onNodeWithTag("panel-switch-s-on", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        val pending = writer.pending
        assertNotNull("点开关 = 请求确认", pending)
        assertEquals("s-on", pending!!.widget.id)
        // 当前是 ON，请求的是 OFF
        assertEquals(listOf(false), pending.write.plan.bits)
    }

    @Test fun tappingAButtonOnlyRequestsAConfirmation() {
        show()
        scrollTo("panel-button-b-bcast")
        compose.onNodeWithTag("panel-button-b-bcast", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue(writer.pending!!.write.broadcast)
    }

    @Test fun buttonNoteSaysWhatWillBeWrittenAndWhere() {
        show()
        scrollTo("panel-button-note-b-coil")
        // v3：写入目标写成“地址 ← 值”，Unit 靠右；广播在名称行有红色标签
        assertEquals("C5 ← ON", text("panel-button-note-b-coil"))
        assertEquals("C9 ← OFF · Unit 0，无回应", text("panel-button-note-b-bcast"))
        assertEquals("HR20 ← 500", text("panel-button-note-b-reg"))
        compose.onNodeWithTag("panel-broadcast-b-bcast", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("规范 54：卡上红字写明广播（所有从站）", "广播（所有从站）", text("panel-broadcast-b-bcast"))
    }

    @Test fun slaveAndIdleDisableAllWriteControlsWithAReason() {
        for ((role, reason) in listOf(Role.SLAVE to "从站模式不能写设备", null to "未连接主站，不能写设备")) {
            show(role = role, snapshots = emptyMap())
            for (id in listOf("s-on", "s-off")) compose.onNodeWithTag("panel-switch-$id", useUnmergedTree = true).assertIsNotEnabled()
            for (id in listOf("b-coil", "b-bcast")) {
                scrollTo("panel-button-note-$id")
                assertEquals(reason, text("panel-button-note-$id"))
            }
            compose.onNodeWithTag("panel-switch-s-on", useUnmergedTree = true).performClick()
            assertNull(writer.pending)
            compose.activityRule.scenario.recreate()
        }
    }

    @Test fun cardsInTheSameRowHaveEqualHeightAndStayInside() {
        show()
        val a = bounds("panel-card-s-on")
        val b = bounds("panel-card-s-off")
        assertEquals(a.top.value, b.top.value, 0.6f)
        assertEquals(a.height.value, b.height.value, 0.6f)
        assertTrue(a.right.value <= b.left.value)
        scrollTo("panel-card-b-coil")
        val c = bounds("panel-card-b-coil")
        val d = bounds("panel-card-b-bcast")
        assertEquals(c.height.value, d.height.value, 0.6f)
    }

    @Test fun largeFontKeepsEverythingInsideTheCards() {
        show(fontScale = 1.3f)
        val card = bounds("panel-card-s-on")
        val sw = bounds("panel-switch-s-on")
        val value = bounds("panel-value-s-on")
        assertTrue(sw.right <= card.right && value.left >= card.left)
        assertTrue("文字和开关不重叠", value.right <= sw.left + 0.6.dp)
    }

    @Test fun threeColumnsStillFit() {
        show(columns = 3)
        val c = bounds("panel-card-s-on")
        assertTrue(bounds("panel-switch-s-on").right <= c.right)
        scrollTo("panel-card-b-coil")
        assertTrue(bounds("panel-button-b-coil").right <= bounds("panel-card-b-coil").right)
    }

    @Test fun cardsWithoutAWriterAreDisplayOnly() {
        // 向导预览 / 编辑页用的就是这条路径：没有 writer → 没有任何点击处理
        session = PanelFakeSession(Role.MASTER)
        compose.setContent {
            CompositionLocalProvider(LocalSession provides session, LocalOpenConnection provides {}, LocalShellMode provides ShellMode.PORTRAIT, LocalDockInsets provides DockInsets()) {
                ModbusTheme(UiSettings(theme = ThemeMode.DARK)) {
                    PanelContent(PanelUiState.Ready(Project("default", "x", listOf(Panel("p1", "a", widgets = widgets))), emptyList(), false), snaps, PollStats(1000, 1000))
                }
            }
        }
        compose.waitForIdle()
        val sw = compose.onNodeWithTag("panel-switch-s-on", useUnmergedTree = true).fetchSemanticsNode().config
        assertFalse("没有 writer 时开关没有点击 / 切换动作", sw.contains(SemanticsActions.OnClick) || sw.contains(SemanticsProperties.ToggleableState))
        assertEquals("没有 writer 时按钮是只画样子的", 4, count("panel-button-visual-b-coil") + count("panel-button-visual-b-bcast") + count("panel-button-visual-b-reg") + count("panel-button-visual-b-plain"))
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_write_cards_dark() { show(); shot("write_cards_dark.png") }

    @Test fun render_write_cards_light() { show(theme = ThemeMode.LIGHT); shot("write_cards_light.png") }

    @Test fun render_write_cards_slave_dark() { show(role = Role.SLAVE, snapshots = emptyMap()); shot("write_cards_slave_dark.png") }

    @Test fun render_write_cards_font13_dark() { show(fontScale = 1.3f); shot("write_cards_font13_dark.png") }
}
