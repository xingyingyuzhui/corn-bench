@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.xingyingyuzhui.modbusmobile.application.AddressStyle
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.MasterPoller
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
import io.github.xingyingyuzhui.modbusmobile.ui.components.dropLeadingZero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbenchWriteTest {
    @Test
    fun writeNeverGoesOutBeforeTheUserConfirms() = withModel { model, session ->
        model.setWordText(0, "123")
        model.requestWrite()
        advanceUntilIdle()
        assertNotNull(model.pending)
        assertTrue("点“预览并写入”不能直接发送", session.requests.isEmpty())

        model.dismissPending()
        advanceUntilIdle()
        assertNull(model.pending)
        assertTrue("取消后不能发送", session.requests.isEmpty())
        assertTrue(model.recentWrites.isEmpty())

        model.requestWrite()
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleRegister(1, 0, 123)), session.requests)
        assertNull(model.pending)
        assertEquals(WriteOutcome.OK, model.recentWrites.single().outcome)
        assertEquals(0x06, model.recentWrites.single().functionCode)
    }

    @Test
    fun invalidInputShowsAnErrorAndDoesNotOpenTheSheet() = withModel { model, session ->
        model.setWordText(0, "abc")
        model.requestWrite()
        advanceUntilIdle()
        assertNull(model.pending)
        assertNotNull(model.formError)
        assertTrue(session.requests.isEmpty())
    }

    @Test
    fun severalValuesGoOutAsFc16() = withModel { model, session ->
        model.addValue()
        model.addValue()
        model.setWordText(0, "100")
        model.setWordText(1, "300")
        model.setWordText(2, "500")
        model.address = "100"
        model.requestWrite()
        assertEquals(0x10, model.pending!!.plan.functionCode)
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteMultipleRegisters(1, 100, listOf(100, 300, 500))), session.requests)
        assertEquals("FC16", fcLabel(model.recentWrites.single().functionCode))
    }

    @Test
    fun forcingFc16SendsOneValueAsAMultipleWrite() = withModel { model, session ->
        model.toggleForceMultiple()
        model.setWordText(0, "7")
        model.requestWrite()
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteMultipleRegisters(1, 0, listOf(7))), session.requests)
    }

    @Test
    fun coilConfirmationCanFlipTheNewValueAndKnowsTheCurrentOne() = withModel { model, session ->
        // 先读线圈，确认弹窗里的“当前”才有来源。
        session.next += ModbusResult.Success(ModbusResponse.ReadBits(1, FunctionCode.READ_COILS, List(10) { false }))
        model.area = RegisterArea.COIL
        model.address = "2"
        model.read()
        advanceUntilIdle()
        model.selectMode(WorkbenchMode.WRITE)
        model.selectWriteArea(RegisterArea.COIL)
        model.requestWrite()
        val pending = model.pending!!
        assertEquals(listOf<Int?>(0), pending.current)
        assertEquals(0x05, pending.plan.functionCode)

        model.setPendingCoil(true)
        assertEquals(true, model.pending!!.plan.bits.single())
        // 01 05 00 02 FF 00 + CRC：与稿面 b1-4 一致
        assertEquals(0xFF, model.pending!!.plan.frame[4].toInt() and 0xFF)
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(ModbusRequest.WriteSingleCoil(1, 2, true), session.requests.last())
    }

    @Test
    fun unknownCurrentValueIsNotInvented() = withModel { model, _ ->
        model.requestWrite()
        assertEquals(listOf<Int?>(null), model.pending!!.current)
    }

    @Test
    fun failedWritesAreRecordedWithTheirOutcome() = withModel { model, session ->
        session.next += ModbusResult.Timeout
        session.next += ModbusResult.UnknownOutcome
        session.next += ModbusResult.Exception(ExceptionCode.ILLEGAL_DATA_ADDRESS)
        repeat(3) {
            model.requestWrite()
            model.confirmWrite()
            advanceUntilIdle()
        }
        assertEquals(
            listOf(WriteOutcome.EXCEPTION, WriteOutcome.UNKNOWN, WriteOutcome.TIMEOUT),
            model.recentWrites.map { it.outcome },
        )
        assertEquals(2, model.recentWrites.first().exceptionCode)
    }

    @Test
    fun recentWritesKeepTheLastFive() = withModel { model, session ->
        repeat(7) { index ->
            model.setWordText(0, index.toString())
            model.requestWrite()
            model.confirmWrite()
            advanceUntilIdle()
        }
        assertEquals(5, model.recentWrites.size)
        assertEquals(6, model.recentWrites.first().words.single())
        assertEquals(7, session.requests.size)
        model.clearRecentWrites()
        assertTrue(model.recentWrites.isEmpty())
    }

    @Test
    fun clickingARecentWriteRefillsTheFormWithoutSending() = withModel { model, session ->
        model.address = "5"
        model.setWordText(0, "123")
        model.requestWrite()
        model.confirmWrite()
        advanceUntilIdle()
        model.address = "0"
        model.setWordText(0, "0")
        model.reuse(model.recentWrites.single())
        assertEquals("5", model.address)
        assertEquals(listOf("123"), model.writeWords)
        assertEquals(WorkbenchMode.WRITE, model.mode)
        assertEquals(1, session.requests.size)
    }

    @Test
    fun readMarksOnlyTheRegistersThatChanged() = withModel { model, session ->
        var now = 10_000L
        val clocked = WorkbenchViewModel(session, clock = { now })
        session.next += readOf(listOf(1, 2, 3))
        session.next += readOf(listOf(1, 9, 3))
        clocked.read()
        advanceUntilIdle()
        assertTrue((clocked.readState as ReadState.Ok).result.changedAt.isEmpty())
        now = 10_500L
        clocked.read()
        advanceUntilIdle()
        val result = (clocked.readState as ReadState.Ok).result
        assertEquals(mapOf(1 to 10_500L), result.changedAt)
        assertEquals(listOf(1, 9, 3), result.values)
        clocked.onClearedForTest()
    }

    @Test
    fun editingARowOpensTheWriteFormAtThatAddress() = withModel { model, session ->
        session.next += readOf(listOf(0, 10, 20))
        model.address = "40"
        model.read()
        advanceUntilIdle()
        val result = (model.readState as ReadState.Ok).result
        model.editRow(result, 41)
        assertEquals(WorkbenchMode.WRITE, model.mode)
        assertEquals("41", model.address)
        assertEquals(listOf("10"), model.writeWords)
        assertTrue(session.requests.size == 1)
    }

    @Test
    fun readFailuresBecomeAStateNotACrash() = withModel { model, session ->
        session.next += ModbusResult.Timeout
        model.read()
        advanceUntilIdle()
        assertTrue(model.readState is ReadState.Failed)
        model.address = "x"
        model.read()
        advanceUntilIdle()
        assertNotNull(model.formError)
        assertEquals(1, session.requests.size)
    }

    @Test
    fun pollingStopsWhenTheConnectionIsGone() = withModel { model, session ->
        session.next += ModbusResult.Disconnected
        model.changePolling(true)
        advanceUntilIdle()
        assertFalse(model.polling)
        assertEquals(1, session.requests.size)
    }

    @Test
    fun pollingYieldsToTheWriteMode() = withModel { model, session ->
        session.next += readOf(listOf(1))
        model.selectMode(WorkbenchMode.WRITE)
        model.changePolling(true)
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertTrue("写入模式下轮询不发读请求", session.requests.isEmpty())
        model.changePolling(false)
        advanceUntilIdle()
    }

    @Test
    fun broadcastWriteIsMarkedSentAndHasNoCurrentValue() = withModel { model, session ->
        model.unit = "0"
        model.setWordText(0, "7")
        model.requestWrite()
        val pending = model.pending!!
        assertTrue(pending.broadcast)
        assertEquals(listOf<Int?>(null), pending.current)
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleRegister(0, 0, 7)), session.requests)
        val entry = model.recentWrites.single()
        // 引擎对广播返回合成的成功；界面只能说“已发送”。
        assertEquals(WriteOutcome.SENT, entry.outcome)
        // 广播不更新“当前值”缓存。
        model.unit = "0"
        model.requestWrite()
        assertEquals(listOf<Int?>(null), model.pending!!.current)
    }

    @Test
    fun pollingStopsOnlyAfterThreeInvalidResponsesInARow() = withModel { model, session ->
        val bad = ModbusResult.InvalidResponse("noise")
        session.next += listOf(bad, bad, readOf(listOf(1)), bad, bad)
        model.changePolling(true)
        testScheduler.advanceTimeBy(20_000)
        testScheduler.runCurrent()
        // 两次无效 → 一次成功（计数清零）→ 两次无效 → 队列空，后面是 Disconnected 才停：一共 6 次读取。
        assertEquals(6, session.requests.size)
        assertFalse(model.polling)
    }

    @Test
    fun threeInvalidResponsesStopPolling() = withModel { model, session ->
        val bad = ModbusResult.InvalidResponse("noise")
        session.next += listOf(bad, bad, bad)
        model.changePolling(true)
        testScheduler.advanceTimeBy(20_000)
        testScheduler.runCurrent()
        assertEquals(3, session.requests.size)
        assertFalse(model.polling)
    }

    /**
     * T-20261018-qa-02 回归：离开工作台（工作台 VM 被清掉 / 页面不可见）轮询不能停。
     * 修复前（main 05ea1e2）同一场景的测试是 pollingPausesWhileThePageIsHiddenAndResumesAfterwards，断言的是「页面不可见时不读」，
     * 也就是 qa-02 报的静默停止；本测试断言相反的行为，在修复前的实现上不可能通过。
     */
    @Test
    fun pollingKeepsGoingAfterLeavingTheWorkbenchAndTheButtonStaysOn() = withModel { _, session ->
        val poller = MasterPoller(session, backgroundScope) { testScheduler.currentTime }
        session.next += List(80) { readOf(listOf(1)) }
        val first = ViewModelStore()
        val before = ViewModelProvider(first, session.factory(poller))[WorkbenchViewModel::class.java]
        before.address = "7"
        before.changePolling(true)
        testScheduler.runCurrent()
        // 离开工作台：VM 被清掉（最坏情况），只剩应用级 poller。
        first.clear()
        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()
        assertTrue("离开 60 s 仍在发请求：${session.requests.size}", session.requests.size in 60..62)
        assertTrue(poller.state.value.running)

        // 回到工作台：新 VM 从唯一状态源接上，按钮仍是「停止 · 1 s」，表单回填成正在轮询的请求。
        val second = ViewModelStore()
        val after = ViewModelProvider(second, session.factory(poller))[WorkbenchViewModel::class.java]
        testScheduler.runCurrent()
        assertTrue(after.polling)
        assertEquals(1_000L, after.pollIntervalMs)
        assertEquals("7", after.address)
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertTrue("回来后结果照常显示", after.readState is ReadState.Ok)

        // 点停止：请求停。
        after.changePolling(false)
        val sent = session.requests.size
        testScheduler.advanceTimeBy(10_000)
        testScheduler.runCurrent()
        assertEquals(sent, session.requests.size)
        assertFalse(after.polling)
        second.clear()
        poller.close()
    }

    @Test
    fun disconnectTurnsTheButtonBackToStart() = withModel { _, session ->
        val poller = MasterPoller(session, backgroundScope) { testScheduler.currentTime }
        session.next += List(20) { readOf(listOf(1)) }
        val store = ViewModelStore()
        val model = ViewModelProvider(store, session.factory(poller))[WorkbenchViewModel::class.java]
        model.changePolling(true)
        testScheduler.advanceTimeBy(3_000)
        testScheduler.runCurrent()
        assertTrue(model.polling)
        session.state.value = SessionState.Idle
        testScheduler.advanceTimeBy(1)
        testScheduler.runCurrent()
        val sent = session.requests.size
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        assertFalse("断开后按钮回到「开始」", model.polling)
        assertEquals(sent, session.requests.size)
        store.clear()
        poller.close()
    }

    @Test
    fun confirmShowsWhereTheCurrentValueCameFrom() = withModel { model, session ->
        session.next += readOf(listOf(5, 6))
        model.quantity = "2"
        model.read()
        advanceUntilIdle()
        model.selectMode(WorkbenchMode.WRITE)
        model.requestWrite()
        val pending = model.pending!!
        assertEquals(listOf<Int?>(5), pending.current)
        assertTrue(pending.currentFromRead)
        assertNotNull(pending.currentAgeMs)
    }

    @Test
    fun newSessionClearsCacheResultFormAndRecentWrites() = withModel { model, session ->
        session.next += readOf(listOf(5, 6))
        model.quantity = "2"
        model.read()
        advanceUntilIdle()
        model.selectMode(WorkbenchMode.WRITE)
        model.address = "9"
        model.requestWrite()
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(1, model.recentWrites.size)

        session.state.value = SessionState.Running(Role.SLAVE, ConnectionTarget.Demo, 2)
        advanceUntilIdle()
        assertEquals(ReadState.None, model.readState)
        assertTrue(model.recentWrites.isEmpty())
        assertEquals(WorkbenchMode.READ, model.mode)
        assertEquals("0", model.address)
        model.selectMode(WorkbenchMode.WRITE)
        model.requestWrite()
        assertEquals("换了会话，旧缓存不能再当“当前值”", listOf<Int?>(null), model.pending!!.current)
    }

    @Test
    fun disconnectingClearsTheWorkbench() = withModel { model, session ->
        model.selectMode(WorkbenchMode.WRITE)
        model.setWordText(0, "42")
        model.requestWrite()
        assertNotNull(model.pending)
        session.state.value = SessionState.Idle
        advanceUntilIdle()
        assertNull(model.pending)
        assertEquals(listOf("0"), model.writeWords)
        assertEquals(WorkbenchMode.READ, model.mode)
    }

    @Test
    fun successfulWriteUpdatesTheShownResultAndFlashesWithoutReading() = withModel { model, session ->
        session.next += readOf(listOf(1, 2, 3, 4, 5))
        model.quantity = "5"
        model.read()
        advanceUntilIdle()
        val readsBefore = session.requests.size
        model.selectMode(WorkbenchMode.WRITE)
        model.address = "2"
        model.setWordText(0, "99")
        model.requestWrite()
        model.confirmWrite()
        advanceUntilIdle()
        val shown = (model.readState as ReadState.Ok).result
        assertEquals(listOf(1, 2, 99, 4, 5), shown.values)
        assertEquals(setOf(2), shown.changedAt.keys)
        assertEquals("写入后不自动再发读取", readsBefore + 1, session.requests.size)

        // 地址在结果表之外：表不变。
        model.address = "20"
        model.setWordText(0, "7")
        model.requestWrite()
        model.confirmWrite()
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 99, 4, 5), (model.readState as ReadState.Ok).result.values)
    }

    @Test
    fun aWriteDoneOnTheWritePageStartsItsFlashWhenTheTableIsShownAgain() = withModel { _, session ->
        var now = 1_000L
        val model = WorkbenchViewModel(session, clock = { now })
        session.next += readOf(listOf(1, 2, 3, 4, 5))
        model.quantity = "5"
        model.read()
        advanceUntilIdle()
        model.selectMode(WorkbenchMode.WRITE)
        model.address = "2"
        model.setWordText(0, "99")
        model.requestWrite()
        now = 2_000L
        model.confirmWrite()
        advanceUntilIdle()
        // 写入成功：值已更新，变化时刻是写入时刻。
        assertEquals(2_000L, (model.readState as ReadState.Ok).result.changedAt[2])

        // 用户在写入页停留 30 秒后才回到读取页：闪烁的起点重置为“现在”，不是 30 秒前（那时 2 秒早过了）。
        now = 32_000L
        model.selectMode(WorkbenchMode.READ)
        assertEquals(32_000L, (model.readState as ReadState.Ok).result.changedAt[2])
        assertEquals(listOf(1, 2, 99, 4, 5), (model.readState as ReadState.Ok).result.values)

        // 只重置一次：再切一轮，时刻不变。
        now = 60_000L
        model.selectMode(WorkbenchMode.WRITE)
        model.selectMode(WorkbenchMode.READ)
        assertEquals(32_000L, (model.readState as ReadState.Ok).result.changedAt[2])
        model.onClearedForTest()
    }

    @Test
    fun aFreshReadDropsThePendingFlashRestamp() = withModel { _, session ->
        var now = 1_000L
        val model = WorkbenchViewModel(session, clock = { now })
        session.next += readOf(listOf(1, 2, 3, 4, 5))
        model.quantity = "5"
        model.read()
        advanceUntilIdle()
        model.selectMode(WorkbenchMode.WRITE)
        model.address = "2"
        model.setWordText(0, "99")
        model.requestWrite()
        model.confirmWrite()
        advanceUntilIdle()
        // 读回来的值和写入的值一样：没有变化，也就没有闪烁；回到读取页不能凭空再闪一次。
        session.next += readOf(listOf(1, 2, 99, 4, 5))
        model.selectMode(WorkbenchMode.READ)
        now = 50_000L
        model.read()
        advanceUntilIdle()
        assertTrue((model.readState as ReadState.Ok).result.changedAt.isEmpty())
        model.onClearedForTest()
    }

    @Test
    fun recentWriteSummaryFollowsTheAddressStyle() {
        fun entry(address: Int, area: RegisterArea = RegisterArea.HOLDING_REGISTER, words: List<Int> = listOf(123), unit: Int = 1) =
            RecentWrite(area, unit, address, words, emptyList(), 0x06, WriteOutcome.OK, null, 0L)
        assertEquals("FC06 U1 0 · 40001 = 123", writeSummary(entry(0), "广播", AddressStyle.BOTH))
        assertEquals("FC06 U1 40006 = 123", writeSummary(entry(5), "广播", AddressStyle.MODICON))
        assertEquals("FC06 U1 5 = 123", writeSummary(entry(5), "广播", AddressStyle.ZERO_BASED))
        assertEquals("FC06 广播 5 · 40006 = 123", writeSummary(entry(5, unit = 0), "广播", AddressStyle.BOTH))
    }

    @Test
    fun leadingZeroIsDroppedOnlyWhenTypingAfterASingleZero() {
        assertEquals("1", dropLeadingZero("0", "01"))
        assertEquals("0", dropLeadingZero("0", "00"))
        assertEquals("7B", dropLeadingZero("0", "07B"))
        assertEquals("0x", dropLeadingZero("0", "0x"))
        assertEquals("10", dropLeadingZero("0", "10"))
        assertEquals("102", dropLeadingZero("10", "102"))
        assertEquals("05", dropLeadingZero("5", "05"))
    }

    private fun readOf(values: List<Int>) =
        ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, values))

    private fun withModel(block: suspend TestScope.(WorkbenchViewModel, ScriptedSession) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val session = ScriptedSession()
            val model = ViewModelProvider(store, session.factory())[WorkbenchViewModel::class.java]
            advanceUntilIdle()
            block(model, session)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}

private fun WorkbenchViewModel.onClearedForTest() = changePolling(false)

/** 按顺序吐出预设结果；没有预设时：写请求回显成功，读请求断开。 */
private class ScriptedSession : SessionManager {
    val requests = mutableListOf<ModbusRequest>()
    val next = ArrayDeque<ModbusResult>()
    override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1))
    override val traffic = MutableSharedFlow<TrafficEvent>()
    override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(null)

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit

    override suspend fun disconnect() = Unit

    override suspend fun execute(request: ModbusRequest): ModbusResult {
        requests += request
        next.removeFirstOrNull()?.let { return it }
        return when (request) {
            is ModbusRequest.WriteSingleRegister ->
                ModbusResult.Success(ModbusResponse.WriteSingleRegister(request.unitId, request.function, request.address, request.value))
            is ModbusRequest.WriteSingleCoil ->
                ModbusResult.Success(ModbusResponse.WriteSingleCoil(request.unitId, request.function, request.address, request.value))
            is ModbusRequest.WriteMultipleRegisters ->
                ModbusResult.Success(ModbusResponse.WriteMultipleRegisters(request.unitId, request.function, request.address, request.quantity))
            is ModbusRequest.WriteMultipleCoils ->
                ModbusResult.Success(ModbusResponse.WriteMultipleCoils(request.unitId, request.function, request.address, request.quantity))
            else -> ModbusResult.Disconnected
        }
    }

    fun factory(poller: MasterPoller? = null): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return WorkbenchViewModel(this@ScriptedSession, poller = poller) as T
        }
    }
}
