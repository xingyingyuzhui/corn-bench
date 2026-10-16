package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.AddressStyle
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 主站工作台的截图：读取结果（含变化行高亮）、写入模式、写入确认。
 * 用的是内存里的假会话，读到的数字是脚本给的，不是设备数据。
 * 设置里把“地址显示”设成参考号、“HEX 0x 前缀”打开，对应稿面 b1-2 的状态。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class WorkbenchScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun workbench_read_result_dark_portrait() = readResult(ThemeMode.DARK, count = 5)
    @Test fun workbench_read_result_light_portrait() = readResult(ThemeMode.LIGHT, count = 5)

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun workbench_read_result_dark_landscape() = readResult(ThemeMode.DARK, count = 7)

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun workbench_read_result_light_landscape() = readResult(ThemeMode.LIGHT, count = 7)

    @Test fun workbench_write_dark_portrait() = writeMode(ThemeMode.DARK)
    @Test fun workbench_write_light_portrait() = writeMode(ThemeMode.LIGHT)

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun workbench_write_dark_landscape() = writeMode(ThemeMode.DARK)

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun workbench_write_light_landscape() = writeMode(ThemeMode.LIGHT)

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeWriteKeepsTheMainButtonOnScreenAndDropsTheResultTable() {
        val session = ShotSession()
        session.reads += List(5) { it * 10 }
        show(ThemeMode.DARK, session)
        compose.onAllNodes(hasSetTextAction() and hasText("10"))[0].performTextReplacement("5")
        compose.onNodeWithText("读取 · FC03").performClick()
        compose.waitForIdle()
        enterWriteValues()
        compose.onNodeWithText("预览并写入 · FC16").assertIsDisplayed()
        compose.onNodeWithText("最近写入").assertIsDisplayed()
        // 右栏不再放读取结果表：表头“地址”和上一次读到的数值都不在。
        compose.onAllNodesWithText("HEX").assertCountEquals(0)
    }

    @Test
    fun customClickablesHaveA48dpTouchTarget() {
        val session = ShotSession()
        session.reads += List(5) { it * 10 }
        show(ThemeMode.DARK, session)
        compose.onAllNodes(hasSetTextAction() and hasText("10"))[0].performTextReplacement("5")
        compose.onNodeWithText("读取 · FC03").performClick()
        compose.waitForIdle()
        // 分段控件（视觉 31 / 36）、xs chip（视觉 25）、图标钮（视觉 36）、文字链接：触控高度都不小于 48dp。
        compose.onNodeWithText("读取").assertTouchHeightIsEqualTo(48.dp)
        compose.onNodeWithText("保持").assertTouchHeightIsEqualTo(48.dp)
        compose.onNodeWithText("S16").assertTouchHeightIsEqualTo(48.dp)
        compose.onNodeWithContentDescription("更多").assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp)
        compose.onNodeWithText("读取 · FC03").assertTouchHeightIsEqualTo(48.dp)
        compose.onNodeWithText("写入").performClick()
        compose.onNodeWithText("＋ 添加").assertTouchHeightIsEqualTo(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("清空").assertTouchHeightIsEqualTo(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun numberFieldSelectsEverythingWhenFocused() {
        show(ThemeMode.DARK, ShotSession())
        val quantity = compose.onAllNodes(hasSetTextAction())[2]
        quantity.performClick()
        compose.waitForIdle()
        val range = compose.onAllNodes(hasSetTextAction())[2].fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        assertEquals(androidx.compose.ui.text.TextRange(0, 2), range)
    }

    @Test
    fun workbench_menu_dark_portrait() = menu(ThemeMode.DARK)

    @Test
    fun workbench_menu_light_portrait() = menu(ThemeMode.LIGHT)

    @Test
    fun broadcastWriteHasItsOwnConfirmation() {
        val session = ShotSession()
        show(ThemeMode.DARK, session)
        compose.onNodeWithText("写入").performClick()
        replaceField(0, "0")
        compose.onNodeWithText("预览并写入 · FC06").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("广播 · 所有从站").assertExists()
        compose.onNodeWithText("所有从站都会执行，且不会有回应，结果只能标为已发送").assertExists()
        compose.onNodeWithText("发送写入").assertDoesNotExist()
        assertTrue(session.requests.none { it.isWrite })
        captureScreenRoboImage("src/test/screenshots/io.github.xingyingyuzhui.modbusmobile.ui.WorkbenchScreenshotTest.workbench_confirm_broadcast_dark_portrait.png")
        compose.onNodeWithText("发送广播").performClick()
        compose.waitForIdle()
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleRegister(0, 0, 0)), session.requests.filter { it.isWrite })
        compose.onNodeWithText("已发送").assertExists()
        compose.onNodeWithText("成功").assertDoesNotExist()
    }

    @Test
    fun theWriteFormAndRecentWritesSurviveLeavingAndComingBack() {
        val session = ShotSession()
        show(ThemeMode.DARK, session, startRoute = Destination.Connection.route)
        compose.onNodeWithText("工作台").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("写入").performClick()
        replaceField(2, "321")
        compose.onNodeWithText("预览并写入 · FC06").performClick()
        compose.onNodeWithText("发送写入").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("成功").assertExists()

        // 去“日志”页，再按系统返回键（回到起始页“连接”），然后重新进入工作台。
        compose.onAllNodesWithText("日志")[0].performClick()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("工作台").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("预览并写入 · FC06").assertExists()
        compose.onAllNodes(hasSetTextAction() and hasText("321")).assertCountEquals(1)
        compose.onNodeWithText("成功").assertExists()
    }

    @Test
    fun backFromTheWritePageGoesToTheReadPageFirst() {
        show(ThemeMode.DARK, ShotSession(), startRoute = Destination.Connection.route)
        compose.onNodeWithText("工作台").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("写入").performClick()
        compose.onNodeWithText("预览并写入 · FC06").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("读取 · FC03").assertExists()
    }

    @Test
    fun workbench_confirm_registers_dark_portrait() {
        val session = ShotSession()
        show(ThemeMode.DARK, session)
        enterWriteValues()
        compose.onNodeWithText("预览并写入 · FC16").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("发送写入").assertExists()
        assertTrue("弹窗出现时不能已经发出写请求", session.requests.none { it.isWrite })
        captureScreenRoboImage("src/test/screenshots/io.github.xingyingyuzhui.modbusmobile.ui.WorkbenchScreenshotTest.workbench_confirm_registers_dark_portrait.png")
    }

    @Test
    fun workbench_confirm_coil_light_portrait() {
        val session = ShotSession()
        show(ThemeMode.LIGHT, session)
        compose.onNodeWithText("线圈").performClick()
        replaceField(1, "2")
        compose.onNodeWithText("读取 · FC01").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("写入").performClick()
        compose.onNodeWithText("线圈").performClick()
        compose.onNodeWithText("OFF").performClick()
        compose.onNodeWithText("预览并写入 · FC05").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("发送写入").assertExists()
        assertTrue(session.requests.none { it.isWrite })
        captureScreenRoboImage("src/test/screenshots/io.github.xingyingyuzhui.modbusmobile.ui.WorkbenchScreenshotTest.workbench_confirm_coil_light_portrait.png")
    }

    @Test
    fun confirmingTheSheetSendsExactlyOneWrite() {
        val session = ShotSession()
        show(ThemeMode.DARK, session)
        compose.onNodeWithText("写入").performClick()
        compose.onNodeWithText("预览并写入 · FC06").performClick()
        compose.waitForIdle()
        assertTrue(session.requests.none { it.isWrite })
        compose.onNodeWithText("发送写入").performClick()
        compose.waitForIdle()
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleRegister(1, 0, 0)), session.requests.filter { it.isWrite })
        compose.onNodeWithText("成功").assertExists()
    }

    private fun readResult(theme: ThemeMode, count: Int) {
        val session = ShotSession()
        session.reads += List(count) { it * 10 }
        session.reads += List(count) { if (it == 4) 123 else it * 10 }
        show(theme, session)
        compose.onAllNodes(hasSetTextAction() and hasText("10"))[0].performTextReplacement(count.toString())
        compose.onNodeWithText("读取 · FC03").performClick()
        compose.waitForIdle()
        // 第二次读取：第 5 个寄存器变成 123，这一行按稿面高亮。时钟停住，截图时高亮还在。
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("读取 · FC03").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(50)
        compose.onRoot().captureRoboImage()
    }

    private fun menu(theme: ThemeMode) {
        show(theme, ShotSession())
        compose.onNodeWithContentDescription("更多").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("清空结果").assertExists()
        captureScreenRoboImage("src/test/screenshots/io.github.xingyingyuzhui.modbusmobile.ui.WorkbenchScreenshotTest.workbench_menu_${if (theme == ThemeMode.DARK) "dark" else "light"}_portrait.png")
    }

    private fun writeMode(theme: ThemeMode) {
        val session = ShotSession()
        show(theme, session)
        enterWriteValues()
        compose.onRoot().captureRoboImage()
    }

    private fun enterWriteValues() {
        compose.onNodeWithText("写入").performClick()
        compose.onNodeWithText("＋ 添加").performClick()
        compose.onNodeWithText("＋ 添加").performClick()
        replaceField(1, "100")
        replaceField(2, "100")
        replaceField(3, "300")
        replaceField(4, "500")
        compose.waitForIdle()
    }

    private fun replaceField(index: Int, text: String) {
        compose.onAllNodes(hasSetTextAction())[index].performTextReplacement(text)
    }

    private fun show(theme: ThemeMode, session: SessionManager, startRoute: String = Destination.Workbench.route) {
        // 其它设置全是默认值：地址“两者”、HEX 带 0x，对应稿面 b1-2 的状态。
        val settings = UiSettings(theme = theme)
        val repository = MemorySettingsRepository(settings)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides repository,
            ) {
                ModbusTheme(settings) {
                    ModbusApp(startRoute = startRoute)
                }
            }
        }
        compose.waitForIdle()
    }
}

/** 主站运行中的假会话：读请求按脚本返回，写请求回显成功；记录所有发出的请求。 */
private class ShotSession : SessionManager {
    val requests = mutableListOf<ModbusRequest>()
    val reads = ArrayDeque<List<Int>>()
    private var lastRead: List<Int> = emptyList()

    override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1))
    override val traffic = MutableSharedFlow<TrafficEvent>(replay = 1)
    override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(SerialSettings())

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit

    override suspend fun disconnect() = Unit

    override suspend fun execute(request: ModbusRequest): ModbusResult {
        requests += request
        return when (request) {
            is ModbusRequest.ReadHoldingRegisters -> {
                lastRead = reads.removeFirstOrNull() ?: lastRead
                ModbusResult.Success(ModbusResponse.ReadRegisters(request.unitId, FunctionCode.READ_HOLDING_REGISTERS, lastRead))
            }
            is ModbusRequest.ReadCoils ->
                ModbusResult.Success(ModbusResponse.ReadBits(request.unitId, FunctionCode.READ_COILS, List(request.quantity) { false }))
            is ModbusRequest.WriteSingleRegister ->
                ModbusResult.Success(ModbusResponse.WriteSingleRegister(request.unitId, request.function, request.address, request.value))
            is ModbusRequest.WriteSingleCoil ->
                ModbusResult.Success(ModbusResponse.WriteSingleCoil(request.unitId, request.function, request.address, request.value))
            is ModbusRequest.WriteMultipleRegisters ->
                ModbusResult.Success(ModbusResponse.WriteMultipleRegisters(request.unitId, request.function, request.address, request.quantity))
            else -> ModbusResult.Disconnected
        }
    }
}
