package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PollSchedulerTest {
    private class FakeSession(private val now: () -> Long) : SessionManager {
        override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1))
        override val traffic = MutableSharedFlow<TrafficEvent>()
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        val log = mutableListOf<Pair<Long, ModbusRequest>>()
        var latencyMs = 0L
        var handler: (ModbusRequest) -> ModbusResult = { ModbusResult.Timeout }
        var inFlight = 0
        var maxInFlight = 0
        private val engine = Mutex() // 与 MasterEngine 一样串行
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult = engine.withLock {
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            log += now() to request
            if (latencyMs > 0) delay(latencyMs)
            inFlight--
            handler(request)
        }
    }

    private fun regs(vararg v: Int) = ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, v.toList()))

    private fun pt(
        id: String,
        address: Int,
        type: DataType = DataType.U16,
        conversion: Conversion = Conversion(),
        thresholds: List<ThresholdBand> = emptyList(),
        pollMs: Int = 1000,
        area: RegisterArea = RegisterArea.HOLDING_REGISTER,
    ) = PointDefinition(id, id, 1, area, address, type, conversion = conversion, thresholds = thresholds, pollMs = pollMs)

    private class Rig(scope: TestScope) {
        val session = FakeSession { scope.currentTime }
        val store = PointStore()
        val scheduler = PollScheduler(session, scope.backgroundScope, store, nowMs = { scope.currentTime }, wallMs = { 1_000_000L + scope.currentTime })
    }

    private fun TestScope.rig() = Rig(this)

    private fun Rig.start(vararg points: PointDefinition) {
        scheduler.setPoints(points.toList())
        scheduler.setActive(true)
    }

    @Test fun nothingIsSentUntilActive() = runTest {
        val r = rig()
        r.scheduler.setPoints(listOf(pt("a", 0)))
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(r.session.log.isEmpty())
        assertEquals(Quality.NO_DATA, r.store.get("a")?.quality)
    }

    @Test fun pollsOnPeriodAndStoresDecodedValue() = runTest {
        val r = rig()
        r.session.handler = { regs(1000) }
        r.start(pt("t", 100, DataType.S16, Conversion(0.1, -20.0, 1), listOf(ThresholdBand(ThresholdLevel.ALERT, min = 80.0))))
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(listOf(0L, 1000L, 2000L), r.session.log.map { it.first })
        val s = r.store.get("t")!!
        assertEquals(Quality.OK, s.quality)
        assertEquals("80.0", s.text)
        assertEquals(80.0, s.engineering!!, 1e-9)
        assertEquals(ThresholdLevel.ALERT, s.level)
        assertEquals(2000L, s.updatedAtMs)
    }

    @Test fun adjacentPointsAreReadInOneRequestAndSplitCorrectly() = runTest {
        val r = rig()
        r.session.handler = { regs(0x3FC0, 0x0000, 7) }
        r.start(pt("f", 10, DataType.F32), pt("u", 12))
        runCurrent()
        assertEquals(1, r.session.log.size)
        assertEquals(ModbusRequest.ReadHoldingRegisters(1, 10, 3), r.session.log.single().second)
        assertEquals("1.5", r.store.get("f")!!.text)
        assertEquals("7", r.store.get("u")!!.text)
    }

    @Test fun coilsDecodeAsFlags() = runTest {
        val r = rig()
        r.session.handler = { ModbusResult.Success(ModbusResponse.ReadBits(1, FunctionCode.READ_COILS, listOf(true, false))) }
        r.start(pt("c0", 0, DataType.BOOL, area = RegisterArea.COIL), pt("c1", 1, DataType.BOOL, area = RegisterArea.COIL))
        runCurrent()
        assertEquals(1.0, r.store.get("c0")!!.engineering!!, 0.0)
        assertEquals(0.0, r.store.get("c1")!!.engineering!!, 0.0)
        assertNull(r.store.get("c0")!!.text)
        assertEquals(RawValue.Flag(true), r.store.get("c0")!!.raw)
    }

    @Test fun differentPeriodsPollIndependently() = runTest {
        val r = rig()
        r.session.handler = { regs(1) }
        r.start(pt("fast", 0, pollMs = 500), pt("slow", 5, pollMs = 2000))
        advanceTimeBy(2_100)
        runCurrent()
        val fast = r.session.log.count { (it.second as ModbusRequest.ReadHoldingRegisters).address == 0 }
        val slow = r.session.log.count { (it.second as ModbusRequest.ReadHoldingRegisters).address == 5 }
        assertEquals(5, fast) // 0, 500, 1000, 1500, 2000
        assertEquals(2, slow) // 0, 2000
        assertEquals(1, r.session.maxInFlight)
    }

    @Test fun timeoutsKeepLastValueAsStaleThenBecomeTimeoutAndRecover() = runTest {
        val r = rig()
        r.session.handler = { regs(5) }
        r.start(pt("a", 0))
        runCurrent()
        assertEquals(Quality.OK, r.store.get("a")!!.quality)
        r.session.handler = { ModbusResult.Timeout }
        advanceTimeBy(1_000); runCurrent() // 1 次失败
        assertEquals(Quality.STALE, r.store.get("a")!!.quality)
        assertEquals("5", r.store.get("a")!!.text)
        advanceTimeBy(1_000); runCurrent() // 2 次
        assertEquals(Quality.STALE, r.store.get("a")!!.quality)
        advanceTimeBy(1_000); runCurrent() // 3 次
        val s = r.store.get("a")!!
        assertEquals(Quality.TIMEOUT, s.quality)
        assertEquals(3, s.failures)
        assertEquals("5", s.text)
        r.session.handler = { regs(9) }
        advanceTimeBy(1_000); runCurrent()
        val back = r.store.get("a")!!
        assertEquals(Quality.OK, back.quality)
        assertEquals(0, back.failures)
        assertNull(back.error)
        assertEquals("9", back.text)
    }

    @Test fun timeoutsBeforeAnyValueStayNoData() = runTest {
        val r = rig()
        r.start(pt("a", 0))
        advanceTimeBy(1_500); runCurrent()
        assertEquals(Quality.NO_DATA, r.store.get("a")!!.quality)
        assertEquals(ErrorKind.TIMEOUT, r.store.get("a")!!.error!!.kind)
    }

    @Test fun exceptionMarksErrorAndBacksOff() = runTest {
        val r = rig()
        r.session.handler = { ModbusResult.Exception(ExceptionCode.ILLEGAL_DATA_ADDRESS) }
        r.start(pt("a", 0))
        advanceTimeBy(3_900); runCurrent()
        assertEquals(listOf(0L, 2000L), r.session.log.map { it.first })
        val s = r.store.get("a")!!
        assertEquals(Quality.ERROR, s.quality)
        assertEquals(ErrorKind.EXCEPTION, s.error!!.kind)
        assertEquals(2, s.error!!.code)
    }

    @Test fun invalidResponseAndWrongLengthAreErrors() = runTest {
        val r = rig()
        r.session.handler = { ModbusResult.InvalidResponse("crc") }
        r.start(pt("a", 0))
        runCurrent()
        assertEquals(ErrorKind.INVALID_RESPONSE, r.store.get("a")!!.error!!.kind)
        r.scheduler.setPoints(listOf(pt("b", 0), pt("c", 1)))
        r.session.handler = { regs(1) } // 应有 2 个
        advanceTimeBy(100); runCurrent()
        assertEquals(Quality.ERROR, r.store.get("b")!!.quality)
        assertEquals("响应长度与请求不符", r.store.get("b")!!.error!!.detail)
    }

    @Test fun badValuesAreErrorsNotNumbers() = runTest {
        val r = rig()
        r.session.handler = { regs(0x12AB, 0x7FC0, 0x0000) }
        r.start(pt("bcd", 0, DataType.BCD16), pt("nan", 1, DataType.F32))
        runCurrent()
        assertEquals(ErrorKind.BAD_VALUE, r.store.get("bcd")!!.error!!.kind)
        assertEquals(Quality.ERROR, r.store.get("nan")!!.quality)
        assertNull(r.store.get("nan")!!.text)
    }

    @Test fun slowReadsDoNotCatchUpAndStatsShowActualInterval() = runTest {
        val r = rig()
        r.session.latencyMs = 2_500
        r.session.handler = { regs(1) }
        r.start(pt("a", 0))
        advanceTimeBy(5_100); runCurrent()
        assertEquals(listOf(0L, 2500L, 5000L), r.session.log.map { it.first })
        assertEquals(PollStats(1000, 2500), r.scheduler.stats.value)
    }

    @Test fun statsStartWithoutActualThenMeasure() = runTest {
        val r = rig()
        r.session.handler = { regs(1) }
        assertNull(r.scheduler.stats.value)
        r.start(pt("a", 0))
        runCurrent()
        assertEquals(PollStats(1000, null), r.scheduler.stats.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(PollStats(1000, 1000), r.scheduler.stats.value)
        r.scheduler.setActive(false)
        runCurrent()
        assertNull(r.scheduler.stats.value)
    }

    @Test fun deactivatingStopsRequestsButKeepsValuesAndResumes() = runTest {
        val r = rig()
        r.session.handler = { regs(3) }
        r.start(pt("a", 0))
        runCurrent()
        r.scheduler.setActive(false)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, r.session.log.size)
        assertEquals("3", r.store.get("a")!!.text)
        r.scheduler.setActive(true)
        runCurrent()
        assertEquals(2, r.session.log.size)
    }

    @Test fun samePointsDoNotResetStoreButChangedPointsDo() = runTest {
        val r = rig()
        r.session.handler = { regs(3) }
        r.start(pt("a", 0))
        runCurrent()
        r.scheduler.setPoints(listOf(pt("a", 0)))
        assertEquals("3", r.store.get("a")!!.text)
        r.session.handler = { ModbusResult.Timeout }
        r.scheduler.setPoints(listOf(pt("a", 0), pt("b", 4)))
        runCurrent()
        assertEquals(Quality.NO_DATA, r.store.get("a")!!.quality)
        assertNull(r.store.get("a")!!.raw)
    }

    @Test fun newConnectionClearsOldValues() = runTest {
        val r = rig()
        r.session.handler = { regs(3) }
        r.start(pt("a", 0))
        runCurrent()
        r.session.handler = { ModbusResult.Timeout }
        r.session.state.value = SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 2)
        runCurrent()
        val s = r.store.get("a")!!
        assertNull("上一次连接的值不能带到新连接", s.raw)
        assertEquals(Quality.NO_DATA, s.quality)
    }

    @Test fun disconnectedOrSlaveSessionSendsNothing() = runTest {
        val r = rig()
        r.session.handler = { regs(3) }
        r.start(pt("a", 0))
        runCurrent()
        val before = r.session.log.size
        r.session.state.value = SessionState.Idle
        runCurrent()
        assertNull(r.store.get("a")!!.raw)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(before, r.session.log.size)
        r.session.state.value = SessionState.Running(Role.SLAVE, ConnectionTarget.Demo, 3)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(before, r.session.log.size)
        r.session.state.value = SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 4)
        runCurrent()
        assertEquals(before + 1, r.session.log.size)
    }

    @Test fun disconnectedResultRetriesSlowly() = runTest {
        val r = rig()
        r.session.handler = { ModbusResult.Disconnected }
        r.start(pt("a", 0))
        advanceTimeBy(3_500); runCurrent()
        assertTrue(r.session.log.size <= 4)
        assertEquals(ErrorKind.DISCONNECTED, r.store.get("a")!!.error!!.kind)
    }

    @Test fun manualRequestsInterleaveBetweenPollsWithoutBeingStarved() = runTest {
        val r = rig()
        r.session.latencyMs = 300
        r.session.handler = { regs(1) }
        r.start(pt("a", 0))
        runCurrent() // 轮询读在 0 开始，300 结束
        var manualStart = -1L
        backgroundScope.launch {
            delay(100)
            r.session.execute(ModbusRequest.ReadHoldingRegisters(1, 50, 1))
        }
        advanceTimeBy(1_200); runCurrent()
        manualStart = r.session.log.first { (it.second as ModbusRequest.ReadHoldingRegisters).address == 50 }.first
        assertEquals("手动请求最多等当前这一次读", 300L, manualStart)
        assertEquals(1, r.session.maxInFlight)
        assertEquals(listOf(0L, 1000L), r.session.log.filter { (it.second as ModbusRequest.ReadHoldingRegisters).address == 0 }.map { it.first })
    }

    @Test fun snapshotsCarryWallClockOfTheRead() = runTest {
        val r = rig()
        r.session.handler = { regs(1) }
        r.start(pt("a", 0))
        advanceTimeBy(1_100); runCurrent()
        val s = r.store.get("a")!!
        assertEquals(1_000L, s.updatedAtMs)
        assertEquals(1_001_000L, s.updatedWallMs)
    }

    @Test fun userPauseStopsRequestsKeepsValuesAndSurvivesInactiveToggle() = runTest {
        val r = rig()
        r.session.handler = { regs(3) }
        r.start(pt("a", 0))
        runCurrent()
        assertEquals(1, r.session.log.size)
        r.scheduler.setPaused(true)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, r.session.log.size)
        assertEquals("3", r.store.get("a")!!.text)
        assertNull(r.scheduler.stats.value)
        assertTrue(r.scheduler.paused.value)
        // 离开页面再回来：仍是暂停
        r.scheduler.setActive(false); runCurrent()
        r.scheduler.setActive(true)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, r.session.log.size)
        r.scheduler.setPaused(false)
        runCurrent()
        assertEquals(2, r.session.log.size)
    }
}
