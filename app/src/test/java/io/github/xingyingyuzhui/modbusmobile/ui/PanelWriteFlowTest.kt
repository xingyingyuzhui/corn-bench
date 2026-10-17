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
 * 面板写入（P8b1）端到端：真 ViewModel + 真工程文件 + 真轮询 + 真确认弹窗（ModalBottomSheet）。
 * 点开关 / 按钮先弹确认、取消不发、确认才发；广播红色；位写入先读后写；编辑模式 / 从站不能写。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelWriteFlowTest {
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
        Widget("sw1", WidgetKinds.SWITCH, point = point("p-sw1", "压缩机", 1, RegisterArea.COIL, 0, DataType.BOOL), onLabel = "运行", offLabel = "停止"),
        Widget("sw2", WidgetKinds.SWITCH, point = point("p-sw2", "运行位", 1, RegisterArea.HOLDING_REGISTER, 140, DataType.BIT, bit = 3)),
        Widget("bt1", WidgetKinds.BUTTON, point = point("p-bt1", "启动压缩机", 1, RegisterArea.COIL, 5, DataType.BOOL), buttonLabel = "启动", writeValue = 1.0),
        Widget("bt2", WidgetKinds.BUTTON, point = point("p-bt2", "全部停机", 0, RegisterArea.COIL, 9, DataType.BOOL), buttonLabel = "全停", writeValue = 0.0),
        Widget("bt3", WidgetKinds.BUTTON, point = point("p-bt3", "设定值", 1, RegisterArea.HOLDING_REGISTER, 20, DataType.U16), buttonLabel = "设为 500", writeValue = 500.0),
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

    /** 等到开关读到真实状态（轮询回来）：线圈 C0 = 关 → “停止”。 */
    private fun waitSwitchesLive() = compose.waitUntil(8_000) { count("panel-value-sw1") > 0 && text("panel-value-sw1") == "停止" }

    @Test fun coilSwitchAsksFirstCancelSendsNothingConfirmSends() = withScope { scope ->
        val session = Session(Role.MASTER)
        show(session, true, scope)
        waitSwitchesLive()
        tap("panel-switch-sw1")
        waitTag("confirm-title")
        compose.onNodeWithTag("confirm-context", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("开关 · 压缩机", text("confirm-context"))
        assertTrue("弹窗开着，还没有任何写请求", session.writes.isEmpty())
        tapText("取消")
        waitGone("confirm-title")
        assertTrue("取消不发", session.writes.isEmpty())

        tap("panel-switch-sw1")
        waitTag("confirm-title")
        tapText("发送写入")
        compose.waitUntil(5_000) { session.writes.isNotEmpty() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleCoil(1, 0, true)), session.writes)
        waitTag("panel-write-notice")
        assertTrue(compose.onAllNodesWithText("已写入", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun broadcastButtonShowsRedBroadcastConfirmAndOnlyAfterConfirmGoesOut() = withScope { scope ->
        val session = Session(Role.MASTER)
        show(session, true, scope)
        waitTag("panel-button-bt2")
        assertTrue("卡上就写明是广播", text("panel-button-note-bt2").contains("广播"))
        tap("panel-button-bt2")
        waitTag("confirm-title")
        compose.onNodeWithText("广播 · 所有从站", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("发送广播", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(session.writes.isEmpty())
        tapText("取消")
        waitGone("confirm-title")
        assertTrue(session.writes.isEmpty())
        tap("panel-button-bt2")
        waitTag("confirm-title")
        tapText("发送广播")
        compose.waitUntil(5_000) { session.writes.isNotEmpty() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleCoil(0, 9, false)), session.writes)
    }

    @Test fun registerBitSwitchReadsFirstWarnsAndWritesTheWholeWordBack() = withScope { scope ->
        val session = Session(Role.MASTER)
        show(session, true, scope)
        // 0x0029 的 bit3 = 1 → 开；点它 = 关
        compose.waitUntil(8_000) { count("panel-value-sw2") > 0 && text("panel-value-sw2") == "开" }
        tap("panel-switch-sw2")
        waitTag("confirm-title")
        assertTrue("位写入先读后写提示", compose.onAllNodesWithText("先读整字", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(text("confirm-context").contains("位 3 → 0"))
        assertTrue("弹窗开着只有读，没有写", session.writes.isEmpty())
        tapText("发送写入")
        compose.waitUntil(5_000) { session.writes.isNotEmpty() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleRegister(1, 140, 0x0021)), session.writes)
    }

    @Test fun editModeCardsDoNothingAndNothingIsWritten() = withScope { scope ->
        val session = Session(Role.MASTER)
        show(session, true, scope)
        waitSwitchesLive()
        tap("panel-edit")
        waitTag("edit-grid")
        // 编辑模式里点卡片 = 打开这个组件的向导（改配置），不是操作它
        tap("edit-card-bt2")
        waitTag("wiz-preview")
        assertEquals("编辑 / 向导里没有写确认弹窗", 0, count("confirm-title"))
        // 向导预览里的按钮是置灰的，点了也没有任何反应
        tap("panel-button-bt2")
        assertEquals(0, count("confirm-title"))
        assertTrue("编辑和向导期间一个写请求也没有", session.writes.isEmpty())
    }

    @Test fun slaveModeDisablesEverythingWithAReason() = withScope { scope ->
        val session = Session(Role.SLAVE)
        show(session, false, scope)
        waitTag("panel-button-bt1")
        assertTrue(compose.onAllNodesWithText("从站模式不能写设备", useUnmergedTree = true).fetchSemanticsNodes().size >= 3)
        tap("panel-button-bt1")
        tap("panel-switch-sw1")
        assertEquals(0, count("confirm-title"))
        assertTrue(session.requests.isEmpty())
    }
}
