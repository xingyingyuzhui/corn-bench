@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.core

import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.rtu.BusTopology
import io.github.xingyingyuzhui.modbusmobile.core.rtu.Crc16Modbus
import io.github.xingyingyuzhui.modbusmobile.core.rtu.EchoMode
import io.github.xingyingyuzhui.modbusmobile.core.rtu.RtuCodec
import io.github.xingyingyuzhui.modbusmobile.core.rtu.ScannerConfig
import io.github.xingyingyuzhui.modbusmobile.core.rtu.f
import io.github.xingyingyuzhui.modbusmobile.core.slave.DefaultSlaveEngine
import io.github.xingyingyuzhui.modbusmobile.core.slave.InMemoryRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.SlaveConfig
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.ConnectionState
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import io.github.xingyingyuzhui.modbusmobile.core.transport.SendResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SlaveScenarioTest {
    @Test
    fun f03_foreignPayloadDoesNotWriteTheBank() = runTest {
        val harness = harness()
        harness.bank.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 1)
        val embedded = f("01 06 00 00 00 07")
        harness.rx(f("02 03 00 00 00 04"))
        harness.rx(f("02 03 08 ${embedded.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }}"))
        runCurrent()
        assertEquals(1, harness.word(0))
        assertTrue(harness.transport.sent.isEmpty())
    }

    @Test
    fun f05_echoedFc06ReplyIsNotAppliedAgain() = runTest {
        val harness = harness(echo = EchoMode.ON)
        harness.bank.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 0)
        harness.rx(f("01 06 00 00 00 2A"))
        runCurrent()
        val reply = harness.transport.sent.single()
        harness.rx(reply)
        runCurrent()
        assertEquals(42, harness.word(0))
        assertEquals(1, harness.transport.sent.size)
    }

    @Test
    fun f06_echoOffAcceptsTwoIdenticalFc06Requests() = runTest {
        val harness = harness(echo = EchoMode.OFF)
        harness.bank.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 0)
        val request = f("01 06 00 00 00 2A")
        harness.rx(request)
        harness.rx(request)
        runCurrent()
        assertEquals(42, harness.word(0))
        assertEquals(2, harness.transport.sent.size)
    }

    @Test
    fun f09_f20_maskWriteBoundaries() = runTest {
        val harness = harness()
        harness.bank.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 4)
        harness.rx(maskWrite(readQty = 1, writeQty = 1, data = byteArrayOf(0x00, 0x2A)))
        runCurrent()
        val small = harness.transport.sent.single()
        assertTrue(small.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }.startsWith("01 97 01"))
        assertTrue(Crc16Modbus.matches(small))
        assertEquals(5, small.size)
        assertEquals(4, harness.word(0))

        harness.transport.sent.clear()
        harness.rx(maskWrite(readQty = 1, writeQty = 121, data = ByteArray(242)))
        runCurrent()
        assertEquals(1, harness.transport.sent.size)
        assertTrue(Crc16Modbus.matches(harness.transport.sent.single()))
        assertEquals(4, harness.word(0))

        harness.transport.sent.clear()
        harness.rx(maskWrite(readQty = 1, writeQty = 1, data = ByteArray(4)))
        runCurrent()
        assertTrue(harness.transport.sent.isEmpty())
        assertEquals(4, harness.word(0))

        harness.rx(maskWrite(readQty = 1, writeQty = 1, data = byteArrayOf(0x00, 0x01)).copyOf(10))
        advanceTimeBy(400)
        runCurrent()
        assertTrue(harness.transport.sent.isEmpty())
        assertEquals(4, harness.word(0))
    }

    @Test
    fun f10_unmodeledFunctionGetsNoExceptionReply() = runTest {
        val harness = harness()
        harness.rx(f("01 41 AA BB"))
        advanceTimeBy(20)
        runCurrent()
        assertTrue(harness.transport.sent.isEmpty())
    }

    @Test
    fun f18_truncatedRequestGetsNoReply() = runTest {
        val harness = harness()
        harness.rx(f("01 03 00 00 00 01").copyOf(5))
        advanceTimeBy(400)
        runCurrent()
        assertTrue(harness.transport.sent.isEmpty())
    }

    @Test
    fun f21_broadcastWritesOnlyMappedUnitsAndDoesNotReply() = runTest {
        val harness = harness(units = setOf(1, 2))
        harness.bank.putWord(1, RegisterArea.HOLDING_REGISTER, 100, 0)
        harness.rx(RtuCodec.encodeRequest(ModbusRequest.WriteSingleRegister(0, 100, 5)))
        harness.rx(RtuCodec.encodeRequest(ModbusRequest.WriteMultipleRegisters(0, 100, listOf(6))))
        runCurrent()
        assertEquals(6, harness.word(100))
        assertTrue(harness.bank.words(2, RegisterArea.HOLDING_REGISTER).isEmpty())
        assertTrue(harness.transport.sent.isEmpty())
    }

    @Test
    fun f23_chunkedEchoDoesNotSwallowTheNextFc06() = runTest {
        val harness = harness(echo = EchoMode.ON)
        harness.bank.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 0)
        val request = f("01 06 00 00 00 2A")
        harness.rx(request)
        runCurrent()
        val reply = harness.transport.sent.single()
        harness.rx(reply.copyOfRange(0, 2))
        harness.rx(reply.copyOfRange(2, 5))
        harness.rx(reply.copyOfRange(5, 8))
        runCurrent()
        assertEquals(1, harness.transport.sent.size)
        harness.rx(request)
        runCurrent()
        assertEquals(2, harness.transport.sent.size)
        assertEquals(42, harness.word(0))
    }

    @Test
    fun f25_untrustedLocalWriteDoesNotTouchTheBank() = runTest {
        val harness = harness()
        harness.bank.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 1)
        val bad = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00)
        harness.rx(bad + f("01 06 00 00 00 07"))
        runCurrent()
        assertEquals(1, harness.word(0))
        assertTrue(harness.transport.sent.isEmpty())
        harness.rx(f("01 06 00 00 00 09"))
        runCurrent()
        assertEquals(9, harness.word(0))
        assertEquals(1, harness.transport.sent.size)
    }

    private fun TestScope.harness(echo: EchoMode = EchoMode.OFF, units: Set<Int> = setOf(1)): Harness {
        val clock = SchedulerMonotonicClock(testScheduler)
        val transport = ScriptedTransport(clock)
        val bank = InMemoryRegisterBank()
        val scanner = ScannerConfig.forSerial(9600, 10, units, 1_000)
        val slave = DefaultSlaveEngine(
            transport = transport,
            bank = bank,
            clock = clock,
            enabledUnits = units,
            config = SlaveConfig(
                topology = BusTopology.MULTIDROP,
                echoMode = echo,
                localUnits = units,
                baud = 9600,
                bitsPerChar = 10,
                scanner = scanner,
            ),
        )
        slave.start(backgroundScope)
        return Harness(transport, bank, slave)
    }

    private class Harness(
        val transport: ScriptedTransport,
        val bank: InMemoryRegisterBank,
        val slave: DefaultSlaveEngine,
    ) {
        suspend fun rx(bytes: ByteArray) = transport.rx(bytes)
        fun word(address: Int): Int =
            bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == address }.value
    }
}

private fun maskWrite(readQty: Int, writeQty: Int, data: ByteArray): ByteArray {
    val payload = ByteArray(11 + data.size)
    payload[0] = 1
    payload[1] = 0x17
    payload[4] = (readQty ushr 8).toByte()
    payload[5] = readQty.toByte()
    payload[8] = (writeQty ushr 8).toByte()
    payload[9] = writeQty.toByte()
    payload[10] = data.size.toByte()
    data.copyInto(payload, 11)
    return Crc16Modbus.append(payload)
}

private class ScriptedTransport(private val clock: MonotonicClock) : ByteTransport {
    private val incoming = Channel<ByteChunk>(capacity = 64)
    private val state = MutableStateFlow(ConnectionState.CONNECTED)
    val sent = mutableListOf<ByteArray>()
    override val received = incoming.receiveAsFlow()
    override val connectionState: StateFlow<ConnectionState> = state
    override val connectionEpoch: Long = 1
    override suspend fun send(frame: ByteArray): SendResult {
        sent += frame.copyOf()
        return SendResult.AcceptedByTransport
    }

    suspend fun rx(bytes: ByteArray) {
        incoming.send(ByteChunk(bytes.copyOf(), clock.nowNs(), connectionEpoch))
    }
}
