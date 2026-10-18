package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.panel.EditActions
import io.github.xingyingyuzhui.modbusmobile.ui.panel.EditError
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** 面板编辑模式（P5）：版式对稿、热区 ≥48dp、手势、确认流程、字体 1.3 倍、多面板 chip 滚动；渲染图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelEditTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private val calls = mutableListOf<String>()

    private val actions = EditActions(
        onRename = { calls += "rename:$it" },
        onColumns = { calls += "columns:$it" },
        onMove = { a, b -> calls += "move:$a>$b" },
        onSpan = { id, c, r -> calls += "span:$id:${c}x$r" },
        onDelete = { calls += "delete:$it" },
        onSelectPanel = { calls += "select:$it" },
        onAddPanel = { calls += "addPanel" },
        onDeletePanel = { calls += "deletePanel" },
        onDone = { calls += "done" },
        onCancel = { calls += "cancel" },
        onAddWidget = { calls += "addWidget" },
        onOpenWidget = { calls += "open:$it" },
    )

    private fun editState(project: Project = PanelFixtures.project(), panelId: String = "p1", dirty: Boolean = false, error: EditError? = null) =
        EditState(project, panelId, dirty, error = error)

    private fun showEditor(
        edit: EditState = editState(),
        theme: ThemeMode = ThemeMode.DARK,
        landscape: Boolean = false,
        fontScale: Float = 1f,
        snapshots: Map<String, PointSnapshot> = PanelFixtures.snapshots,
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
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelEditor(edit, snapshots, actions)
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

    // ---- 版式 ----

    @Test fun headerAndFieldsMatchDesign() {
        showEditor()
        compose.onNodeWithText("编辑面板", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("完成", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("面板名称", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("列数", useUnmergedTree = true).assertIsDisplayed()
        // 面板名称格 + 列数格同高，列数格宽 110
        assertEquals(110f, bounds("edit-columns").width.value, 0.6f)
        assertEquals(bounds("edit-name").height.value, bounds("edit-columns").height.value, 1f)
        // 页面左右边距 16
        assertEquals(16f, bounds("edit-name").left.value, 0.6f)
        assertEquals(360f - 16f, bounds("edit-columns").right.value, 0.6f)
    }

    @Test fun cardsAreDashedWithPrefixAndAtLeast96High() {
        showEditor()
        for (w in PanelFixtures.widgets) {
            val card = bounds("edit-card-${w.id}")
            assertTrue("${w.id} 高 ${card.height.value}", card.height.value >= 95.5f)
        }
        // 名称前有 ⠿，且编辑模式不显示 badge / meta 行
        compose.onNodeWithText("⠿ 供水温度", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, count("panel-meta"))
        assertEquals(PanelFixtures.widgets.size, count("edit-outline"))
        // 两列、间距 8、边距 16
        val a = bounds("edit-card-w-temp")
        val b = bounds("edit-card-w-press")
        assertEquals(8f, (b.left - a.right).value, 0.6f)
        assertEquals(16f, a.left.value, 0.6f)
        assertEquals(360f - 16f, b.right.value, 0.6f)
    }

    @Test fun addWidgetButtonIsRealAndHintIsHonest() {
        showEditor()
        val t = touch("edit-add-widget")
        assertTrue("热区高 ${t.height / dp}", t.height / dp >= 47.5f)
        assertEquals("按钮高 44（稿面 .btn.sm）", 44f, bounds("edit-add-widget").height.value, 0.6f)
        compose.onNodeWithTag("edit-add-widget", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("addWidget"), calls)
        compose.onNodeWithTag("edit-hint", useUnmergedTree = true).assertTextContains("不会向设备发送任何写入", substring = true)
    }

    @Test fun tappingACardOpensItsConfig() {
        showEditor()
        compose.onNodeWithTag("edit-card-w-temp", useUnmergedTree = true).performClick()
        assertEquals(listOf("open:w-temp"), calls)
    }

    @Test fun everyControlHasAtLeast48dpTouchArea() {
        showEditor()
        val tags = listOf("edit-close", "edit-done", "edit-columns") +
            PanelFixtures.widgets.flatMap { listOf("edit-delete-${it.id}", "edit-handle-${it.id}") }
        for (tag in tags) {
            val t = touch(tag)
            assertTrue("$tag 宽 ${t.width / dp}", t.width / dp >= 47.5f)
            assertTrue("$tag 高 ${t.height / dp}", t.height / dp >= 47.5f)
        }
        // 同一张卡的 × 和角标不重叠
        for (w in PanelFixtures.widgets) {
            val del = touch("edit-delete-${w.id}")
            val handle = touch("edit-handle-${w.id}")
            assertTrue("${w.id} × 与角标重叠", del.bottom <= handle.top + 0.5f)
        }
    }

    @Test fun deleteAndHandleSitAtCardCorners() {
        showEditor()
        val card = touch("edit-card-w-temp")
        val del = touch("edit-delete-w-temp")
        val handle = touch("edit-handle-w-temp")
        assertEquals(card.right, del.right, 1f)
        assertEquals(card.top, del.top, 1f)
        assertEquals(card.right, handle.right, 1f)
        assertEquals(card.bottom, handle.bottom, 1f)
    }

    // ---- 操作 ----

    @Test fun deleteButtonDeletesThatWidget() {
        showEditor()
        compose.onNodeWithTag("edit-delete-w-press", useUnmergedTree = true).performClick()
        assertEquals(listOf("delete:w-press"), calls)
    }

    @Test fun tappingHandleCyclesSize() {
        showEditor()
        compose.onNodeWithTag("edit-handle-w-temp", useUnmergedTree = true).performClick()
        assertEquals(listOf("span:w-temp:2x1"), calls)
    }

    @Test fun draggingHandleResizes() {
        showEditor()
        val card = touch("edit-card-w-temp")
        // 1×1 卡向右拖出约一整列宽 → 2 列
        compose.onNodeWithTag("edit-handle-w-temp", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(card.width * 0.9f, 0f), durationMillis = 300)
        }
        assertEquals(listOf("span:w-temp:2x1"), calls)
        calls.clear()
        // 向下拖出约一行高 → 2 行
        compose.onNodeWithTag("edit-handle-w-press", useUnmergedTree = true).performTouchInput {
            swipe(center, center + Offset(0f, card.height * 1.0f), durationMillis = 300)
        }
        assertEquals(listOf("span:w-press:1x2"), calls)
    }

    @Test fun longPressDragOntoAnotherCardMoves() {
        showEditor()
        val from = touch("edit-card-w-temp")
        val to = touch("edit-card-w-press")
        val start = Offset(from.left + from.width * 0.3f, from.top + from.height * 0.5f)
        val end = Offset(to.left + to.width * 0.4f, to.top + to.height * 0.5f)
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().performTouchInput {
            moveTo(start + Offset(5f, 0f))
            moveTo(end)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("move:w-temp>w-press"), calls)
    }

    @Test fun plainSwipeOnCardDoesNotMoveIt() {
        showEditor()
        val from = touch("edit-card-w-temp")
        val to = touch("edit-card-w-press")
        compose.onRoot().performTouchInput {
            swipe(Offset(from.left + 40f, from.top + 60f), Offset(to.left + 40f, to.top + 60f), durationMillis = 200)
        }
        assertTrue(calls.toString(), calls.none { it.startsWith("move") })
    }

    @Test fun columnsTileCyclesTwoThreeFour() {
        showEditor()
        compose.onNodeWithTag("edit-columns", useUnmergedTree = true).performClick()
        assertEquals(listOf("columns:3"), calls)
        calls.clear()
        val four = PanelFixtures.project().copy(panels = listOf(Panel("p1", "机房 A", columns = 4, widgets = PanelFixtures.widgets)))
        compose.activityRule.scenario.recreate()
        showEditor(editState(four))
        compose.onNodeWithTag("edit-columns", useUnmergedTree = true).performClick()
        assertEquals(listOf("columns:2"), calls)
    }

    @Test fun threeColumnPanelEditsInThreeColumns() {
        val three = PanelFixtures.project().copy(panels = listOf(Panel("p1", "机房 A", columns = 3, widgets = PanelFixtures.widgets)))
        showEditor(editState(three))
        val a = bounds("edit-card-w-temp")
        val b = bounds("edit-card-w-press")
        val c = bounds("edit-card-w-comp")
        assertEquals(a.width.value, b.width.value, 0.6f)
        assertEquals(a.width.value, c.width.value, 0.6f)
        assertEquals(8f, (c.left - b.right).value, 0.6f)
        assertEquals(a.top.value, c.top.value, 0.6f)
    }

    @Test fun blankNameShowsErrorAndDoneStillCallsBack() {
        val blank = PanelFixtures.project().copy(panels = listOf(Panel("p1", "", widgets = PanelFixtures.widgets)))
        showEditor(editState(blank, error = EditError.BLANK_NAME))
        compose.onAllNodesWithText("面板名称不能为空", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
        assertTrue(count("edit-error") == 1)
    }

    @Test fun saveFailureAndReadOnlyAreExplained() {
        showEditor(editState(error = EditError.SAVE_FAILED))
        compose.onNodeWithText("保存失败，修改还在，请重试", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun renameFieldReportsTypedText() {
        showEditor()
        compose.onNode(hasSetTextAction() and hasTestTag("edit-name") or hasSetTextAction(), useUnmergedTree = true)
            .performTextReplacement("冷站")
        assertEquals(listOf("rename:冷站"), calls)
    }

    @Test fun doneCallsBack() {
        showEditor()
        compose.onNodeWithTag("edit-done", useUnmergedTree = true).performClick()
        assertEquals(listOf("done"), calls)
    }

    // ---- 退出 / 确认 ----

    @Test fun closeWithoutChangesLeavesImmediately() {
        showEditor(editState(dirty = false))
        compose.onNodeWithTag("edit-close", useUnmergedTree = true).performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test fun closeWithChangesAsksFirst() {
        showEditor(editState(dirty = true))
        compose.onNodeWithTag("edit-close", useUnmergedTree = true).performClick()
        assertTrue("先确认再退出", calls.isEmpty())
        compose.onNodeWithText("放弃修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        assertTrue(calls.isEmpty())
        compose.onNodeWithTag("edit-close", useUnmergedTree = true).performClick()
        compose.onNodeWithText("放弃").performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test fun deletePanelOnlyWithMultiplePanelsAndConfirms() {
        showEditor()
        assertEquals("只有一个面板时不显示", 0, count("edit-delete-panel"))
        compose.activityRule.scenario.recreate()
        showEditor(editState(PanelFixtures.project(Panel("p2", "冷站"))))
        val t = touch("edit-delete-panel")
        assertTrue("热区高 ${t.height / dp}", t.height / dp >= 47.5f)
        compose.onNodeWithTag("edit-delete-panel", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("删除面板“机房 A”？").assertIsDisplayed()
        compose.onNodeWithText("面板里的 6 个组件会一起删除", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("edit-dialog-confirm").performClick()
        assertEquals(listOf("deletePanel"), calls)
    }

    @Test fun emptyPanelSaysSoAndOffersTheRealAddButton() {
        val empty = Project("default", "x", listOf(Panel("p1", "机房 A")))
        showEditor(editState(empty))
        compose.onNodeWithTag("edit-empty", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, count("edit-grid"))
        compose.onNodeWithTag("edit-add-widget", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 多面板 chip ----

    private fun manyPanels(n: Int = 9) = Project(
        "default", "x",
        listOf(Panel("p1", "机房 A", widgets = PanelFixtures.widgets)) + (2..n).map { Panel("p$it", "长一点的面板$it") },
    )

    @Test fun editChipsSelectAndPlusAddsAPanel() {
        showEditor(editState(PanelFixtures.project(Panel("p2", "冷站"))))
        compose.onNodeWithText("冷站", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("新建面板", useUnmergedTree = true).performClick()
        assertEquals(listOf("select:p2", "addPanel"), calls)
    }

    @Test fun manyPanelsChipRowScrollsInEditMode() {
        showEditor(editState(manyPanels()))
        compose.onNodeWithText("长一点的面板9", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("select:p9"), calls)
    }

    // ---- 横屏 / 字体 ----

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeEditsInFourColumnsAndKeepsRailInset() {
        showEditor(landscape = true)
        val a = bounds("edit-card-w-temp")
        val d = bounds("edit-card-w-mode")
        assertEquals("rail 占 88（页边距含在里面，与面板页一致）", 88f, a.left.value, 0.6f)
        assertEquals(a.top.value, d.top.value, 0.6f)
        assertEquals(a.width.value, d.width.value, 0.6f)
        assertEquals(8f, (bounds("edit-card-w-press").left - a.right).value, 0.6f)
    }

    @Test fun fontScale13EditCardsKeepControlsAndTextInside() {
        showEditor(fontScale = 1.3f)
        for (w in PanelFixtures.widgets) {
            val card = bounds("edit-card-${w.id}")
            assertTrue("${w.id} 高 ${card.height.value}", card.height.value >= 95.5f)
            val value = bounds("panel-value-${w.id}")
            assertTrue("${w.id} 值超出卡片底部", value.bottom.value <= card.bottom.value - 4f)
            assertTrue("${w.id} 值超出卡片右侧", value.right.value <= card.right.value)
            val del = touch("edit-delete-${w.id}")
            val handle = touch("edit-handle-${w.id}")
            assertTrue(del.bottom <= handle.top + 0.5f)
        }
        compose.onNodeWithText("完成", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("面板名称", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 页面入口（PanelContent）----

    private fun showContent(readOnly: Boolean = false, onEdit: (() -> Unit)?, panels: List<Panel> = PanelFixtures.project().panels) {
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalDockInsets provides DockInsets(bottom = 76.dp),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(
                            PanelUiState.Ready(Project("default", "x", panels), emptyList(), readOnly),
                            PanelFixtures.snapshots, onEdit = onEdit,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun pencilOpensEditAndIsTouchSized() {
        var opened = 0
        showContent(onEdit = { opened++ })
        val t = touch("panel-edit")
        assertTrue(t.width / dp >= 47.5f && t.height / dp >= 47.5f)
        compose.onNodeWithTag("panel-edit", useUnmergedTree = true).performClick()
        assertEquals(1, opened)
    }

    @Test fun pencilHiddenWhenReadOnlyOrNoPanelsOrNoRepository() {
        showContent(readOnly = true, onEdit = {})
        assertEquals("只读工程不显示 ✎", 0, count("panel-edit"))
    }

    @Test fun pencilHiddenWithoutPanels() {
        showContent(onEdit = {}, panels = emptyList())
        assertEquals(0, count("panel-edit"))
    }

    @Test fun pencilHiddenWithoutRepositoryCallback() {
        showContent(onEdit = null)
        assertEquals(0, count("panel-edit"))
    }

    @Test fun pageChipsScrollWithManyPanelsAndSelect() {
        val selected = mutableListOf<String>()
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER), LocalOpenConnection provides {},
                LocalDockInsets provides DockInsets(bottom = 76.dp),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(
                            PanelUiState.Ready(manyPanels(), emptyList(), false), PanelFixtures.snapshots,
                            onSelectPanel = { selected += it },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("长一点的面板9", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("p9"), selected)
        // 滚动后第一个 chip 被挤出视野，但不换行、不撑高：整排高度仍是一个 chip（≥48 触控）
        val row = touch("panel-chips")
        assertTrue("chip 行高 ${row.height / dp}", row.height / dp < 60f)
    }

    @Test fun fontScale13PageCardsKeepLayout() {
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER), LocalOpenConnection provides {},
                LocalDockInsets provides DockInsets(bottom = 76.dp),
                LocalDensity provides Density(base.density, 1.3f),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(PanelUiState.Ready(PanelFixtures.project(), emptyList(), false), PanelFixtures.snapshots)
                    }
                }
            }
        }
        compose.waitForIdle()
        val a = bounds("panel-card-w-temp")
        val b = bounds("panel-card-w-press")
        assertEquals("同行等宽", a.width.value, b.width.value, 0.6f)
        assertEquals("同行等高", a.height.value, b.height.value, 0.6f)
        assertEquals("列间距 8", 8f, (b.left - a.right).value, 0.6f)
        for (id in listOf("w-temp", "w-press", "w-comp", "w-mode", "w-valve", "w-cur")) {
            val card = bounds("panel-card-$id")
            val value = bounds("panel-value-$id")
            assertTrue("$id 值超出卡片底部 ${value.bottom.value} > ${card.bottom.value}", value.bottom.value <= card.bottom.value)
            assertTrue("$id 值超出卡片右侧", value.right.value <= card.right.value)
        }
        // 有 meta 的卡：meta 在值下面，且仍在卡内
        val metas = compose.onAllNodesWithTag("panel-meta", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(metas.isNotEmpty())
        val card = compose.onNodeWithTag("panel-card-w-temp", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val temp = compose.onNodeWithTag("panel-value-w-temp", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val meta = metas.map { it.boundsInRoot }.filter { it.top >= temp.bottom - 1f && it.left >= card.left - 1f && it.right <= card.right + 1f }
        assertTrue("温度卡 meta 在值下方并且在卡内", meta.any { it.bottom <= card.bottom + 0.5f })
        assertFalse("meta 不与值重叠", meta.any { it.top < temp.bottom - 1f })
    }

    // ---- 渲染图 ----

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_edit_dark_portrait() { showEditor(); shot("panel_edit_dark_portrait.png") }

    @Test fun render_edit_light_portrait() { showEditor(theme = ThemeMode.LIGHT); shot("panel_edit_light_portrait.png") }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun render_edit_dark_landscape() { showEditor(landscape = true); shot("panel_edit_dark_landscape.png") }

    @Test fun render_edit_multi_dark_portrait() {
        val project = PanelFixtures.project(Panel("p2", "冷站"), Panel("p3", "测试"))
        showEditor(editState(project, dirty = true))
        shot("panel_edit_multi_dark_portrait.png")
    }

    @Test fun render_edit_wide_card_dark_portrait() {
        val w = PanelFixtures.widgets.mapIndexed { i, x -> if (i == 0) x.copy(spanColumns = 2) else if (i == 4) x.copy(spanRows = 2) else x }
        val project = Project("default", "x", listOf(Panel("p1", "机房 A", widgets = w)))
        showEditor(editState(project))
        shot("panel_edit_resized_dark_portrait.png")
    }

    @Test fun render_edit_fontscale_dark_portrait() { showEditor(fontScale = 1.3f); shot("panel_edit_font13_dark_portrait.png") }

    @Test fun render_page_fontscale_dark_portrait() {
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER), LocalOpenConnection provides {},
                LocalDockInsets provides DockInsets(bottom = 76.dp),
                LocalDensity provides Density(base.density, 1.3f),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(PanelUiState.Ready(PanelFixtures.project(), emptyList(), false), PanelFixtures.snapshots, onEdit = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        shot("panel_widgets_font13_dark_portrait.png")
    }
}
