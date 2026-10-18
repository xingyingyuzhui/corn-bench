package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.RecorderState
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.TrafficRecorder
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficViewModelTest {
    private class FakeSession : SessionManager {
        override val state = MutableStateFlow<SessionState>(SessionState.Idle)
        override val traffic = MutableSharedFlow<TrafficEvent>(replay = 1_000)
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
    }

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun rig(pageSize: Int = 10): Triple<FakeSession, TrafficRecorder, TrafficViewModel> {
        val session = FakeSession()
        val recorder = TrafficRecorder(session, CoroutineScope(dispatcher))
        return Triple(session, recorder, TrafficViewModel(recorder, pageSize))
    }

    private suspend fun FakeSession.emit(kind: TrafficKind = TrafficKind.TX_REQUEST) {
        traffic.emit(
            TrafficEvent(
                System.nanoTime(),
                if (kind == TrafficKind.TX_REQUEST) TrafficDirection.TX else TrafficDirection.RX,
                byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x02, 0xC4.toByte(), 0x0B),
                kind,
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun startsEmptyAndNotRecording() = runTest(dispatcher) {
        val (session, _, model) = rig()
        advanceUntilIdle()
        session.emit(); session.emit()
        assertEquals(RecorderState.IDLE, model.ui.value.recorder)
        assertEquals(0, model.ui.value.items.size)
        assertEquals(1, model.ui.value.pageCount)
    }

    @Test
    fun paginatesAndFollowsTheLatestPage() = runTest(dispatcher) {
        val (session, _, model) = rig(pageSize = 10)
        model.start(); advanceUntilIdle()
        repeat(25) { session.emit() }
        var ui = model.ui.value
        assertEquals(3, ui.pageCount)
        assertEquals("跟随最新：停在最后一页", 2, ui.page)
        assertEquals(5, ui.items.size)
        assertTrue(ui.following)

        model.previousPage()
        ui = model.ui.value
        assertEquals(1, ui.page)
        assertEquals(10, ui.items.size)
        assertTrue("翻到旧页就不再跟随", !ui.following)
        session.emit()
        assertEquals("记录继续，但页面不被新帧顶走", 1, model.ui.value.page)
        assertEquals(26, model.ui.value.matching)

        model.goToPage(0)
        assertEquals(0, model.ui.value.page)
        model.previousPage()
        assertEquals("不会翻到负页", 0, model.ui.value.page)

        model.lastPage()
        assertEquals(2, model.ui.value.page)
        assertTrue("回到最后一页 = 回到跟随最新", model.ui.value.following)
        model.nextPage()
        assertEquals("不会翻过最后一页", 2, model.ui.value.page)
    }

    @Test
    fun pageCountHandlesEmptyExactAndRemainder() = runTest(dispatcher) {
        val (session, _, model) = rig(pageSize = 10)
        model.start(); advanceUntilIdle()
        assertEquals(1, model.ui.value.pageCount)
        repeat(10) { session.emit() }
        assertEquals(1, model.ui.value.pageCount)
        session.emit()
        assertEquals(2, model.ui.value.pageCount)
    }

    @Test
    fun filterAppliesToTheWholeRecordBeforePaging() = runTest(dispatcher) {
        val (session, _, model) = rig(pageSize = 10)
        model.start(); advanceUntilIdle()
        repeat(12) { session.emit(TrafficKind.TX_REQUEST) }
        repeat(3) { session.emit(TrafficKind.ECHO) }
        assertEquals(2, model.ui.value.pageCount)
        model.setFilter(LogFilter.ECHO)
        val ui = model.ui.value
        assertEquals(1, ui.pageCount)
        assertEquals(3, ui.matching)
        assertEquals(15, ui.recorded)
        assertTrue(ui.items.all { it is LogEntry && it.event.kind == TrafficKind.ECHO })
    }

    @Test
    fun clearEmptiesTheListButKeepsRecording() = runTest(dispatcher) {
        val (session, _, model) = rig()
        model.start(); advanceUntilIdle()
        repeat(5) { session.emit() }
        model.clear()
        assertEquals(0, model.ui.value.items.size)
        assertEquals(0, model.ui.value.recorded)
        assertEquals(RecorderState.RECORDING, model.ui.value.recorder)
        session.emit()
        assertEquals(1, model.ui.value.recorded)
    }

    @Test
    fun lockingTheScrollDoesNotPauseTheRecording() = runTest(dispatcher) {
        val (session, _, model) = rig()
        model.start(); advanceUntilIdle()
        session.emit()
        model.toggleLocked()
        assertTrue(model.locked.value)
        session.emit(); session.emit()
        assertEquals("锁定滚动只是不跟随，新帧照常记录", 3, model.ui.value.recorded)
        assertEquals(RecorderState.RECORDING, model.ui.value.recorder)
    }

    @Test
    fun pausingStopsCapturingButKeepsTheFrames() = runTest(dispatcher) {
        val (session, _, model) = rig()
        model.start(); advanceUntilIdle()
        session.emit(); session.emit()
        model.pauseRecording()
        dispatcher.scheduler.advanceUntilIdle()
        session.emit()
        assertEquals(2, model.ui.value.recorded)
        assertEquals(RecorderState.PAUSED, model.ui.value.recorder)
    }

    @Test
    fun exportIncludesEverythingRegardlessOfFilterAndPage() = runTest(dispatcher) {
        val (session, _, model) = rig(pageSize = 10)
        model.start(); advanceUntilIdle()
        repeat(12) { session.emit(TrafficKind.TX_REQUEST) }
        repeat(3) { session.emit(TrafficKind.ECHO) }
        model.setFilter(LogFilter.ECHO)
        assertEquals(15, model.exportItems().count { it is LogEntry })
    }

    @Test
    fun everyFrameCarriesAWallClockCloseToNow() = runTest(dispatcher) {
        val (session, _, model) = rig()
        model.start(); advanceUntilIdle()
        val before = System.currentTimeMillis()
        session.emit()
        val after = System.currentTimeMillis()
        val entry = model.ui.value.items.single() as LogEntry
        assertTrue("墙钟 ${entry.wallMs} 应在 [$before, $after] 内（容差 50ms）", entry.wallMs in (before - 50)..(after + 50))
    }
    // ---- T-20261018-qa-01：记录中持续进帧时 refresh 越界（IndexOutOfBoundsException @ TrafficViewModel.refresh） ----

    /**
     * 真实并发的装置：记录器的收集协程跑在 [Dispatchers.Unconfined] 上，由生产线程 emit 时就地执行 onEvent，
     * 所以 refresh()（测试线程）执行期间记录器会被另一个线程持续追加——和从站演示里对端主站每秒发帧的情况一样。
     * 容量放到最大，避免记满后停止记录。
     */
    private class LiveRig(pageSize: Int) {
        val session = FakeSession()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val recorder = TrafficRecorder(session, scope, maxFrames = Int.MAX_VALUE, maxBytes = Long.MAX_VALUE)
        val model = TrafficViewModel(recorder, pageSize)
        private val stop = AtomicBoolean(false)
        private var producer: Thread? = null

        fun startProducing(gapNs: Long = 20_000) {
            producer = thread(name = "qa01-traffic-producer", isDaemon = true) {
                runBlocking {
                    var n = 0L
                    while (!stop.get()) {
                        val kind = if (n++ % 4 == 3L) TrafficKind.ECHO else TrafficKind.TX_REQUEST
                        session.traffic.emit(
                            TrafficEvent(
                                System.nanoTime(),
                                if (kind == TrafficKind.TX_REQUEST) TrafficDirection.TX else TrafficDirection.RX,
                                byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x02, 0xC4.toByte(), 0x0B),
                                kind,
                            ),
                        )
                        val until = System.nanoTime() + gapNs
                        while (System.nanoTime() < until) {
                            // 忙等，模拟连续进帧
                        }
                    }
                }
            }
        }

        fun close() {
            stop.set(true)
            producer?.join(5_000)
            scope.cancel()
        }
    }

    private fun assertConsistent(ui: LogUiState, recorder: TrafficRecorder, round: Int) {
        assertTrue("第 $round 轮：matching=${ui.matching} 不应大于 recorder.size", ui.matching <= recorder.size)
        assertTrue("第 $round 轮：recorded=${ui.recorded} 不应大于 recorder.frames", ui.recorded <= recorder.frames)
        assertTrue("第 $round 轮：一页最多 pageSize 项", ui.items.size <= ui.pageSize)
        assertTrue("第 $round 轮：页内按 seq 递增", ui.items.zipWithNext().all { (a, b) -> a.seq < b.seq })
    }

    /** A4：记录中持续进帧，refresh（翻页 / 改过滤触发）重复 5000 轮不越界。修复前此测试抛 IndexOutOfBoundsException。 */
    @Test
    fun refreshNeverIndexesPastItsSnapshotWhileFramesKeepArriving() {
        val rig = LiveRig(pageSize = 10)
        try {
            rig.model.start()
            rig.startProducing()
            val filters = arrayOf(LogFilter.ALL, LogFilter.ECHO, LogFilter.LOCAL)
            var seen = 0L
            repeat(5_000) { round ->
                when (round % 3) {
                    0 -> rig.model.setFilter(filters[(round / 3) % filters.size])
                    1 -> rig.model.previousPage()
                    else -> rig.model.lastPage()
                }
                assertConsistent(rig.model.ui.value, rig.recorder, round)
                if (round % 50 == 49) {
                    seen += rig.recorder.frames
                    rig.model.clear() // 控制列表长度，同时覆盖清除
                }
            }
            assertTrue("生产线程应确实在进帧（否则没测到并发），seen=$seen", seen > 0)
            assertEquals(RecorderState.RECORDING, rig.recorder.state.value)
        } finally {
            rig.close()
        }
    }

    /** A5：记录中调用 clear() 的同时持续进帧：不越界，页码回到第 0 页。 */
    @Test
    fun clearWhileFramesKeepArrivingGoesBackToTheFirstPage() {
        val rig = LiveRig(pageSize = 1_000)
        try {
            rig.model.start()
            rig.startProducing()
            repeat(1_000) { round ->
                rig.model.setFilter(if (round % 2 == 0) LogFilter.ALL else LogFilter.ECHO)
                rig.model.clear()
                val ui = rig.model.ui.value
                assertEquals("第 $round 轮：清除后回到第 0 页", 0, ui.page)
                assertTrue("第 $round 轮：清除后只剩清除之后进来的帧", ui.recorded < 1_000)
                assertConsistent(ui, rig.recorder, round)
            }
            assertEquals(RecorderState.RECORDING, rig.recorder.state.value)
        } finally {
            rig.close()
        }
    }
}
