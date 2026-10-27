package io.github.xingyingyuzhui.modbusmobile.application

import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCountersTest {
    private fun event(kind: TrafficKind, direction: TrafficDirection, vararg bytes: Int) =
        TrafficEvent(0L, direction, ByteArray(bytes.size) { bytes[it].toByte() }, kind)

    private val request = intArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x0A, 0xC5, 0xCD)
    private val exception = intArrayOf(0x01, 0x83, 0x02, 0xC0, 0xF1)

    @Test
    fun masterCountsTxAndRxFrames() {
        val counters = SessionCounters()
            .count(event(TrafficKind.TX_REQUEST, TrafficDirection.TX, *request))
            .count(event(TrafficKind.RX_RESPONSE, TrafficDirection.RX, *request))
        assertEquals(1, counters.tx)
        assertEquals(1, counters.rx)
        assertEquals(0, counters.errors)
    }

    @Test
    fun echoIsNotCountedAsReceived() {
        val counters = SessionCounters().count(event(TrafficKind.ECHO, TrafficDirection.RX, *request))
        assertEquals(0, counters.rx)
        assertEquals(1, counters.echoes)
    }

    @Test
    fun masterErrorsComeFromTransactionResults() {
        val ok = ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(1)))
        val counters = SessionCounters()
            .countMasterResult(ok)
            .countMasterResult(ModbusResult.Timeout)
            .countMasterResult(ModbusResult.Exception(ExceptionCode.ILLEGAL_DATA_ADDRESS))
            .countMasterResult(ModbusResult.UnknownOutcome)
        assertEquals("成功不算错误，超时 / 异常 / 结果未知各算一次", 3, counters.errors)
    }

    @Test
    fun slaveStatsSplitLocalForeignAndExceptions() {
        val counters = SessionCounters()
            .count(event(TrafficKind.RX_LOCAL_REQUEST, TrafficDirection.RX, *request))
            .count(event(TrafficKind.TX_RESPONSE, TrafficDirection.TX, *request))
            .count(event(TrafficKind.RX_LOCAL_REQUEST, TrafficDirection.RX, *request))
            .count(event(TrafficKind.TX_RESPONSE, TrafficDirection.TX, *exception))
            .count(event(TrafficKind.RX_FOREIGN_REQUEST, TrafficDirection.RX, *request))
            .count(event(TrafficKind.RX_FOREIGN_RESPONSE, TrafficDirection.RX, *request))
            .count(event(TrafficKind.AMBIGUOUS, TrafficDirection.RX, *request))
        assertEquals(2, counters.localRequests)
        assertEquals("已响应包含异常响应", 2, counters.responses)
        assertEquals(1, counters.exceptions)
        assertEquals(2, counters.foreignFrames)
        assertEquals(1, counters.ambiguous)
        assertEquals("异常响应 + 方向不明", 2, counters.errors)
        assertEquals(2, counters.tx)
        assertEquals(5, counters.rx)
    }
}
