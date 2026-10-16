@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.core

import io.github.xingyingyuzhui.modbusmobile.core.master.DefaultMasterEngine
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.rtu.DecodedRequest
import io.github.xingyingyuzhui.modbusmobile.core.rtu.PointToPointScanner
import io.github.xingyingyuzhui.modbusmobile.core.rtu.RtuCodec
import io.github.xingyingyuzhui.modbusmobile.core.rtu.ScanEvent
import io.github.xingyingyuzhui.modbusmobile.core.transport.memory.InMemoryBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class MasterLateResponseTest {
    @Test
    fun f15_lateQuantityTwoDoesNotMatchNextQuantityThree() = runTest {
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
        backgroundScope.launch {
            val scanner = PointToPointScanner()
            scanner.reset(bus.right.connectionEpoch)
            bus.right.received.collect { chunk ->
                for (event in scanner.accept(chunk)) {
                    val raw = (event as? ScanEvent.LocalRequest)?.raw ?: continue
                    val read = (RtuCodec.decodeRequest(raw) as DecodedRequest.Ok).request as ModbusRequest.ReadHoldingRegisters
                    if (read.quantity == 3) {
                        bus.right.send(
                            RtuCodec.encodeResponse(
                                ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(7, 8, 9)),
                            ),
                        )
                    }
                }
            }
        }
        val started = testScheduler.currentTime
        val first = master.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 2))
        assertEquals(ModbusResult.Timeout, first)
        assertTrue(testScheduler.currentTime - started >= 200)
        val remain = 500 - (testScheduler.currentTime - started)
        if (remain > 0) advanceTimeBy(remain)
        runCurrent()
        bus.right.send(
            RtuCodec.encodeResponse(
                ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(11, 22)),
            ),
        )
        runCurrent()
        val second = master.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 3))
        val values = assertIs<ModbusResponse.ReadRegisters>(assertIs<ModbusResult.Success>(second).response).values
        assertEquals(listOf(7, 8, 9), values)
    }
}
