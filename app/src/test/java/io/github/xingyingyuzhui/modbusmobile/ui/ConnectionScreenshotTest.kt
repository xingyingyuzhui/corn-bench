package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** PR-2（连接页）的 Roborazzi 截图：带 USB 设备的空闲态、展开“高级”、连接态（断开条）、横屏、帮助。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class ConnectionScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun connection_usb_dark_portrait() = shot(ThemeMode.DARK, idleWithUsb())

    @Test fun connection_usb_light_portrait() = shot(ThemeMode.LIGHT, idleWithUsb())

    @Test
    fun connection_advanced_dark_portrait() {
        show(ThemeMode.DARK, idleWithUsb())
        compose.onNodeWithText("高级", substring = true).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun connection_custom_light_portrait() {
        show(ThemeMode.LIGHT, idleWithUsb())
        compose.onNodeWithContentDescription("自定义").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
    }

    @Test fun connection_running_light_portrait() = shot(ThemeMode.LIGHT, ConnSession(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1)))

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun connection_usb_dark_landscape() = shot(ThemeMode.DARK, idleWithUsb())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun connection_running_light_landscape() = shot(ThemeMode.LIGHT, ConnSession(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1)))

    @Test
    fun helpButtonOpensTheExplanation() {
        show(ThemeMode.DARK, idleWithUsb())
        compose.onNodeWithContentDescription("帮助").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("连接说明").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("连接说明").assertDoesNotExist()
    }

    @Test
    fun presetRowOffersThePresetsAndTheEllipsis() {
        show(ThemeMode.DARK, idleWithUsb())
        compose.onNodeWithText("9600 8N1").assertIsDisplayed()
        compose.onNodeWithText("19200 8E1").assertIsDisplayed()
        compose.onNodeWithText("115200 8N1").assertIsDisplayed()
        compose.onNodeWithText("…").assertIsDisplayed()
    }

    private fun idleWithUsb() = ConnSession(SessionState.Idle).also {
        it.usbDevices.value = listOf(UsbDeviceSummary(1, 0, "FT232R USB UART", 0x0403, 0x6001))
    }

    private fun shot(theme: ThemeMode, session: SessionManager) {
        show(theme, session)
        compose.onRoot().captureRoboImage()
    }

    private fun show(theme: ThemeMode, session: SessionManager) {
        val settings = UiSettings(theme = theme)
        val repository = MemorySettingsRepository(settings)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides repository,
            ) {
                ModbusTheme(settings) { ModbusApp(startRoute = Destination.Connection.route) }
            }
        }
        compose.waitForIdle()
    }
}

private class ConnSession(initial: SessionState) : SessionManager {
    override val state = MutableStateFlow(initial)
    override val traffic = MutableSharedFlow<TrafficEvent>(replay = 1)
    override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(null)

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
}
