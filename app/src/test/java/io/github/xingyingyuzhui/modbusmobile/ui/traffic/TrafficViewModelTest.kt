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
}
