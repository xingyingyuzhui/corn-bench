package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeClock : RecorderClock {
    var nano = 1_000_000_000L
    var wall = 1_760_000_000_000L
    override fun nanoTime() = nano
    override fun wallMs() = wall
    fun advanceMs(ms: Long) {
        nano += ms * 1_000_000L
        wall += ms
    }
}

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

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficRecorderTest {
    private fun event(ns: Long, size: Int = 8) =
        TrafficEvent(ns, TrafficDirection.TX, ByteArray(size) { it.toByte() }, TrafficKind.TX_REQUEST)

    private class Rig(maxFrames: Int = 20_000, maxBytes: Long = 8L * 1024 * 1024) {
        val clock = FakeClock()
        val session = FakeSession()
        val scope = TestScope(StandardTestDispatcher())
        val recorder = TrafficRecorder(session, scope, clock, maxFrames, maxBytes)
        suspend fun emit(sizeBytes: Int = 8) {
            session.traffic.emit(TrafficEvent(clock.nanoTime(), TrafficDirection.TX, ByteArray(sizeBytes), TrafficKind.TX_REQUEST))
            scope.advanceUntilIdle()
        }
    }

    @Test
    fun recordsNothingUntilStartIsPressed() = runTest {
        val rig = Rig()
        rig.emit(); rig.emit()
        assertEquals(RecorderState.IDLE, rig.recorder.state.value)
        assertEquals(0, rig.recorder.size)
    }

    @Test
    fun startDropsTheFramesThatWereReplayedFromBeforeStart() = runTest {
        val rig = Rig()
        rig.emit(); rig.clock.advanceMs(500); rig.emit() // 点开始之前已经在 SharedFlow replay 里的两帧
        rig.clock.advanceMs(10)
        rig.recorder.start(); rig.scope.advanceUntilIdle()
        assertEquals("replay 里的旧帧不能被记进来", 0, rig.recorder.size)
        rig.clock.advanceMs(5)
        rig.emit()
        assertEquals(1, rig.recorder.size)
    }

    @Test
    fun eachFrameGetsItsWallClockFromTheMonotonicStamp() = runTest {
        val rig = Rig()
        rig.recorder.start(); rig.scope.advanceUntilIdle()
        val t0 = rig.clock.wall
        // 事件发生在 40 ms 前（比如协程调度有延迟）：墙钟应是“现在 − 40ms”
        val ns = rig.clock.nano
        rig.clock.advanceMs(40)
        rig.session.traffic.emit(event(ns))
        rig.scope.advanceUntilIdle()
        assertEquals(t0, rig.recorder.get(0).wallMs)
        // 过一会儿再来一帧：墙钟按各自的单调时刻算
        rig.clock.advanceMs(1000)
        rig.emit()
        assertEquals(t0 + 40 + 1000, rig.recorder.get(1).wallMs)
    }

    @Test
    fun pauseKeepsExistingFramesAndStopsCapturing() = runTest {
        val rig = Rig()
        rig.recorder.start(); rig.scope.advanceUntilIdle()
        rig.emit(); rig.emit()
        rig.recorder.pause()
        assertEquals(RecorderState.PAUSED, rig.recorder.state.value)
        rig.clock.advanceMs(10); rig.emit(); rig.emit(); rig.emit()
        assertEquals("暂停期间不记录，已有的保留", 2, rig.recorder.size)
        // 继续：暂停期间的帧（在 replay 里）不会补进来
        rig.clock.advanceMs(10)
        rig.recorder.start(); rig.scope.advanceUntilIdle()
        assertEquals(2, rig.recorder.size)
        rig.clock.advanceMs(5); rig.emit()
        assertEquals(3, rig.recorder.size)
    }

    @Test
    fun clearEmptiesTheListAndKeepsTheState() = runTest {
        val rig = Rig()
        rig.recorder.start(); rig.scope.advanceUntilIdle()
        rig.emit(); rig.emit()
        rig.recorder.clear()
        assertEquals(0, rig.recorder.size)
        assertEquals(RecorderState.RECORDING, rig.recorder.state.value)
        rig.clock.advanceMs(1); rig.emit()
        assertEquals(1, rig.recorder.size)
    }

    @Test
    fun stopsWhenFullAndSaysSoInsteadOfDroppingOldFrames() = runTest {
        val rig = Rig(maxFrames = 3)
        rig.recorder.start(); rig.scope.advanceUntilIdle()
        repeat(5) { rig.clock.advanceMs(1); rig.emit() }
        assertEquals(RecorderState.FULL, rig.recorder.state.value)
        assertEquals(3, rig.recorder.frames)
        assertEquals("最早的帧还在，没有被挤掉", 1_760_000_000_001L, rig.recorder.get(0).wallMs)
        rig.recorder.start()
        assertEquals("记满后不能直接继续", RecorderState.FULL, rig.recorder.state.value)
        rig.recorder.clear()
        assertEquals(RecorderState.PAUSED, rig.recorder.state.value)
        assertEquals(0, rig.recorder.size)
        rig.clock.advanceMs(1) // 真实时钟不会让“继续”和上一帧落在同一纳秒
        rig.recorder.start(); rig.scope.advanceUntilIdle()
        rig.clock.advanceMs(1); rig.emit()
        assertEquals(1, rig.recorder.size)
    }

    @Test
    fun stopsWhenTheByteBudgetIsReached() = runTest {
        val rig = Rig(maxBytes = 3L * (100 + TrafficRecorder.ITEM_OVERHEAD) + 10)
        rig.recorder.start(); rig.scope.advanceUntilIdle()
        repeat(6) { rig.clock.advanceMs(1); rig.emit(sizeBytes = 100) }
        assertEquals(RecorderState.FULL, rig.recorder.state.value)
        assertEquals(3, rig.recorder.frames)
    }

    @Test
    fun aNewSessionInsertsAMarkerButKeepsTheFrames() = runTest {
        val rig = Rig()
        rig.session.state.value = SessionState.Running(Role.MASTER, ConnectionTarget.Demo, epoch = 1)
        rig.recorder.start(); rig.scope.advanceUntilIdle()
        rig.clock.advanceMs(1); rig.emit()
        rig.session.state.value = SessionState.Running(Role.MASTER, ConnectionTarget.Demo, epoch = 2)
        rig.scope.advanceUntilIdle()
        rig.clock.advanceMs(1); rig.emit()
        val items = rig.recorder.snapshot()
        assertEquals(3, items.size)
        assertNull("第二项是分隔标记", items[1].event)
        assertEquals(2L, items[1].epoch)
        assertEquals("标记不算帧", 2, rig.recorder.frames)
        assertTrue(items[2].event != null)
    }

    @Test
    fun startingTwiceOrPausingWhileIdleDoesNothing() = runTest {
        val rig = Rig()
        rig.recorder.pause()
        assertEquals(RecorderState.IDLE, rig.recorder.state.value)
        rig.recorder.start(); rig.recorder.start(); rig.scope.advanceUntilIdle()
        rig.clock.advanceMs(1); rig.emit()
        assertEquals("重复 start 不会重复记录", 1, rig.recorder.size)
    }
}
