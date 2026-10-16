@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.core

import io.github.xingyingyuzhui.modbusmobile.core.master.DefaultMasterEngine
import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.slave.DefaultSlaveEngine
import io.github.xingyingyuzhui.modbusmobile.core.slave.InMemoryRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.transport.memory.InMemoryBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class EngineIntegrationTest {
    @Test
    fun readPresetHoldingRegisters() = runTest {
        val harness = harness()
        val result = harness.master.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 10))
        val response = assertIs<ModbusResponse.ReadRegisters>(assertIs<ModbusResult.Success>(result).response)
        assertEquals(listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90), response.values)
    }

    @Test
    fun writeSingleThenReadBack() = runTest {
        val harness = harness()
        val write = harness.master.execute(ModbusRequest.WriteSingleRegister(1, 0, 123))
        assertIs<ModbusResult.Success>(write)
        val read = harness.master.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 1))
        val response = assertIs<ModbusResponse.ReadRegisters>(assertIs<ModbusResult.Success>(read).response)
        assertEquals(listOf(123), response.values)
    }

    @Test
    fun unmappedAddressReturnsIllegalAddress() = runTest {
        val harness = harness()
        val result = harness.master.execute(ModbusRequest.ReadHoldingRegisters(1, 100, 1))
        assertEquals(ExceptionCode.ILLEGAL_DATA_ADDRESS, assertIs<ModbusResult.Exception>(result).code)
    }

    @Test
    fun broadcastWriteChangesRegisterWithoutReplyAndWaitsTurnaround() = runTest {
        val harness = harness(turnaroundMs = 150, timeoutMs = 800)
        harness.bank.putWord(1, RegisterArea.HOLDING_REGISTER, 3, 1)
        val started = testScheduler.currentTime
        val result = harness.master.execute(ModbusRequest.WriteSingleRegister(0, 3, 7))
        val elapsed = testScheduler.currentTime - started
        assertIs<ModbusResult.Success>(result)
        assertEquals(7, harness.bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == 3 }.value)
        assertTrue(harness.bus.right.sentFrames.isEmpty(), "从站不应回复广播")
        assertTrue(elapsed in 150..400, "turnaround=$elapsed")
    }

    @Test
    fun oneByteChunksStillComplete() = runTest {
        val harness = harness(chunkSize = 1)
        val result = harness.master.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 10))
        val response = assertIs<ModbusResponse.ReadRegisters>(assertIs<ModbusResult.Success>(result).response)
        assertEquals(listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90), response.values)
        val write = harness.master.execute(ModbusRequest.WriteSingleRegister(1, 1, 55))
        assertIs<ModbusResult.Success>(write)
        assertEquals(55, harness.bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == 1 }.value)
    }

    @Test
    fun echoedWriteIsAppliedOnce() = runTest {
        val harness = harness(chunkSize = 1, echo = true)
        val result = harness.master.execute(ModbusRequest.WriteSingleRegister(1, 0, 42))
        assertIs<ModbusResult.Success>(result)
        assertEquals(42, harness.bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == 0 }.value)
    }

    @Test
    fun silenceTimesOutAndWriteIsUnknown() = runTest {
        val harness = harness(withSlave = false, timeoutMs = 200)
        val readStarted = testScheduler.currentTime
        val read = harness.master.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 1))
        assertEquals(ModbusResult.Timeout, read)
        assertTrue(testScheduler.currentTime - readStarted >= 200)
        val write = harness.master.execute(ModbusRequest.WriteSingleRegister(1, 0, 1))
        assertEquals(ModbusResult.UnknownOutcome, write)
    }

    private fun TestScope.harness(
        chunkSize: Int = 256,
        echo: Boolean = false,
        timeoutMs: Long = 300,
        turnaroundMs: Long = 20,
        withSlave: Boolean = true,
    ): Harness {
        val clock = SchedulerMonotonicClock(testScheduler)
        val bus = InMemoryBus(clock, chunkSize = chunkSize, echo = echo)
        val bank = InMemoryRegisterBank()
        repeat(10) { bank.putWord(1, RegisterArea.HOLDING_REGISTER, it, it * 10) }
        repeat(8) { bank.putBit(1, RegisterArea.COIL, it, false) }
        val slave = if (withSlave) {
            DefaultSlaveEngine(
                transport = bus.right,
                bank = bank,
                clock = clock,
                enabledUnits = setOf(1),
                suppressEcho = echo,
            ).also { it.start(backgroundScope) }
        } else {
            null
        }
        val master = DefaultMasterEngine(
            transport = bus.left,
            clock = clock,
            scope = backgroundScope,
            responseTimeout = timeoutMs.milliseconds,
            turnaround = turnaroundMs.milliseconds,
            suppressEcho = echo,
        )
        return Harness(bus, bank, master, slave)
    }

    private class Harness(
        val bus: InMemoryBus,
        val bank: InMemoryRegisterBank,
        val master: DefaultMasterEngine,
        val slave: DefaultSlaveEngine?,
    )
}
