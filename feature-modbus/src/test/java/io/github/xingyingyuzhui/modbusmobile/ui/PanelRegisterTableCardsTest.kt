package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.Conversion
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
import io.github.xingyingyuzhui.modbusmobile.ui.panel.WidgetCard
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

/** 寄存器表卡（P8b2）：行 / 表头 / HEX、质量态、✎ 触控 ≥48dp、禁用原因、预览与编辑只画不写、渲染图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelRegisterTableCardsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private fun table(id: String, name: String, point: PointDefinition, rows: Int, writable: Boolean) =
        Widget(id, WidgetKinds.REGISTER_TABLE, title = name, point = point, spanColumns = 2, tableRows = rows, tableWritable = writable)

    private val rw = table(
        "t-rw", "水泵参数",
        PointDefinition("p-rw", "水泵", 1, RegisterArea.HOLDING_REGISTER, 100, DataType.U16, conversion = Conversion(0.1, 0.0, 1), unitLabel = "℃"), 4, true,
    )
    private val ro = table(
        "t-ro", "电表读数",
        PointDefinition("p-ro", "电表", 2, RegisterArea.INPUT_REGISTER, 0, DataType.F32, conversion = Conversion(1.0, 0.0, 2), unitLabel = "kW"), 3, false,
    )
    private val coil = table("t-coil", "阀门状态", PointDefinition("p-co", "阀门", 1, RegisterArea.COIL, 10, DataType.BOOL), 4, true)
    private val widgets = listOf(rw, ro, coil)

    private fun reg(id: String, v: Int, scaled: Double, text: String, quality: Quality = Quality.OK) =
        PointSnapshot(id, quality, RawValue.Integer(v.toLong(), false), scaled, text, updatedAtMs = 1_000, updatedWallMs = PanelFixtures.WALL_MS)

    private fun flag(id: String, on: Boolean) =
        PointSnapshot(id, Quality.OK, RawValue.Flag(on), if (on) 1.0 else 0.0, null, updatedAtMs = 1_000, updatedWallMs = PanelFixtures.WALL_MS)

    private val snaps: Map<String, PointSnapshot> = buildMap {
        put("p-rw#0", reg("p-rw#0", 234, 23.4, "23.4"))
        put("p-rw#1", reg("p-rw#1", 82, 8.2, "8.2"))
        put("p-rw#2", reg("p-rw#2", 500, 50.0, "50.0", Quality.STALE))
        // 第 4 行没数据
        put("p-ro#0", PointSnapshot("p-ro#0", Quality.OK, RawValue.Real(1.5), 1.5, "1.50", updatedAtMs = 1_000, updatedWallMs = PanelFixtures.WALL_MS))
        put("p-co#0", flag("p-co#0", true))
        put("p-co#1", flag("p-co#1", false))
    }

    private lateinit var writer: PanelWriteController
    private lateinit var session: PanelFakeSession

    private fun show(
        role: Role? = Role.MASTER,
        theme: ThemeMode = ThemeMode.DARK,
        fontScale: Float = 1f,
        columns: Int = 2,
        snapshots: Map<String, PointSnapshot> = snaps,
        withWriter: Boolean = true,
        list: List<Widget> = widgets,
    ) {
        session = PanelFakeSession(role)
        val project = Project("default", "x", listOf(Panel("p1", "机房 A", columns = columns, widgets = list)))
        compose.setContent {
            val base = androidx.compose.ui.platform.LocalDensity.current
            writer = PanelWriteController(session, rememberCoroutineScope(), snapshotOf = { snapshots[it] })
            CompositionLocalProvider(
                LocalSession provides session,
                LocalOpenConnection provides {},
                io.github.xingyingyuzhui.modbusmobile.ui.curve.LocalWallClock provides { PanelFixtures.WALL_MS + 12_000 },
                LocalShellMode provides ShellMode.PORTRAIT,
                LocalDockInsets provides DockInsets(bottom = 76.dp),
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale),
            ) {
                ModbusTheme(UiSettings(theme = theme)) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(
                            PanelUiState.Ready(project, emptyList(), false), snapshots, PollStats(1000, 1012),
                            writer = if (withWriter) writer else null,
                        )
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

    @Test fun rowsShowAddressValueAndWireHex() {
        show()
        assertEquals("23.4", text("panel-row-value-t-rw-0"))
        assertEquals("8.2", text("panel-row-value-t-rw-1"))
        assertEquals("过期的行仍显示上一次的值", "50.0", text("panel-row-value-t-rw-2"))
        assertEquals("没读到的行是 —", "—", text("panel-row-value-t-rw-3"))
        assertEquals("00EA", text("panel-row-hex-t-rw-0"))
        assertEquals("0052", text("panel-row-hex-t-rw-1"))
        assertEquals("—", text("panel-row-hex-t-rw-3"))
        scrollTo("panel-row-hex-t-ro-0")
        assertEquals("F32 一行两个寄存器：3FC00000", "3FC0 0000", text("panel-row-hex-t-ro-0"))
        assertEquals("1.50", text("panel-row-value-t-ro-0"))
    }

    @Test fun coilTablesShowOnOffAndNoHexColumn() {
        show()
        scrollTo("panel-row-value-t-coil-0")
        assertEquals("ON", text("panel-row-value-t-coil-0"))
        assertEquals("OFF", text("panel-row-value-t-coil-1"))
        assertEquals("—", text("panel-row-value-t-coil-2"))
        assertEquals("线圈 / 离散输入没有 HEX 列", 0, count("panel-row-hex-t-coil-0"))
    }

    @Test fun writableRowsAreWholeRowTouchTargetsAndOnlyThoseHaveAPencilHint() {
        show()
        val card = bounds("panel-card-t-rw")
        for (i in 0 until 4) {
            // v3：整行可点（≥48 高、占满卡片内宽），✎ 只是灰色提示
            val t = touch("panel-row-t-rw-$i")
            assertTrue("行 $i 触控 ${t.width / dp}×${t.height / dp}", t.height >= 47.5f * dp && t.width / dp > card.width.value - 30f)
            val cfg = compose.onNodeWithTag("panel-row-t-rw-$i", useUnmergedTree = true).fetchSemanticsNode().config
            assertTrue("整行有点击动作", cfg.contains(SemanticsActions.OnClick))
            assertEquals(1, count("panel-row-edit-t-rw-$i"))
        }
        assertEquals("只读表没有 ✎", 0, count("panel-row-edit-t-ro-0"))
        scrollTo("panel-row-t-ro-0")
        val ro = compose.onNodeWithTag("panel-row-t-ro-0", useUnmergedTree = true).fetchSemanticsNode().config
        assertFalse("只读表的行不可点", ro.contains(SemanticsActions.OnClick))
        for (i in 0 until 4) {
            scrollTo("panel-row-t-coil-$i")
            val t = touch("panel-row-t-coil-$i")
            assertTrue("线圈行 $i 触控 ${t.height / dp}", t.height >= 47.5f * dp)
        }
    }

    @Test fun rowsAreAtLeast48HighWritableOrNotAndHexSitsUnderTheValue() {
        show()
        assertEquals(48f, bounds("panel-row-t-rw-0").height.value, 0.6f)
        scrollTo("panel-row-t-ro-0")
        assertEquals(48f, bounds("panel-row-t-ro-0").height.value, 0.6f)
        // HEX 在值的正下方、右对齐
        val v = bounds("panel-row-value-t-rw-0")
        val h = bounds("panel-row-hex-t-rw-0")
        assertTrue("HEX 在值下面", h.top >= v.bottom - 0.6.dp)
        assertEquals("右对齐", v.right.value, h.right.value, 0.6f)
    }

    @Test fun headerCarriesUnitTypeAndUnitAndAReadOnlyLock() {
        show()
        assertEquals("U1 · U16 · ℃", text("panel-table-info-t-rw"))
        scrollTo("panel-table-info-t-ro")
        assertEquals("U2 · F32 · kW", text("panel-table-info-t-ro"))
        assertTrue("可修改的表头是 ⚡", count("panel-writes-mark") >= 1)
        assertEquals("只读表头是锁", 1, count("panel-readonly-mark"))
    }

    @Test fun tableSpansTheFullRowAndStaysInsideTheCard() {
        show()
        val card = bounds("panel-card-t-rw")
        val grid = bounds("panel-grid")
        assertEquals("寄存器表占满一行宽度", grid.width.value, card.width.value, 0.6f)
        for (i in 0 until 4) {
            val e = bounds("panel-row-edit-t-rw-$i")
            assertTrue(e.right <= card.right && bounds("panel-row-value-t-rw-$i").left >= card.left)
        }
        // 下一张表在它下面，不重叠
        scrollTo("panel-card-t-ro")
        assertTrue(bounds("panel-card-t-ro").top >= bounds("panel-card-t-rw").bottom)
    }

    @Test fun badgeFollowsTheWorstRowAndFooterOnlyAppearsForProblems() {
        show()
        // t-rw 有一行过期 → 卡片标签是“过期”，底行说明读取失败、显示的是多久前的值
        assertEquals("读取失败的那一行决定说明：这里是过期", "读取失败 · 12 秒前的值", text("panel-table-meta-t-rw"))
        compose.onNodeWithText("过期", useUnmergedTree = true).assertExists()
        // 全部正常的表没有底行，也没有“正常”标签
        assertEquals(0, count("panel-table-meta-t-ro"))
        assertEquals(0, count("panel-table-meta-t-coil"))
        assertEquals(0, compose.onAllNodesWithText("正常", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun tappingAPencilOnlyOpensTheEntryBox() {
        show()
        compose.onNodeWithTag("panel-row-edit-t-rw-1", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        val entry = writer.entry
        assertNotNull(entry)
        assertEquals(1, entry!!.index)
        assertNull("点 ✎ 只是请求，没有确认弹窗更没有写入", writer.pending)
        compose.onNodeWithTag("row-entry", useUnmergedTree = true).assertExists()
        assertEquals("修改 · 水泵参数 第 2 行", text("row-entry-title"))
    }

    @Test fun tappingACoilPencilAsksForConfirmationDirectly() {
        show()
        scrollTo("panel-row-edit-t-coil-0")
        compose.onNodeWithTag("panel-row-edit-t-coil-0", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertNull(writer.entry)
        assertEquals("当前 ON → 目标 OFF", listOf(false), writer.pending!!.write.plan.bits)
    }

    @Test fun slaveAndIdleDisablePencilsWithAReason() {
        for ((role, reason) in listOf(Role.SLAVE to "从站模式不能写设备", null to "未连接主站，不能写设备")) {
            show(role = role)
            compose.onNodeWithTag("panel-row-t-rw-0", useUnmergedTree = true).assertIsNotEnabled()
            assertEquals(reason, text("panel-table-note-t-rw"))
            compose.onNodeWithTag("panel-row-edit-t-rw-0", useUnmergedTree = true).performClick()
            assertNull(writer.entry)
            compose.activityRule.scenario.recreate()
        }
    }

    @Test fun masterPencilsAreEnabled() {
        show()
        compose.onNodeWithTag("panel-row-t-rw-0", useUnmergedTree = true).assertIsEnabled()
    }

    @Test fun withoutAWriterPencilsAreDrawnButDoNothing() {
        // 向导预览用的就是这条路径
        show(withWriter = false)
        assertEquals(4, count("panel-row-edit-t-rw-0") * 4)
        val cfg = compose.onNodeWithTag("panel-row-edit-t-rw-0", useUnmergedTree = true).fetchSemanticsNode().config
        assertFalse("没有 writer 时 ✎ 没有点击动作", cfg.contains(SemanticsActions.OnClick))
        val row = compose.onNodeWithTag("panel-row-t-rw-0", useUnmergedTree = true).fetchSemanticsNode().config
        assertFalse("没有 writer 时整行也不可点", row.contains(SemanticsActions.OnClick))
    }

    @Test fun editModeCardIsASummaryWithoutRowsOrPencils() {
        compose.setContent {
            ModbusTheme(UiSettings(theme = ThemeMode.DARK)) {
                Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                    WidgetCard(rw, null, Modifier.fillMaxSize(), editing = true)
                }
            }
        }
        compose.waitForIdle()
        assertEquals("4 行 · HR100–HR103 · 允许修改", text("panel-table-summary-t-rw"))
        assertEquals(0, count("panel-row-edit-t-rw-0"))
        assertEquals(0, count("panel-row-t-rw-0"))
    }

    @Test fun largeFontKeepsEverythingInsideTheCard() {
        show(fontScale = 1.3f)
        val card = bounds("panel-card-t-rw")
        for (i in 0 until 4) {
            assertTrue(bounds("panel-row-edit-t-rw-$i").right <= card.right)
            assertTrue(bounds("panel-row-hex-t-rw-$i").right <= bounds("panel-row-edit-t-rw-$i").left + 0.6.dp)
        }
    }

    @Test fun threeColumnsGridStillGivesTheTableTwoColumns() {
        show(columns = 3)
        val card = bounds("panel-card-t-rw")
        val grid = bounds("panel-grid")
        assertEquals("跨 2 列 = 2/3 宽（含间距）", (grid.width.value - 16f) / 3f * 2f + 8f, card.width.value, 1f)
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_table_cards_dark() { show(); shot("table_cards_dark.png") }
    @Test fun render_table_cards_light() { show(theme = ThemeMode.LIGHT); shot("table_cards_light.png") }
    @Test fun render_table_cards_slave_dark() { show(role = Role.SLAVE); shot("table_cards_slave_dark.png") }
    @Test fun render_table_cards_font13_dark() { show(fontScale = 1.3f); shot("table_cards_font13_dark.png") }
    @Test fun render_table_cards_timeout_dark() {
        val bad = snaps + mapOf(
            "p-rw#0" to reg("p-rw#0", 234, 23.4, "23.4", Quality.TIMEOUT).copy(failures = 3),
            "p-rw#1" to reg("p-rw#1", 82, 8.2, "8.2", Quality.ERROR),
        )
        show(snapshots = bad)
        shot("table_cards_timeout_dark.png")
    }

    @Test fun render_row_entry_dark() {
        show()
        compose.onNodeWithTag("panel-row-edit-t-rw-1", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        shot("table_row_entry_dark.png")
    }

    @Test fun render_row_entry_error_light() {
        show(theme = ThemeMode.LIGHT)
        compose.onNodeWithTag("panel-row-edit-t-rw-0", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("row-entry-value")), useUnmergedTree = true).performTextReplacement("70000")
        compose.onNodeWithTag("row-entry-next", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertNull("越界不生成计划", writer.pending)
        assertNotNull("输入框还开着", writer.entry)
        compose.onNodeWithText("超出 U16 能表示的范围", useUnmergedTree = true).assertExists()
        shot("table_row_entry_error_light.png")
    }

    @Test fun nextWithAValidValueShowsTheWriteFrameBeforeAnythingIsSent() {
        show(theme = ThemeMode.LIGHT)
        compose.onNodeWithTag("panel-row-edit-t-rw-0", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("row-entry-next", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertNull(writer.entry)
        assertNotNull(writer.pending)
        shot("table_row_confirm_light.png")
    }

    @Test fun entryValidationMessagesForEmptyAndGarbage() {
        show()
        compose.onNodeWithTag("panel-row-edit-t-rw-0", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        val field = hasSetTextAction() and hasAnyAncestor(hasTestTag("row-entry-value"))
        compose.onNode(field, useUnmergedTree = true).performTextReplacement("")
        compose.onNodeWithTag("row-entry-next", useUnmergedTree = true).performClick()
        compose.onNodeWithText("请输入新值", useUnmergedTree = true).assertExists()
        compose.onNode(field, useUnmergedTree = true).performTextReplacement("-")
        compose.onNodeWithTag("row-entry-next", useUnmergedTree = true).performClick()
        compose.onNodeWithText("不是有效的数字", useUnmergedTree = true).assertExists()
        assertNotNull(writer.entry)
        compose.onNodeWithTag("row-entry-cancel", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertNull(writer.entry)
        assertNull(writer.pending)
        assertEquals("row-entry 浮层关闭", 0, count("row-entry"))
    }

    @Test fun scrimTapAndCancelCloseTheEntryWithoutSending() {
        show()
        compose.onNodeWithTag("panel-row-edit-t-rw-0", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("row-entry-scrim", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertNull(writer.entry)
        assertNull(writer.pending)
    }
}
