package io.github.xingyingyuzhui.modbusmobile.usb

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import io.github.xingyingyuzhui.modbusmobile.application.UsbStack
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.SystemMonotonicClock
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSettingsRepository
import io.github.xingyingyuzhui.modbusmobile.ui.ModbusApp
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class LocalSerialLabConnectionScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun closeScope() { scope.cancel() }

    @Test fun connection_selects_factory_lab_device_and_connects_as_master() {
        // Use the same source-set factory as AppContainer. The session records the UI request
        // rather than opening the debug socket, so this test cannot accidentally bind a listener.
        val stack = withLocalSerialLab(NoPhysicalDevices(), scope, SystemMonotonicClock)
        assertTrue(stack is LocalSerialLabStack)
        val device = stack.devices.value.single()
        assertEquals(LOCAL_LAB_DEVICE_ID, device.deviceId)
        val session = RecordingSession(stack)
        val settings = UiSettings(theme = ThemeMode.DARK)
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
        compose.onNodeWithText(device.productName).assertIsDisplayed().performClick()
        compose.onNodeWithText(device.productName).assertIsSelected()
        compose.onNodeWithText("连接为主站 · 9600 8N1").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf(ConnectionTarget.Usb(LOCAL_LAB_DEVICE_ID, 0) to Role.MASTER), session.connections)
            assertEquals(9_600, session.lastSerial?.baudRate)
        }
        compose.onRoot().captureRoboImage()
        stack.stop()
    }

    private class RecordingSession(stack: UsbStack) : SessionManager {
        override val state = MutableStateFlow<SessionState>(SessionState.Idle)
        override val traffic = MutableSharedFlow<TrafficEvent>()
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = stack.devices
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        val connections = mutableListOf<Pair<ConnectionTarget, Role>>()
        var lastSerial: SerialSettings? = null
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) {
            connections += target to role
            lastSerial = serial
        }
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
    }

    private class NoPhysicalDevices : UsbStack {
        override val devices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val detached = MutableSharedFlow<Int>()
        override suspend fun requestPermission(deviceId: Int): Boolean = error("Unexpected physical permission request")
        override suspend fun open(target: ConnectionTarget.Usb, serial: SerialSettings): ByteTransport =
            error("UI test must not open a transport")
        override fun stop() = Unit
        override fun setIoErrorHandler(handler: (String) -> Unit) = Unit
    }
}
