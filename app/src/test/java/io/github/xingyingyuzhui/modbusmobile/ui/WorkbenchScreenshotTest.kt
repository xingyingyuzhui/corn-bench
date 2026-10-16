package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
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

    private fun show(theme: ThemeMode, session: SessionManager) {
        val settings = UiSettings(theme = theme, addressStyle = AddressStyle.MODICON, hexPrefix = true)
        val repository = MemorySettingsRepository(settings)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides repository,
            ) {
                ModbusTheme(settings) {
                    ModbusApp(startRoute = Destination.Workbench.route)
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
