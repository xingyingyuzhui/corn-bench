@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.DefaultSessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
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

    private fun TestScope.session(): DefaultSessionManager = DefaultSessionManager(
        scope = backgroundScope,
        clock = object : MonotonicClock {
            override fun nowNs(): Long = testScheduler.currentTime * 1_000_000L
        },
    )
}
