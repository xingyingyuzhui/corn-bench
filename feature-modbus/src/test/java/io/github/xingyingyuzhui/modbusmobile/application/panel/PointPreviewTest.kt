package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PointPreviewTest {
    private class Session : SessionManager {
        override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1))
        override val traffic = MutableSharedFlow<TrafficEvent>()
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        val requests = mutableListOf<ModbusRequest>()
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            requests += request
            return ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(234)))
        }
    }

    private val point = PointDefinition("preview", "温度", 1, RegisterArea.HOLDING_REGISTER, 100, DataType.S16, conversion = Conversion(0.1, 0.0, 1), pollMs = 100)

    @Test fun readsTheDraftPointWithItsOwnStoreAndOnlyReads() = runTest {
        val session = Session()
        val preview = PointPreview(session, backgroundScope) { currentTime }
        preview.setPoint(point)
        preview.setActive(true)
        runCurrent()
        advanceTimeBy(350)
        val snapshot = preview.snapshots.value["preview"]
        assertEquals("23.4", snapshot?.text)
        assertEquals(Quality.OK, snapshot?.quality)
        assertTrue(session.requests.size >= 3)
        assertTrue(session.requests.none { it.isWrite })
    }

    @Test fun inactiveOrClosedPreviewSendsNothing() = runTest {
        val session = Session()
        val preview = PointPreview(session, backgroundScope) { currentTime }
        preview.setPoint(point)
        runCurrent()
        advanceTimeBy(500)
        assertTrue("没激活不发请求", session.requests.isEmpty())
        preview.setActive(true)
        runCurrent()
        advanceTimeBy(250)
        val sent = session.requests.size
        assertTrue(sent > 0)
        preview.close()
        runCurrent()
        advanceTimeBy(1_000)
        assertEquals("关闭后不再发请求", sent, session.requests.size)
        assertNull(preview.snapshots.value["preview"]?.raw)
    }

    @Test fun changingThePointRestartsWithNewAddress() = runTest {
        val session = Session()
        val preview = PointPreview(session, backgroundScope) { currentTime }
        preview.setActive(true)
        preview.setPoint(point)
        runCurrent()
        advanceTimeBy(150)
        preview.setPoint(point.copy(address = 200))
        runCurrent()
        advanceTimeBy(150)
        val addresses = session.requests.map { (it as ModbusRequest.ReadHoldingRegisters).address }
        assertTrue(addresses.contains(100))
        assertEquals(200, addresses.last())
    }
}
