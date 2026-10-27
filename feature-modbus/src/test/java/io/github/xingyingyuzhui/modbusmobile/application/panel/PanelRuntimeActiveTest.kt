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
import org.junit.Assert.assertTrue
import org.junit.Test

/** 面板页和曲线全屏页各是一个“前台所有者”：任何一个在前台就轮询，全部离开才停（页面切换时先进后出也不误停）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelRuntimeActiveTest {
    private class Session : SessionManager {
        override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1))
        override val traffic = MutableSharedFlow<TrafficEvent>()
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        var count = 0
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            count++
            return ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(1)))
        }
    }

    private fun kotlinx.coroutines.test.TestScope.runtime(session: Session): PanelRuntime {
        val store = PointStore()
        val history = SampleHistory()
        val scheduler = PollScheduler(session, backgroundScope, store, nowMs = { currentTime }, wallMs = { 1_000_000L + currentTime }, history = history)
        return PanelRuntime(store, scheduler, history).also {
            it.apply(Project("p", "p", listOf(Panel("a", "a", widgets = listOf(Widget("w", WidgetKinds.GAUGE, point = PointDefinition("x", "x", 1, RegisterArea.HOLDING_REGISTER, 0, DataType.U16, pollMs = 1000)))))))
        }
    }

    @Test fun pollsWhileAnyOwnerIsInTheForegroundAndStopsWhenAllLeave() = runTest {
        val s = Session()
        val rt = runtime(s)
        advanceTimeBy(3_000); runCurrent()
        assertEquals("没人在前台不轮询", 0, s.count)
        rt.setActive("panel", true)
        advanceTimeBy(2_500); runCurrent()
        val running = s.count
        assertTrue("面板在前台就轮询 $running", running >= 2)
        // 进曲线全屏：新页面先进、旧页面后出，中间不能停
        rt.setActive("curve", true)
        rt.setActive("panel", false)
        advanceTimeBy(3_000); runCurrent()
        assertTrue("曲线页在前台继续轮询 ${s.count}", s.count >= running + 2)
        rt.setActive("curve", false)
        advanceTimeBy(500); runCurrent()
        val stopped = s.count
        advanceTimeBy(5_000); runCurrent()
        assertEquals("全部离开后停止", stopped, s.count)
    }

    @Test fun leavingAnOwnerTwiceOrNeverEnteredDoesNotBreakTheCount() = runTest {
        val s = Session()
        val rt = runtime(s)
        rt.setActive("curve", false)
        rt.setActive("panel", true)
        rt.setActive("panel", true)
        rt.setActive("panel", false)
        advanceTimeBy(3_000); runCurrent()
        assertEquals("同一所有者重复进入只算一次，一次离开就停", 0, s.count)
    }
}
