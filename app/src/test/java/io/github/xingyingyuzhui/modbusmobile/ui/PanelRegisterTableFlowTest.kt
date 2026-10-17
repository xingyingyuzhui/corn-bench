package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PanelRuntime
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointStore
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollScheduler
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 寄存器表（P8b2）端到端：真 ViewModel + 真工程文件 + 真轮询 + 真输入浮层 + 真确认弹窗（ModalBottomSheet）。
 * 点 ✎ 先输入、下一步先看写入帧、取消不发、确认才发；线圈行直接确认；编辑模式 / 向导预览 / 从站不能写；只读表没有 ✎。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelRegisterTableFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private class Session(role: Role) : PanelFakeSession(role) {
        val requests = CopyOnWriteArrayList<ModbusRequest>()
        val writes get() = requests.filter { it.isWrite }
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            requests += request
            return when (request) {
                is ModbusRequest.ReadCoils -> ModbusResult.Success(ModbusResponse.ReadBits(request.unitId, FunctionCode.READ_COILS, List(request.quantity) { false }))
                is ModbusRequest.ReadHoldingRegisters ->
                    ModbusResult.Success(ModbusResponse.ReadRegisters(request.unitId, FunctionCode.READ_HOLDING_REGISTERS, List(request.quantity) { 0x0029 }))
                is ModbusRequest.WriteSingleCoil ->
                    ModbusResult.Success(ModbusResponse.WriteSingleCoil(request.unitId, request.function, request.address, request.value))
                is ModbusRequest.WriteSingleRegister ->
                    ModbusResult.Success(ModbusResponse.WriteSingleRegister(request.unitId, request.function, request.address, request.value))
                else -> ModbusResult.Disconnected
            }
        }
    }

    private val dir get() = File(tmp.root, "projects")

    private fun point(id: String, name: String, unit: Int, area: RegisterArea, address: Int, type: DataType, bit: Int = 0) =
        PointDefinition(id, name, unit, area, address, type, bit = bit, pollMs = 200)

    private val widgets = listOf(
        Widget(
            "t1", WidgetKinds.REGISTER_TABLE, title = "水泵参数",
            point = PointDefinition("p-t1", "水泵", 1, RegisterArea.HOLDING_REGISTER, 100, DataType.U16, pollMs = 200),
            spanColumns = 2, tableRows = 3, tableWritable = true,
        ),
        Widget(
            "t2", WidgetKinds.REGISTER_TABLE, title = "阀门",
            point = PointDefinition("p-t2", "阀", 1, RegisterArea.COIL, 10, DataType.BOOL, pollMs = 200),
            spanColumns = 2, tableRows = 2, tableWritable = true,
        ),
        Widget(
            "t3", WidgetKinds.REGISTER_TABLE, title = "只读表",
            point = PointDefinition("p-t3", "只读", 1, RegisterArea.INPUT_REGISTER, 0, DataType.U16, pollMs = 200),
            spanColumns = 2, tableRows = 2, tableWritable = false,
        ),
    )

    private fun show(session: Session, withPolling: Boolean, scope: CoroutineScope) {
        val repo = ProjectRepository(dir).also { it.save(Project("default", "x", listOf(Panel("p1", "机房", widgets = widgets)))) }
        val runtime = if (withPolling) {
            val store = PointStore()
            PanelRuntime(store, PollScheduler(session, scope, store, nowMs = { System.nanoTime() / 1_000_000 }))
        } else {
            null
        }
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides MemorySettingsRepository(settings),
                LocalProjectRepository provides repo,
                LocalPanelRuntime provides runtime,
            ) { ModbusTheme(settings) { ModbusApp(startRoute = Destination.Panel.route) } }
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun waitTag(tag: String) = compose.waitUntil(8_000) { count(tag) > 0 }
    private fun waitGone(tag: String) = compose.waitUntil(8_000) { count(tag) == 0 }
    private fun scrollTo(tag: String) = runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }
    private fun tap(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    private fun tapText(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).performClick()
    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
        .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun withScope(block: (CoroutineScope) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            block(scope)
        } finally {
            scope.cancel()
        }
    }


    private fun waitLive() = compose.waitUntil(8_000) { count("panel-row-value-t1-0") > 0 && text("panel-row-value-t1-0") == "41" && text("panel-row-value-t2-0") == "OFF" }

    private fun typeValue(value: String) {
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("row-entry-value")), useUnmergedTree = true).performTextReplacement(value)
    }

    @Test fun registerRowAsksForValueThenFrameCancelSendsNothingConfirmSends() = withScope { scope ->
        val session = Session(Role.MASTER)
        show(session, true, scope)
        waitLive()
        tap("panel-row-edit-t1-1")
        waitTag("row-entry")
        assertEquals("修改 · 水泵参数 第 2 行", text("row-entry-title"))
        assertTrue("输入框开着没有写请求", session.writes.isEmpty())
        typeValue("300")
        tap("row-entry-next")
        waitTag("confirm-title")
        assertEquals("确认弹窗开着，输入浮层已收起", 0, count("row-entry"))
        assertEquals("寄存器表 · 水泵参数 第 2 行 · HR101 → 300", text("confirm-context"))
        assertTrue("弹窗开着，还没有任何写请求", session.writes.isEmpty())
        tapText("取消")
        waitGone("confirm-title")
        assertTrue("取消不发", session.writes.isEmpty())

        tap("panel-row-edit-t1-1")
        waitTag("row-entry")
        typeValue("300")
        tap("row-entry-next")
        waitTag("confirm-title")
        tapText("发送写入")
        compose.waitUntil(5_000) { session.writes.isNotEmpty() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleRegister(1, 101, 300)), session.writes)
        waitTag("panel-write-notice")
        assertTrue(compose.onAllNodesWithText("已写入", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun coilRowGoesStraightToTheConfirmAndFlipsTheState() = withScope { scope ->
        val session = Session(Role.MASTER)
        show(session, true, scope)
        waitLive()
        scrollTo("panel-row-edit-t2-1")
        tap("panel-row-edit-t2-1")
        waitTag("confirm-title")
        assertEquals(0, count("row-entry"))
        assertEquals("寄存器表 · 阀门 第 2 行 · C11 → ON", text("confirm-context"))
        assertTrue(session.writes.isEmpty())
        tapText("取消")
        waitGone("confirm-title")
        assertTrue(session.writes.isEmpty())
        tap("panel-row-edit-t2-1")
        waitTag("confirm-title")
        tapText("发送写入")
        compose.waitUntil(5_000) { session.writes.isNotEmpty() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleCoil(1, 11, true)), session.writes)
    }

    @Test fun cancelAndScrimOfTheEntryBoxSendNothing() = withScope { scope ->
        val session = Session(Role.MASTER)
        show(session, true, scope)
        waitLive()
        tap("panel-row-edit-t1-0")
        waitTag("row-entry")
        tap("row-entry-cancel")
        waitGone("row-entry")
        tap("panel-row-edit-t1-0")
        waitTag("row-entry")
        tap("row-entry-scrim")
        waitGone("row-entry")
        assertEquals(0, count("confirm-title"))
        assertTrue(session.writes.isEmpty())
    }

    @Test fun readOnlyTableHasNoPencils() = withScope { scope ->
        val session = Session(Role.MASTER)
        show(session, true, scope)
        waitLive()
        scrollTo("panel-row-t3-0")
        assertEquals(0, count("panel-row-edit-t3-0"))
        assertEquals("只读表没有 HEX 之外的操作", 1, count("panel-row-hex-t3-0"))
    }

    @Test fun editModeShowsASummaryWithoutPencilsAndWritesNothing() = withScope { scope ->
        val session = Session(Role.MASTER)
        show(session, true, scope)
        waitLive()
        tap("panel-edit")
        waitTag("edit-grid")
        assertEquals("编辑页里没有行也没有 ✎", 0, count("panel-row-edit-t1-0"))
        assertEquals("3 行 · HR100–HR102 · 允许修改", text("panel-table-summary-t1"))
        // 点卡片 = 打开向导（改配置），向导预览里 ✎ 画出来但点不动
        tap("edit-card-t1")
        waitTag("wiz-preview")
        waitTag("panel-row-edit-t1-0")
        tap("panel-row-edit-t1-0")
        assertEquals(0, count("row-entry"))
        assertEquals(0, count("confirm-title"))
        assertTrue("编辑和向导期间一个写请求也没有", session.writes.isEmpty())
    }

    @Test fun slaveModeDisablesPencilsWithAReason() = withScope { scope ->
        val session = Session(Role.SLAVE)
        show(session, false, scope)
        waitTag("panel-row-edit-t1-0")
        assertEquals("从站模式不能写设备", text("panel-table-note-t1"))
        tap("panel-row-edit-t1-0")
        tap("panel-row-edit-t1-2")
        assertEquals(0, count("row-entry"))
        assertEquals(0, count("confirm-title"))
        assertTrue(session.requests.isEmpty())
    }
}
