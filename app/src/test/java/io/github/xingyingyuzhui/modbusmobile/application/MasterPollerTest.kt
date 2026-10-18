@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-20261018-qa-02：应用级主站轮询是唯一状态源。纯 JVM（不碰 android.*），不受 B-3 影响。
 * 间隔 1 s、读取本身不耗时，所以 N 秒内应该发 N+1 次左右（第 0 秒立刻发一次）。
 */
class MasterPollerTest {
    private val read = ModbusRequest.ReadHoldingRegisters(1, 0, 2)

    @Test
    fun keepsPollingWithNoSubscriberForSixtySeconds() = withPoller { poller, session ->
        poller.start(read, 1_000)
        advance(60_000)
        assertTrue("没有订阅方（离开了工作台）也照常发：${session.requests.size}", session.requests.size in 60..62)
        assertTrue(poller.state.value.running)
        assertEquals(1_000L, poller.state.value.intervalMs)
        poller.stop()
    }

    @Test
    fun subscriberGoingAwayDoesNotStopPolling() = withPoller { poller, session ->
        val client = RecordingClient(read)
        val detach = poller.attach(client)
        poller.start(read, 1_000)
        advance(5_000)
        val seen = client.outcomes.size
        detach()
        advance(10_000)
        assertEquals("断开后不再回调旧订阅方", seen, client.outcomes.size)
        assertTrue(session.requests.size >= 15)
        assertTrue(poller.state.value.running)
        poller.stop()
    }

    @Test
    fun stopStopsRequests() = withPoller { poller, session ->
        poller.start(read, 1_000)
        advance(3_000)
        poller.stop()
        val sent = session.requests.size
        advance(10_000)
        assertEquals(sent, session.requests.size)
        assertFalse(poller.state.value.running)
    }

    @Test
    fun disconnectStopsPolling() = withPoller { poller, session ->
        poller.start(read, 1_000)
        advance(3_000)
        session.state.value = SessionState.Idle
        advance(1)
        val sent = session.requests.size
        advance(10_000)
        assertFalse("断开后按钮回到「开始」", poller.state.value.running)
        assertEquals(sent, session.requests.size)
    }

    @Test
    fun reconnectWithANewEpochStopsPolling() = withPoller { poller, session ->
        poller.start(read, 1_000)
        advance(2_000)
        session.state.value = SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 2)
        advance(1)
        assertFalse(poller.state.value.running)
    }

    @Test
    fun closeStopsAndRefusesNewStarts() = withPoller { poller, session ->
        poller.start(read, 1_000)
        advance(2_000)
        poller.close()
        poller.start(read, 1_000)
        val sent = session.requests.size
        advance(10_000)
        assertFalse(poller.state.value.running)
        assertEquals(sent, session.requests.size)
    }

    @Test
    fun intervalChangeAppliesWhileRunning() = withPoller { poller, session ->
        poller.start(read, 1_000)
        advance(500)
        poller.setInterval(5_000)
        session.requests.clear()
        advance(20_000)
        // 第 1 s 那一拍还按旧间隔等完，之后每 5 s 一次：1、6、11、16 s → 4 次。
        assertEquals(4, session.requests.size)
        assertEquals(5_000L, poller.state.value.intervalMs)
        poller.stop()
    }

    @Test
    fun yieldsWhileTheSubscriberHoldsAndResumesAfterwards() = withPoller { poller, session ->
        val client = RecordingClient(read).apply { hold = true }
        poller.attach(client)
        poller.start(read, 1_000)
        advance(3_000)
        assertTrue("让路时不发", session.requests.isEmpty())
        assertTrue(poller.state.value.running)
        client.hold = false
        advance(1_500)
        assertTrue(session.requests.isNotEmpty())
        poller.stop()
    }

    @Test
    fun followsTheSubscribersFormAndStopsWhenItBecomesInvalid() = withPoller { poller, session ->
        val client = RecordingClient(read)
        poller.attach(client)
        poller.start(read, 1_000)
        advance(1_500)
        val other = ModbusRequest.ReadInputRegisters(2, 10, 1)
        client.request = other
        advance(1_000)
        assertEquals(other, session.requests.last())
        assertEquals(other, poller.state.value.request)
        client.request = null
        advance(2_000)
        assertFalse(poller.state.value.running)
    }

    @Test
    fun threeInvalidResponsesInARowStop() = withPoller { poller, session ->
        val bad = ModbusResult.InvalidResponse("noise")
        session.next += listOf(bad, bad, ok(), bad, bad, bad)
        poller.start(read, 1_000)
        advance(20_000)
        assertEquals(6, session.requests.size)
        assertFalse(poller.state.value.running)
    }

    @Test
    fun disconnectedResultStops() = withPoller { poller, session ->
        session.next += ModbusResult.Disconnected
        poller.start(read, 1_000)
        advance(5_000)
        assertEquals(1, session.requests.size)
        assertFalse(poller.state.value.running)
    }

    @Test
    fun restartReplacesTheRunningLoop() = withPoller { poller, session ->
        poller.start(read, 1_000)
        advance(2_500)
        poller.start(read, 1_000)
        session.requests.clear()
        advance(10_000)
        // 只有一个循环在跑：10 s 内 11 次左右，不是 20+。
        assertTrue("${session.requests.size}", session.requests.size in 10..12)
        poller.stop()
    }

    @Test
    fun aCrashInsideTheLoopTurnsTheButtonBackToStart() = runTest {
        // 循环里抛异常（这里是 execute 抛）：finally 里 finish，状态不能卡在「运行中」，之后还能重新开始。
        val session = FakeSession()
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> errors += e })
        val poller = MasterPoller(session, scope) { testScheduler.currentTime }
        session.throwOnNext = IllegalStateException("boom")
        poller.start(read, 1_000)
        advance(100)
        assertEquals(1, errors.size)
        assertFalse("异常后按钮回到「开始」", poller.state.value.running)
        poller.start(read, 1_000)
        advance(3_000)
        assertTrue(poller.state.value.running)
        assertTrue(session.requests.size >= 4)
        poller.close()
        scope.cancel()
    }

    @Test
    fun startRightAfterConnectingIsNotStoppedByTheStaleEpoch() = withPoller { poller, session ->
        // poller 建好时还没连接（epoch = null），watcher 已经收过 Idle。
        session.state.value = SessionState.Idle
        advance(1)
        // 刚连上（epoch 1），watcher 还没来得及收到，用户就点了开始：start() 要先同步 epoch，
        // 否则 watcher 随后看到 1 ≠ null 会把刚开始的轮询停掉。
        session.state.value = SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1)
        poller.start(read, 1_000)
        advance(3_000)
        assertTrue("同一次连接内开始的轮询不能被停", poller.state.value.running)
        assertTrue(session.requests.size >= 3)
        poller.stop()
    }

    @Test
    fun reconnectDuringExecuteDoesNotDeliverOldOutcomeBeforeWatcherRuns() = withPoller { poller, session ->
        val client = RecordingClient(read)
        poller.attach(client)
        session.afterExecute = {
            session.state.value = SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 2)
        }
        poller.start(read, 1_000)
        advance(1)
        assertEquals(1, session.requests.size)
        assertTrue(client.outcomes.isEmpty())
        assertFalse(poller.state.value.running)
    }

    private fun ok() = ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(1, 2)))

    private fun TestScope.advance(ms: Long) {
        testScheduler.advanceTimeBy(ms)
        testScheduler.runCurrent()
    }

    private fun withPoller(block: suspend TestScope.(MasterPoller, FakeSession) -> Unit) = runTest {
        val session = FakeSession()
        val poller = MasterPoller(session, backgroundScope) { testScheduler.currentTime }
        block(poller, session)
        poller.close()
    }

    private class RecordingClient(var request: ModbusRequest?) : MasterPoller.Client {
        var hold = false
        val outcomes = mutableListOf<MasterPoller.Outcome>()
        override fun holdPolling() = hold
        override fun nextRequest() = request
        override fun onPollResult(outcome: MasterPoller.Outcome) {
            outcomes += outcome
        }
    }

    /** 读请求默认回成功；[next] 里有预设就先吐预设。 */
    private class FakeSession : SessionManager {
        val requests = mutableListOf<ModbusRequest>()
        val next = ArrayDeque<ModbusResult>()
        var throwOnNext: Throwable? = null
        var afterExecute: (() -> Unit)? = null
        override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1))
        override val traffic = MutableSharedFlow<TrafficEvent>()
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() {
            state.value = SessionState.Idle
        }
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            requests += request
            afterExecute?.invoke()
            throwOnNext?.let { throwOnNext = null; throw it }
            next.removeFirstOrNull()?.let { return it }
            return ModbusResult.Success(ModbusResponse.ReadRegisters(request.unitId, FunctionCode.READ_HOLDING_REGISTERS, listOf(1, 2)))
        }
    }
}
