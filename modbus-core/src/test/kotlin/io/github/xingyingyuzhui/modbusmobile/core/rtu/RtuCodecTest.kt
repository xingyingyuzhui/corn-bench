package io.github.xingyingyuzhui.modbusmobile.core.rtu

import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RtuCodecTest {
    @Test
    fun requestsRoundTrip() {
        val requests = listOf(
            ModbusRequest.ReadCoils(1, 10, 8),
            ModbusRequest.ReadDiscreteInputs(2, 0, 1),
            ModbusRequest.ReadHoldingRegisters(1, 0, 10),
            ModbusRequest.ReadInputRegisters(3, 20, 2),
            ModbusRequest.WriteSingleCoil(1, 4, true),
            ModbusRequest.WriteSingleCoil(1, 4, false),
            ModbusRequest.WriteSingleRegister(1, 0, 0x1234),
            ModbusRequest.WriteMultipleCoils(1, 0, listOf(true, false, true, true, false, false, true, false, true, false)),
            ModbusRequest.WriteMultipleRegisters(1, 5, listOf(1, 2, 3)),
        )
        requests.forEach { request ->
            val decoded = RtuCodec.decodeRequest(RtuCodec.encodeRequest(request))
            assertEquals(DecodedRequest.Ok(request), decoded, request.function.name)
        }
    }

    @Test
    fun responsesRoundTrip() {
        val responses = listOf(
            ModbusResponse.ReadBits(1, FunctionCode.READ_COILS, List(8) { it % 2 == 0 }),
            ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(0, 10, 20)),
            ModbusResponse.WriteSingleCoil(1, FunctionCode.WRITE_SINGLE_COIL, 4, true),
            ModbusResponse.WriteSingleRegister(1, FunctionCode.WRITE_SINGLE_REGISTER, 0, 123),
            ModbusResponse.WriteMultipleCoils(1, FunctionCode.WRITE_MULTIPLE_COILS, 0, 10),
            ModbusResponse.WriteMultipleRegisters(1, FunctionCode.WRITE_MULTIPLE_REGISTERS, 5, 3),
            ModbusResponse.ExceptionFrame(1, FunctionCode.READ_HOLDING_REGISTERS.code, ExceptionCode.ILLEGAL_DATA_ADDRESS),
        )
        responses.forEach { response ->
            val decoded = RtuCodec.decodeResponse(RtuCodec.encodeResponse(response))
            assertEquals(DecodedResponse.Ok(response), decoded)
        }
    }

    @Test
    fun illegalAddressExceptionIsFiveBytes() {
        val frame = RtuCodec.encodeResponse(
            ModbusResponse.ExceptionFrame(1, FunctionCode.READ_HOLDING_REGISTERS.code, ExceptionCode.ILLEGAL_DATA_ADDRESS),
        )
        assertEquals("01 83 02 C0 F1", frame.toHex())
        val decoded = RtuCodec.decodeResponse(frame)
        val response = assertIs<DecodedResponse.Ok>(decoded).response
        val exception = assertIs<ModbusResponse.ExceptionFrame>(response)
        assertEquals(ExceptionCode.ILLEGAL_DATA_ADDRESS, exception.code)
        assertEquals(FunctionCode.READ_HOLDING_REGISTERS.code, exception.functionCode)
    }

    @Test
    fun unknownExceptionCodeDecodes() {
        val frame = Crc16Modbus.append(byteArrayOf(0x01, 0x83.toByte(), 0x04))
        val exception = assertIs<ModbusResponse.ExceptionFrame>(
            assertIs<DecodedResponse.Ok>(RtuCodec.decodeResponse(frame)).response,
        )
        assertEquals(ExceptionCode.SLAVE_DEVICE_FAILURE, exception.code)
        assertEquals(0x03, exception.functionCode)
        val zero = Crc16Modbus.append(byteArrayOf(0x01, 0x83.toByte(), 0x00))
        assertEquals(DecodedResponse.Invalid, RtuCodec.decodeResponse(zero))
    }

    @Test
    fun quantityIsCheckedBeforeAddressSpan() {
        val span = RtuCodec.decodeRequest(Crc16Modbus.append(byteArrayOf(0x01, 0x03, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0x02)))
        val spanReply = assertIs<DecodedRequest.ExceptionReply>(span)
        assertEquals(ExceptionCode.ILLEGAL_DATA_ADDRESS, spanReply.code)
        val quantity = RtuCodec.decodeRequest(Crc16Modbus.append(byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x7E)))
        val quantityReply = assertIs<DecodedRequest.ExceptionReply>(quantity)
        assertEquals(ExceptionCode.ILLEGAL_DATA_VALUE, quantityReply.code)
        val both = RtuCodec.decodeRequest(Crc16Modbus.append(byteArrayOf(0x01, 0x10, 0xFF.toByte(), 0xFF.toByte(), 0x00, 0x02, 0x03, 0x00, 0x01)))
        val bothReply = assertIs<DecodedRequest.ExceptionReply>(both)
        assertEquals(ExceptionCode.ILLEGAL_DATA_VALUE, bothReply.code)
    }

    @Test
    fun quantityOutOfRangeIsRejectedAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            ModbusRequest.ReadHoldingRegisters(1, 0, 126)
        }
        assertFailsWith<IllegalArgumentException> {
            ModbusRequest.ReadCoils(1, 0, 2001)
        }
        assertFailsWith<IllegalArgumentException> {
            ModbusRequest.ReadHoldingRegisters(0, 0, 1)
        }
    }
}
