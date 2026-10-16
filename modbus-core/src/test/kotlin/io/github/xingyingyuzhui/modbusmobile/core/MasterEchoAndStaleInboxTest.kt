@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.core

import io.github.xingyingyuzhui.modbusmobile.core.master.DefaultMasterEngine
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.rtu.DecodedRequest
import io.github.xingyingyuzhui.modbusmobile.core.rtu.EchoMode
import io.github.xingyingyuzhui.modbusmobile.core.rtu.EchoTracker
import io.github.xingyingyuzhui.modbusmobile.core.rtu.PointToPointScanner
import io.github.xingyingyuzhui.modbusmobile.core.rtu.RtuCodec
import io.github.xingyingyuzhui.modbusmobile.core.rtu.ScanEvent
import io.github.xingyingyuzhui.modbusmobile.core.transport.memory.InMemoryBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class MasterEchoAndStaleInboxTest {
    @Test
    fun echoOnWithoutSlaveDoesNotTreatAdapterEchoAsWriteSuccess() = runTest {
        val master = echoingMaster(EchoMode.ON)
        val result = master.execute(ModbusRequest.WriteSingleRegister(1, 0, 42))
        assertEquals(ModbusResult.UnknownOutcome, result)
    }

    @Test
    fun echoOffWithoutSlaveMisreadsAdapterEchoAsWriteSuccess() = runTest {
        // 回显关闭时，FC06 的请求和应答字节相同，适配器回显会被误判为成功。这是已知行为。
        val master = echoingMaster(EchoMode.OFF)
        val result = master.execute(ModbusRequest.WriteSingleRegister(1, 0, 42))
        assertIs<ModbusResult.Success>(result)
    }

    @Test
    fun identicalLateResponseIsDroppedBeforeTheNextRequest() = runTest {
        val clock = SchedulerMonotonicClock(testScheduler)
        val bus = InMemoryBus(clock)
        val master = DefaultMasterEngine(
            transport = bus.left,
            clock = clock,
            scope = backgroundScope,
            responseTimeout = 200.milliseconds,
            turnaround = 0.milliseconds,
            interRequestDelay = 20.milliseconds,
        )
        var reads = 0
        backgroundScope.launch {
            val scanner = PointToPointScanner()
            scanner.reset(bus.right.connectionEpoch)
            bus.right.received.collect { chunk ->
                for (event in scanner.accept(chunk)) {
                    if (event !is ScanEvent.LocalRequest) continue
                    val read = (RtuCodec.decodeRequest(event.raw) as DecodedRequest.Ok).request
                        as ModbusRequest.ReadHoldingRegisters
                    reads += 1
                    if (reads == 2 && read.quantity == 2) {
                        bus.right.send(
                            RtuCodec.encodeResponse(
                                ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(7, 8)),
                            ),
                        )
                    }
                }
            }
        }
        assertEquals(ModbusResult.Timeout, master.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 2)))
        bus.right.send(
            RtuCodec.encodeResponse(
                ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(11, 22)),
            ),
        )
        runCurrent()
        val second = master.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 2))
        val values = assertIs<ModbusResponse.ReadRegisters>(assertIs<ModbusResult.Success>(second).response).values
        assertEquals(listOf(7, 8), values)
    }

    private fun TestScope.echoingMaster(mode: EchoMode): DefaultMasterEngine {
        val clock = SchedulerMonotonicClock(testScheduler)
        val bus = InMemoryBus(clock, echo = true)
        return DefaultMasterEngine(
            transport = bus.left,
            clock = clock,
            scope = backgroundScope,
            responseTimeout = 200.milliseconds,
            turnaround = 0.milliseconds,
            interRequestDelay = 0.milliseconds,
            echo = EchoTracker(
                mode = mode,
                wireNs = { 0L },
                slackNs = Long.MAX_VALUE / 4,
            ),
        )
    }
}
