package io.github.xingyingyuzhui.modbusmobile.core.rtu

import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse

sealed interface DecodedRequest {
    data class Ok(val request: ModbusRequest) : DecodedRequest
    data class ExceptionReply(val unitId: Int, val function: FunctionCode, val code: ExceptionCode) : DecodedRequest
    data object BroadcastRead : DecodedRequest
    data object NotForUs : DecodedRequest
    data object BadCrc : DecodedRequest
    data object Invalid : DecodedRequest
}

sealed interface DecodedResponse {
    data class Ok(val response: ModbusResponse) : DecodedResponse
    data object BadCrc : DecodedResponse
    data object Invalid : DecodedResponse
}

object RtuCodec {
    fun encodeRequest(request: ModbusRequest): ByteArray {
        val payload = FrameWriter()
        payload.u8(request.unitId)
        payload.u8(request.function.code)
        when (request) {
            is ModbusRequest.ReadCoils,
            is ModbusRequest.ReadDiscreteInputs,
            is ModbusRequest.ReadHoldingRegisters,
            is ModbusRequest.ReadInputRegisters,
            -> {
                payload.u16(request.readAddress)
                payload.u16(request.readQuantity)
            }
            is ModbusRequest.WriteSingleCoil -> {
                payload.u16(request.address)
                payload.u16(if (request.value) 0xFF00 else 0x0000)
            }
            is ModbusRequest.WriteSingleRegister -> {
                payload.u16(request.address)
                payload.u16(request.value)
            }
            is ModbusRequest.WriteMultipleCoils -> {
                payload.u16(request.address)
                payload.u16(request.quantity)
                val packed = packBits(request.values)
                payload.u8(packed.size)
                payload.bytes(packed)
            }
            is ModbusRequest.WriteMultipleRegisters -> {
                payload.u16(request.address)
                payload.u16(request.quantity)
                payload.u8(request.quantity * 2)
                request.values.forEach(payload::u16)
            }
        }
        return Crc16Modbus.append(payload.toByteArray())
    }

    fun encodeResponse(response: ModbusResponse): ByteArray {
        val payload = FrameWriter()
        payload.u8(response.unitId)
        when (response) {
            is ModbusResponse.ExceptionFrame -> {
                payload.u8(response.function.code or 0x80)
                payload.u8(response.code.code)
            }
            is ModbusResponse.ReadBits -> {
                payload.u8(response.function.code)
                val packed = packBits(response.values)
                payload.u8(packed.size)
                payload.bytes(packed)
            }
            is ModbusResponse.ReadRegisters -> {
                payload.u8(response.function.code)
                payload.u8(response.values.size * 2)
                response.values.forEach(payload::u16)
            }
            is ModbusResponse.WriteSingleCoil -> {
                payload.u8(response.function.code)
                payload.u16(response.address)
                payload.u16(if (response.value) 0xFF00 else 0x0000)
            }
            is ModbusResponse.WriteSingleRegister -> {
                payload.u8(response.function.code)
                payload.u16(response.address)
                payload.u16(response.value)
            }
            is ModbusResponse.WriteMultipleCoils -> {
                payload.u8(response.function.code)
                payload.u16(response.address)
                payload.u16(response.quantity)
            }
            is ModbusResponse.WriteMultipleRegisters -> {
                payload.u8(response.function.code)
                payload.u16(response.address)
                payload.u16(response.quantity)
            }
        }
        return Crc16Modbus.append(payload.toByteArray())
    }

    fun decodeRequest(frame: ByteArray): DecodedRequest {
        if (frame.size < 4) return DecodedRequest.Invalid
        if (!Crc16Modbus.matches(frame)) return DecodedRequest.BadCrc
        val unit = frame.u8(0)
        if (unit > 247) return DecodedRequest.NotForUs
        val function = FunctionCode.fromCode(frame.u8(1)) ?: return DecodedRequest.NotForUs
        return when (function) {
            FunctionCode.READ_COILS,
            FunctionCode.READ_DISCRETE_INPUTS,
            FunctionCode.READ_HOLDING_REGISTERS,
            FunctionCode.READ_INPUT_REGISTERS,
            -> decodeReadRequest(frame, unit, function)
            FunctionCode.WRITE_SINGLE_COIL -> decodeWriteCoil(frame, unit)
            FunctionCode.WRITE_SINGLE_REGISTER -> decodeWriteRegister(frame, unit)
            FunctionCode.WRITE_MULTIPLE_COILS -> decodeWriteCoils(frame, unit)
            FunctionCode.WRITE_MULTIPLE_REGISTERS -> decodeWriteRegisters(frame, unit)
        }
    }

    fun decodeResponse(frame: ByteArray): DecodedResponse {
        if (frame.size < 5) return DecodedResponse.Invalid
        if (!Crc16Modbus.matches(frame)) return DecodedResponse.BadCrc
        val unit = frame.u8(0)
        val rawFunction = frame.u8(1)
        if ((rawFunction and 0x80) != 0) {
            if (frame.size != 5) return DecodedResponse.Invalid
            val function = FunctionCode.fromCode(rawFunction and 0x7F) ?: return DecodedResponse.Invalid
            val code = ExceptionCode.fromCode(frame.u8(2)) ?: return DecodedResponse.Invalid
            return DecodedResponse.Ok(ModbusResponse.ExceptionFrame(unit, function, code))
        }
        val function = FunctionCode.fromCode(rawFunction) ?: return DecodedResponse.Invalid
        return when (function) {
            FunctionCode.READ_COILS, FunctionCode.READ_DISCRETE_INPUTS -> decodeReadBits(frame, unit, function)
            FunctionCode.READ_HOLDING_REGISTERS, FunctionCode.READ_INPUT_REGISTERS -> decodeReadWords(frame, unit, function)
            FunctionCode.WRITE_SINGLE_COIL -> decodeWriteCoilResponse(frame, unit, function)
            FunctionCode.WRITE_SINGLE_REGISTER -> decodeWriteRegisterResponse(frame, unit, function)
            FunctionCode.WRITE_MULTIPLE_COILS -> decodeWriteMultiResponse(frame, unit, function, bits = true)
            FunctionCode.WRITE_MULTIPLE_REGISTERS -> decodeWriteMultiResponse(frame, unit, function, bits = false)
        }
    }

    private fun decodeReadRequest(frame: ByteArray, unit: Int, function: FunctionCode): DecodedRequest {
        if (frame.size != 8) return DecodedRequest.Invalid
        val address = frame.u16(2)
        val quantity = frame.u16(4)
        val max = if (function.isBit) ModbusRequest.MAX_READ_BITS else ModbusRequest.MAX_READ_REGISTERS
        if (quantity !in 1..max || address.toLong() + quantity - 1 > 0xFFFFL) {
            return DecodedRequest.ExceptionReply(unit, function, ExceptionCode.ILLEGAL_DATA_VALUE)
        }
        if (unit == 0) return DecodedRequest.BroadcastRead
        val request = when (function) {
            FunctionCode.READ_COILS -> ModbusRequest.ReadCoils(unit, address, quantity)
            FunctionCode.READ_DISCRETE_INPUTS -> ModbusRequest.ReadDiscreteInputs(unit, address, quantity)
            FunctionCode.READ_HOLDING_REGISTERS -> ModbusRequest.ReadHoldingRegisters(unit, address, quantity)
            FunctionCode.READ_INPUT_REGISTERS -> ModbusRequest.ReadInputRegisters(unit, address, quantity)
            else -> return DecodedRequest.Invalid
        }
        return DecodedRequest.Ok(request)
    }

    private fun decodeWriteCoil(frame: ByteArray, unit: Int): DecodedRequest {
        if (frame.size != 8) return DecodedRequest.Invalid
        val address = frame.u16(2)
        val raw = frame.u16(4)
        val value = when (raw) {
            0xFF00 -> true
            0x0000 -> false
            else -> return DecodedRequest.ExceptionReply(unit, FunctionCode.WRITE_SINGLE_COIL, ExceptionCode.ILLEGAL_DATA_VALUE)
        }
        if (address > 0xFFFF) return DecodedRequest.ExceptionReply(unit, FunctionCode.WRITE_SINGLE_COIL, ExceptionCode.ILLEGAL_DATA_VALUE)
        return DecodedRequest.Ok(ModbusRequest.WriteSingleCoil(unit, address, value))
    }

    private fun decodeWriteRegister(frame: ByteArray, unit: Int): DecodedRequest {
        if (frame.size != 8) return DecodedRequest.Invalid
        val address = frame.u16(2)
        val value = frame.u16(4)
        return DecodedRequest.Ok(ModbusRequest.WriteSingleRegister(unit, address, value))
    }

    private fun decodeWriteCoils(frame: ByteArray, unit: Int): DecodedRequest {
        if (frame.size < 9) return DecodedRequest.Invalid
        val address = frame.u16(2)
        val quantity = frame.u16(4)
        val byteCount = frame.u8(6)
        val function = FunctionCode.WRITE_MULTIPLE_COILS
        if (quantity !in 1..ModbusRequest.MAX_WRITE_COILS || address.toLong() + quantity - 1 > 0xFFFFL) {
            return DecodedRequest.ExceptionReply(unit, function, ExceptionCode.ILLEGAL_DATA_VALUE)
        }
        val expectedBytes = (quantity + 7) / 8
        if (byteCount != expectedBytes || frame.size != 9 + byteCount) {
            return DecodedRequest.ExceptionReply(unit, function, ExceptionCode.ILLEGAL_DATA_VALUE)
        }
        val values = unpackBits(frame.copyOfRange(7, 7 + byteCount), quantity)
        return DecodedRequest.Ok(ModbusRequest.WriteMultipleCoils(unit, address, values))
    }

    private fun decodeWriteRegisters(frame: ByteArray, unit: Int): DecodedRequest {
        if (frame.size < 9) return DecodedRequest.Invalid
        val address = frame.u16(2)
        val quantity = frame.u16(4)
        val byteCount = frame.u8(6)
        val function = FunctionCode.WRITE_MULTIPLE_REGISTERS
        if (quantity !in 1..ModbusRequest.MAX_WRITE_REGISTERS || address.toLong() + quantity - 1 > 0xFFFFL) {
            return DecodedRequest.ExceptionReply(unit, function, ExceptionCode.ILLEGAL_DATA_VALUE)
        }
        if (byteCount != quantity * 2 || frame.size != 9 + byteCount) {
            return DecodedRequest.ExceptionReply(unit, function, ExceptionCode.ILLEGAL_DATA_VALUE)
        }
        val values = (0 until quantity).map { frame.u16(7 + it * 2) }
        return DecodedRequest.Ok(ModbusRequest.WriteMultipleRegisters(unit, address, values))
    }

    private fun decodeReadBits(frame: ByteArray, unit: Int, function: FunctionCode): DecodedResponse {
        val byteCount = frame.u8(2)
        if (frame.size != 5 + byteCount) return DecodedResponse.Invalid
        val values = unpackBits(frame.copyOfRange(3, 3 + byteCount), byteCount * 8)
        return DecodedResponse.Ok(ModbusResponse.ReadBits(unit, function, values))
    }

    private fun decodeReadWords(frame: ByteArray, unit: Int, function: FunctionCode): DecodedResponse {
        val byteCount = frame.u8(2)
        if (byteCount % 2 != 0 || frame.size != 5 + byteCount) return DecodedResponse.Invalid
        val values = (0 until byteCount / 2).map { frame.u16(3 + it * 2) }
        return DecodedResponse.Ok(ModbusResponse.ReadRegisters(unit, function, values))
    }

    private fun decodeWriteCoilResponse(frame: ByteArray, unit: Int, function: FunctionCode): DecodedResponse {
        if (frame.size != 8) return DecodedResponse.Invalid
        val raw = frame.u16(4)
        val value = when (raw) {
            0xFF00 -> true
            0x0000 -> false
            else -> return DecodedResponse.Invalid
        }
        return DecodedResponse.Ok(ModbusResponse.WriteSingleCoil(unit, function, frame.u16(2), value))
    }

    private fun decodeWriteRegisterResponse(frame: ByteArray, unit: Int, function: FunctionCode): DecodedResponse {
        if (frame.size != 8) return DecodedResponse.Invalid
        return DecodedResponse.Ok(ModbusResponse.WriteSingleRegister(unit, function, frame.u16(2), frame.u16(4)))
    }

    private fun decodeWriteMultiResponse(
        frame: ByteArray,
        unit: Int,
        function: FunctionCode,
        bits: Boolean,
    ): DecodedResponse {
        if (frame.size != 8) return DecodedResponse.Invalid
        val address = frame.u16(2)
        val quantity = frame.u16(4)
        val response = if (bits) {
            ModbusResponse.WriteMultipleCoils(unit, function, address, quantity)
        } else {
            ModbusResponse.WriteMultipleRegisters(unit, function, address, quantity)
        }
        return DecodedResponse.Ok(response)
    }
}

private val ModbusRequest.readAddress: Int
    get() = when (this) {
        is ModbusRequest.ReadCoils -> address
        is ModbusRequest.ReadDiscreteInputs -> address
        is ModbusRequest.ReadHoldingRegisters -> address
        is ModbusRequest.ReadInputRegisters -> address
        else -> error("not a read")
    }

private val ModbusRequest.readQuantity: Int
    get() = when (this) {
        is ModbusRequest.ReadCoils -> quantity
        is ModbusRequest.ReadDiscreteInputs -> quantity
        is ModbusRequest.ReadHoldingRegisters -> quantity
        is ModbusRequest.ReadInputRegisters -> quantity
        else -> error("not a read")
    }

internal fun packBits(values: List<Boolean>): ByteArray {
    val bytes = ByteArray((values.size + 7) / 8)
    values.forEachIndexed { index, bit ->
        if (bit) {
            bytes[index / 8] = (bytes[index / 8].toInt() or (1 shl (index % 8))).toByte()
        }
    }
    return bytes
}

internal fun unpackBits(data: ByteArray, quantity: Int): List<Boolean> = List(quantity) { index ->
    val byte = data.getOrElse(index / 8) { 0 }
    ((byte.toInt() and 0xFF) shr (index % 8) and 0x01) == 1
}

private class FrameWriter {
    private val out = ArrayList<Byte>()
    fun u8(value: Int) { out += value.toByte() }
    fun u16(value: Int) {
        out += (value ushr 8).toByte()
        out += value.toByte()
    }
    fun bytes(value: ByteArray) { value.forEach { out += it } }
    fun toByteArray(): ByteArray = out.toByteArray()
}

private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

private fun ByteArray.u16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)
