package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.application.connectAction
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.shell.CurveRoute
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class ShellScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun connectActionMatchesResource() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val text = context.getString(
            io.github.xingyingyuzhui.modbusmobile.R.string.connect_action,
            "主站",
            "9600 8N1",
        )
        assertEquals(connectAction("主站", "9600 8N1"), text)
        val detail = context.getString(
            io.github.xingyingyuzhui.modbusmobile.R.string.advanced_detail,
            1000,
            20,
            context.getString(io.github.xingyingyuzhui.modbusmobile.R.string.echo_off),
        )
        assertEquals("超时 1000 · 间隔 20 · 回显 关", detail)
        assertEquals(
            "高级 · 超时 1000 · 间隔 20 · 回显 关",
            context.getString(io.github.xingyingyuzhui.modbusmobile.R.string.advanced_summary, detail),
        )
        assertEquals(
            "请输入 1 到 120000 的整数",
            context.getString(io.github.xingyingyuzhui.modbusmobile.R.string.error_timeout_range),
        )
        assertEquals(
            "请输入正整数",
            context.getString(io.github.xingyingyuzhui.modbusmobile.R.string.error_gap_positive),
        )
    }

    @Test fun connection_dark_portrait() = page(ThemeMode.DARK, Destination.Connection.route, idle())
    @Test fun connection_light_portrait() = page(ThemeMode.LIGHT, Destination.Connection.route, idle())
    @Test fun workbench_dark_portrait() = page(ThemeMode.DARK, Destination.Workbench.route, running())
    @Test fun workbench_light_portrait() = page(ThemeMode.LIGHT, Destination.Workbench.route, running())
    @Test fun panel_dark_portrait() = page(ThemeMode.DARK, Destination.Panel.route, running())
    @Test fun panel_light_portrait() = page(ThemeMode.LIGHT, Destination.Panel.route, running())
    @Test fun log_dark_portrait() = page(ThemeMode.DARK, Destination.Log.route, running())
    @Test fun log_light_portrait() = page(ThemeMode.LIGHT, Destination.Log.route, running())
    @Test fun mine_dark_portrait() = page(ThemeMode.DARK, Destination.Mine.route, running())
    @Test fun mine_light_portrait() = page(ThemeMode.LIGHT, Destination.Mine.route, running())
    @Test fun curve_dark_portrait() = page(ThemeMode.DARK, CurveRoute, running())
    @Test fun curve_light_portrait() = page(ThemeMode.LIGHT, CurveRoute, running())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun connection_dark_landscape() = page(ThemeMode.DARK, Destination.Connection.route, idle())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun connection_light_landscape() = page(ThemeMode.LIGHT, Destination.Connection.route, idle())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun workbench_dark_landscape() = page(ThemeMode.DARK, Destination.Workbench.route, running())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun workbench_light_landscape() = page(ThemeMode.LIGHT, Destination.Workbench.route, running())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun panel_dark_landscape() = page(ThemeMode.DARK, Destination.Panel.route, running())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun panel_light_landscape() = page(ThemeMode.LIGHT, Destination.Panel.route, running())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun log_dark_landscape() = page(ThemeMode.DARK, Destination.Log.route, running())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun log_light_landscape() = page(ThemeMode.LIGHT, Destination.Log.route, running())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun mine_dark_landscape() = page(ThemeMode.DARK, Destination.Mine.route, running())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun mine_light_landscape() = page(ThemeMode.LIGHT, Destination.Mine.route, running())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun curve_dark_landscape() = page(ThemeMode.DARK, CurveRoute, running())

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun curve_light_landscape() = page(ThemeMode.LIGHT, CurveRoute, running())

    private fun page(theme: ThemeMode, route: String, session: SessionManager) {
        val settings = UiSettings(theme = theme)
        val repository = MemorySettingsRepository(settings)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides repository,
            ) {
                ModbusTheme(settings) {
                    ModbusApp(startRoute = route)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
    }
}

private fun idle() = ScreenshotSession(SessionState.Idle, serialSettings = null, events = emptyList())

private fun running() = ScreenshotSession(
    SessionState.Running(Role.MASTER, ConnectionTarget.Demo, epoch = 1),
    serialSettings = SerialSettings(),
    events = listOf(
        TrafficEvent(
            monotonicNs = 0L,
            direction = TrafficDirection.TX,
            bytes = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x02, 0xC4.toByte(), 0x0B),
            kind = TrafficKind.TX_REQUEST,
        ),
        TrafficEvent(
            monotonicNs = 38_000_000L,
            direction = TrafficDirection.RX,
            bytes = byteArrayOf(0x01, 0x03, 0x04, 0x00, 0x0A, 0x00, 0x14, 0xDA.toByte(), 0x3E),
            kind = TrafficKind.RX_RESPONSE,
        ),
    ),
)

private class ScreenshotSession(
    state: SessionState,
    serialSettings: SerialSettings?,
    events: List<TrafficEvent>,
) : SessionManager {
    override val state = MutableStateFlow(state)
    override val traffic = MutableSharedFlow<TrafficEvent>(replay = 8)
    override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow(serialSettings)

    init {
        events.forEach { traffic.tryEmit(it) }
    }

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
}
