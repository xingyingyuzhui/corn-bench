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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 轮询结果进样本历史：成功记值、失败记标记，只记登记过的点位，PanelRuntime.apply 同时登记 / 轮询。 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurveHistoryRecordingTest {
    private class Session : SessionManager {
        override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1))
        override val traffic = MutableSharedFlow<TrafficEvent>()
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        var handler: (ModbusRequest) -> ModbusResult = { ModbusResult.Timeout }
        val requests = mutableListOf<ModbusRequest>()
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult { requests += request; return handler(request) }
    }

    private fun regs(vararg v: Int) = ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, v.toList()))

    private fun pt(id: String, address: Int, scale: Double = 1.0) =
        PointDefinition(id, id, 1, RegisterArea.HOLDING_REGISTER, address, DataType.U16, conversion = Conversion(scale), pollMs = 1000)

    private fun curve(vararg points: PointDefinition) = Widget(
        "c1", WidgetKinds.CURVE, spanColumns = 2,
        curve = CurveConfig(points.map { CurveSeries(it) }, retentionMinutes = 10, maxPoints = 500),
    )

    private class Rig(val scope: TestScope) {
        val session = Session()
        val store = PointStore()
        val history = SampleHistory()
        val scheduler = PollScheduler(
            session, scope.backgroundScope, store, nowMs = { scope.currentTime }, wallMs = { 1_000_000L + scope.currentTime }, history = history,
        )
        val runtime = PanelRuntime(store, scheduler, history)
    }

    @Test fun successfulReadsAreRecordedAsEngineeringValuesWithWallTime() = runTest {
        val r = Rig(this)
        r.session.handler = { regs(234) }
        val t = pt("t", 100, 0.1)
        r.runtime.apply(Project("default", "x", listOf(Panel("p", "P", 2, listOf(curve(t))))))
        r.scheduler.setActive(true)
        runCurrent()
        advanceTimeBy(2_500)
        val s = r.history.read("t")
        assertTrue("至少读了 3 次：${s.size}", s.size >= 3)
        assertEquals(23.4, s.values[0], 1e-9)
        assertEquals(1_000_000L, s.times[0])
        assertTrue(s.times.toList() == s.times.sorted())
    }

    @Test fun pointsOfOrdinaryWidgetsAreNotRecorded() = runTest {
        val r = Rig(this)
        r.session.handler = { regs(1, 2) }
        val number = Widget("n", WidgetKinds.NUMBER, point = pt("n", 0))
        val c = curve(pt("c", 1))
        r.runtime.apply(Project("default", "x", listOf(Panel("p", "P", 2, listOf(number, c)))))
        r.scheduler.setActive(true)
        runCurrent()
        advanceTimeBy(1_500)
        assertEquals("普通组件的点位不进历史", 0, r.history.count("n"))
        assertTrue(r.history.count("c") >= 1)
        assertTrue("普通点位照常轮询", r.store.get("n")?.raw != null)
    }

    @Test fun timeoutsRecordOneFailureMarkerPerRunThenGoodSamplesResume() = runTest {
        val r = Rig(this)
        var ok = true
        r.session.handler = { if (ok) regs(5) else ModbusResult.Timeout }
        r.runtime.apply(Project("default", "x", listOf(Panel("p", "P", 2, listOf(curve(pt("t", 0)))))))
        r.scheduler.setActive(true)
        runCurrent()
        advanceTimeBy(1_500)
        ok = false
        advanceTimeBy(4_000)
        ok = true
        advanceTimeBy(2_000)
        val s = r.history.read("t")
        val failures = (0 until s.size).filter { s.isFailure(it) }
        assertEquals("连续超时只记一个标记", 1, failures.size)
        assertTrue("失败之后有恢复的有效样本", failures.single() < s.size - 1)
        assertTrue(!s.isFailure(0) && !s.isFailure(s.size - 1))
    }

    @Test fun applyUntracksPointsWhoseCurveWasRemoved() = runTest {
        val r = Rig(this)
        r.session.handler = { regs(7) }
        val withCurve = Project("default", "x", listOf(Panel("p", "P", 2, listOf(curve(pt("t", 0))))))
        r.runtime.apply(withCurve)
        r.scheduler.setActive(true)
        runCurrent()
        advanceTimeBy(1_200)
        assertTrue(r.history.count("t") >= 1)
        r.runtime.apply(Project("default", "x", listOf(Panel("p", "P", 2))))
        runCurrent()
        assertEquals(0, r.history.count("t"))
        assertTrue(!r.history.isTracked("t"))
    }

    @Test fun editingAnUnrelatedWidgetKeepsTheCurveHistory() = runTest {
        val r = Rig(this)
        r.session.handler = { regs(7) }
        val c = curve(pt("t", 0))
        r.runtime.apply(Project("default", "x", listOf(Panel("p", "P", 2, listOf(c)))))
        r.scheduler.setActive(true)
        runCurrent()
        advanceTimeBy(2_200)
        val before = r.history.count("t")
        assertTrue(before >= 2)
        r.runtime.apply(Project("default", "x", listOf(Panel("p", "P", 2, listOf(c, Widget("n", WidgetKinds.NUMBER, point = pt("n", 5)))))))
        assertEquals("曲线没变，历史不清", before, r.history.count("t"))
    }

    @Test fun historyTargetsUseTheCurvesOwnRetention() {
        val c = curve(pt("a", 0), pt("b", 1))
        val targets = PanelRuntime.historyTargets(Project("default", "x", listOf(Panel("p", "P", 2, listOf(c)))))
        assertEquals(setOf("a", "b"), targets.keys)
        assertEquals(10 * 60_000L, targets.getValue("a").retentionMs)
        assertEquals(500, targets.getValue("b").maxPoints)
    }
}
