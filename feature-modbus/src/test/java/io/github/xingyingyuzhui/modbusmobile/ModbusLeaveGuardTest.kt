package io.github.xingyingyuzhui.modbusmobile

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外壳切换守卫的上位机侧：[leaveBlockedReasonFor] / [releaseSession] / 容器未创建时的 [ModbusFeature]。
 * 外壳 `BenchShellTest` 用的是假守卫，这里锁定真实判定：各忙态的提示文案、超时返回 false。
 */
class ModbusLeaveGuardTest {
    @Test
    fun idleAndErrorCanLeave() {
        assertNull(leaveBlockedReasonFor(SessionState.Idle))
        assertNull(leaveBlockedReasonFor(SessionState.Error("串口被拔掉")))
    }

    @Test
    fun everyBusyStateBlocksWithItsOwnMessage() {
        assertEquals(
            "上位机演示会话正在运行：请先在连接页断开",
            leaveBlockedReasonFor(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, epoch = 1)),
        )
        assertEquals(
            "串口已连接：请先在连接页断开后再切换",
            leaveBlockedReasonFor(SessionState.Running(Role.SLAVE, ConnectionTarget.Usb(deviceId = 7, portIndex = 0), epoch = 2)),
        )
        assertEquals("正在请求 USB 授权：等授权结果出来后再切换", leaveBlockedReasonFor(SessionState.RequestingPermission))
        assertEquals("正在打开串口：等连接结束或失败后再切换", leaveBlockedReasonFor(SessionState.Opening))
        assertEquals("正在断开串口：等断开完成后再切换", leaveBlockedReasonFor(SessionState.Stopping))
    }

    /** 守卫不创建容器：上位机模式本进程没用过时，直接放行，也不会顺带建出 USB 仓库 / Room。 */
    @Test
    fun uncreatedContainerNeverBlocksAndStaysUncreated() = runTest {
        ModbusFeature.resetForTest()
        assertNull(ModbusFeature.leaveBlockedReason())
        assertTrue(ModbusFeature.releaseUsb())
        assertFalse(ModbusFeature.isCreated)
    }

    @Test
    fun releaseFromIdleStopsPollingWithoutDisconnect() = runTest {
        var stopped = 0
        var disconnects = 0
        val ok = releaseSession(MutableStateFlow(SessionState.Idle), { stopped++ }, { disconnects++ }, 5_000)
        assertTrue(ok)
        assertEquals(1, stopped)
        assertEquals(0, disconnects)
    }

    @Test
    fun releaseFromRunningDisconnectsAndWaitsForIdle() = runTest {
        val state = MutableStateFlow<SessionState>(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, epoch = 1))
        var disconnects = 0
        var result: Boolean? = null
        launch {
            result = releaseSession(state, {}, { disconnects++; state.value = SessionState.Stopping }, 5_000)
        }
        advanceTimeBy(1_000)
        assertEquals(1, disconnects)
        assertNull("还在 Stopping，不应返回", result)
        state.value = SessionState.Idle
        testScheduler.advanceUntilIdle()
        assertEquals(true, result)
    }

    /** 断开后落在 Error 也算释放完成（串口已经不在了）。 */
    @Test
    fun releaseAcceptsErrorAsReleased() = runTest {
        val state = MutableStateFlow<SessionState>(SessionState.Opening)
        val ok = releaseSession(state, {}, { state.value = SessionState.Error("打开失败") }, 5_000)
        assertTrue(ok)
    }

    /** 一直卡在 Stopping：超时返回 false，外壳停在原模式。 */
    @Test
    fun releaseTimesOutWhileStuckStopping() = runTest {
        val state = MutableStateFlow<SessionState>(SessionState.Stopping)
        var disconnects = 0
        val ok = releaseSession(state, {}, { disconnects++ }, 5_000)
        assertFalse(ok)
        assertEquals(1, disconnects)
    }
}
