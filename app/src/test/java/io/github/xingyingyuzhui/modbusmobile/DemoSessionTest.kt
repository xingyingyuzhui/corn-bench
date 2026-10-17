@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.DefaultSessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionCounters
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.rtu.Crc16Modbus
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSessionTest {
    @Test
    fun masterDemoReadsPresetWritesAndLogsCrc() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.MASTER)
        val read = session.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 10))
        val values = (read as ModbusResult.Success).response as ModbusResponse.ReadRegisters
        assertEquals(listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90), values.values)

        val write = session.execute(ModbusRequest.WriteSingleRegister(1, 0, 123))
        assertTrue(write is ModbusResult.Success)
        val again = session.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 1))
        val echoed = (again as ModbusResult.Success).response as ModbusResponse.ReadRegisters
        assertEquals(listOf(123), echoed.values)

        runCurrent()
        val traffic = session.traffic.replayCache
        assertTrue(traffic.any { it.direction == TrafficDirection.TX })
        assertTrue(traffic.any { it.direction == TrafficDirection.RX })
        traffic.forEach { event ->
            assertTrue(Crc16Modbus.matches(event.bytes))
        }
        session.disconnect()
        assertEquals(SessionState.Idle, session.state.value)
    }

    @Test
    fun slaveDemoChangesHoldingRegisterEveryFiveSeconds() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        val bank = session.slaveBank.value ?: error("从站寄存器库为空")
        assertEquals(0, bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == 0 }.value)
        // 第 5 拍先读再写。写请求还要等 SerialSettings 默认的 20ms 请求间隔。
        advanceTimeBy(5_100)
        runCurrent()
        val value = bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == 0 }.value
        assertTrue("HR0=$value", value >= 1)
        val traffic = session.traffic.replayCache
        assertTrue(traffic.any { it.direction == TrafficDirection.RX })
        assertTrue(traffic.any { it.direction == TrafficDirection.TX })
        session.disconnect()
    }

    @Test
    fun masterCountersFollowTheSessionAndResetOnDisconnect() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.MASTER)
        assertEquals("连接时从 0 开始", SessionCounters.Empty, session.counters.value)
        assertTrue(session.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 10)) is ModbusResult.Success)
        // 地址 100 没有映射，演示从站回异常 02：算一次错误。
        assertTrue(session.execute(ModbusRequest.ReadHoldingRegisters(1, 100, 1)) is ModbusResult.Exception)
        runCurrent()
        val counters = session.counters.value
        assertEquals(2, counters.tx)
        assertEquals(2, counters.rx)
        assertEquals(1, counters.errors)
        session.disconnect()
        assertEquals("断开后归零", SessionCounters.Empty, session.counters.value)
    }

    @Test
    fun slaveCountersCountLocalRequestsAndResponses() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        advanceTimeBy(5_100)
        runCurrent()
        val counters = session.counters.value
        assertTrue("演示主站每秒读一次：${counters.localRequests}", counters.localRequests >= 5)
        assertEquals("每个本机请求都回了", counters.localRequests, counters.responses)
        assertEquals(0, counters.exceptions)
        assertEquals(0, counters.foreignFrames)
        session.disconnect()
    }

    @Test
    fun slaveSessionRunsSimulationRulesAndStopsThemOnDisconnect() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        val simulation = session.simulation.value ?: error("从站会话应该有模拟器")
        simulation.setRules(
            listOf(
                io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationRule(
                    id = "hr5",
                    target = io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationTarget(1, RegisterArea.HOLDING_REGISTER, 5),
                    waveform = io.github.xingyingyuzhui.modbusmobile.core.slave.Waveform.Ramp(start = 1.0, step = 1.0, min = 0.0, max = 100.0),
                    refreshMs = 1_000,
                ),
            ),
        )
        advanceTimeBy(3_100)
        runCurrent()
        val bank = session.slaveBank.value ?: error("从站寄存器库为空")
        val hr5 = bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == 5 }.value
        assertTrue("模拟规则在写 HR5：$hr5", hr5 >= 3)
        assertEquals(
            io.github.xingyingyuzhui.modbusmobile.core.slave.ChangeSource.SIMULATION,
            bank.lastChange(1, RegisterArea.HOLDING_REGISTER, 5)?.source,
        )
        session.disconnect()
        assertEquals(null, session.simulation.value)
    }

    @Test
    fun masterSessionHasNoSimulation() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.MASTER)
        assertEquals(null, session.simulation.value)
        session.disconnect()
    }

    @Test
    fun previewFrameIsTheBytesTheMasterSends() = runTest {
        val session = session()
        val request = ModbusRequest.WriteSingleRegister(1, 5, 123)
        val preview = session.previewFrame(request)
        assertEquals("01 06 00 05 00 7B D9 E8", preview.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.MASTER)
        assertTrue(session.execute(request) is ModbusResult.Success)
        runCurrent()
        val sent = session.traffic.replayCache.last { it.direction == TrafficDirection.TX }
        assertTrue("预览和实际发出的字节一致", preview.contentEquals(sent.bytes))
        session.disconnect()
    }

    private fun TestScope.session(): DefaultSessionManager = DefaultSessionManager(
        scope = backgroundScope,
        clock = object : MonotonicClock {
            override fun nowNs(): Long = testScheduler.currentTime * 1_000_000L
        },
    )
}
