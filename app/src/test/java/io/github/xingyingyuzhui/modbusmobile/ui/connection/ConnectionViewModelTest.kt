package io.github.xingyingyuzhui.modbusmobile.ui.connection

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.EchoMode
import io.github.xingyingyuzhui.modbusmobile.application.Parity
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    @Test
    fun invalidTimeoutDisablesConnectAndPresetEditsStayCustom() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = RecordingSession()
            val model = ConnectionViewModel(session)
            assertTrue(model.validation.ok)
            assertEquals(SerialPresetUi.N9600, model.preset)

            model.timeoutText = "0"
            assertFalse(model.validation.ok)
            model.connect()
            advanceUntilIdle()
            assertNull(session.connected)

            model.timeoutText = "1000"
            model.gapText = "0"
            assertFalse(model.validation.ok)

            model.gapText = "20"
            model.selectPreset(SerialPresetUi.N115200)
            assertEquals(115200, model.baud)
            assertEquals(SerialPresetUi.N115200, model.preset)

            model.parity = Parity.EVEN
            model.selectCustom()
            assertNull(model.preset)
            model.connect()
            advanceUntilIdle()
            val serial = session.connected
            assertEquals(115200, serial?.baudRate)
            assertEquals(Parity.EVEN, serial?.parity)
            assertEquals(20L, serial?.interRequestDelayMs)
            assertEquals(EchoMode.OFF, serial?.echoMode)
            assertEquals(Role.MASTER, session.role)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun refreshAsksTheSessionToRescan() {
        val session = RecordingSession()
        val model = ConnectionViewModel(session)
        assertEquals(0, session.refreshes)
        model.refresh()
        assertEquals(1, session.refreshes)
    }

    @Test
    fun presetNineteenTwoHundredIsEvenParity() {
        val model = ConnectionViewModel(RecordingSession())
        model.selectPreset(SerialPresetUi.E19200)
        assertEquals(19_200, model.baud)
        assertEquals(Parity.EVEN, model.parity)
        assertEquals(1, model.stopBits)
        assertEquals("19200 8E1", model.wireLabel)
        assertEquals(SerialPresetUi.E19200, model.preset)
        // 19200 8N1 不再是预设，手动改成它就是自定义
        model.parity = Parity.NONE
        assertNull(model.preset)
    }
}

private class RecordingSession : SessionManager {
    var refreshes = 0
    var connected: SerialSettings? = null
    var role: Role? = null
    override val state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val traffic = MutableSharedFlow<TrafficEvent>()
    override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(null)

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) {
        connected = serial
        this.role = role
    }

    override suspend fun disconnect() = Unit

    override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected

    override fun refreshDevices() {
        refreshes += 1
    }

}
