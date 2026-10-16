package io.github.xingyingyuzhui.modbusmobile.core.model

enum class Role {
    MASTER,
    SLAVE,
}

enum class RegisterArea {
    COIL,
    DISCRETE_INPUT,
    INPUT_REGISTER,
    HOLDING_REGISTER,
}

enum class FunctionCode(val code: Int) {
    READ_COILS(0x01),
    READ_DISCRETE_INPUTS(0x02),
    READ_HOLDING_REGISTERS(0x03),
    READ_INPUT_REGISTERS(0x04),
    WRITE_SINGLE_COIL(0x05),
    WRITE_SINGLE_REGISTER(0x06),
    WRITE_MULTIPLE_COILS(0x0F),
    WRITE_MULTIPLE_REGISTERS(0x10),
    ;

    val isRead: Boolean
        get() = this == READ_COILS || this == READ_DISCRETE_INPUTS ||
            this == READ_HOLDING_REGISTERS || this == READ_INPUT_REGISTERS

    val isBit: Boolean
        get() = this == READ_COILS || this == READ_DISCRETE_INPUTS || this == WRITE_SINGLE_COIL ||
            this == WRITE_MULTIPLE_COILS

    companion object {
        fun fromCode(code: Int): FunctionCode? = entries.firstOrNull { it.code == code }
    }
}

@JvmInline
value class ExceptionCode(val code: Int) {
    init {
        require(code in 1..255) { "异常码超出 1..255" }
    }

    companion object {
        val ILLEGAL_FUNCTION = ExceptionCode(0x01)
        val ILLEGAL_DATA_ADDRESS = ExceptionCode(0x02)
        val ILLEGAL_DATA_VALUE = ExceptionCode(0x03)
        val SLAVE_DEVICE_FAILURE = ExceptionCode(0x04)
        val ACKNOWLEDGE = ExceptionCode(0x05)
        val SLAVE_DEVICE_BUSY = ExceptionCode(0x06)
        val GATEWAY_PATH_UNAVAILABLE = ExceptionCode(0x0A)
        val GATEWAY_TARGET_FAILED = ExceptionCode(0x0B)
    }
}

enum class TrafficDirection {
    TX,
    RX,
}

enum class TrafficKind {
    TX_REQUEST,
    TX_RESPONSE,
    RX_LOCAL_REQUEST,
    RX_FOREIGN_REQUEST,
    RX_FOREIGN_RESPONSE,
    RX_RESPONSE,
    ECHO,
    AMBIGUOUS,
    DROPPED,
}

class TrafficEvent(
    val monotonicNs: Long,
    val direction: TrafficDirection,
    val bytes: ByteArray,
    val kind: TrafficKind,
) {
    val hex: String = bytes.toHex()
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

sealed class ModbusRequest {
    abstract val unitId: Int
    abstract val function: FunctionCode

    val isWrite: Boolean get() = !function.isRead
    val isBroadcast: Boolean get() = unitId == 0

    data class ReadCoils(override val unitId: Int, val address: Int, val quantity: Int) : ModbusRequest() {
        override val function = FunctionCode.READ_COILS
        init { validateRead(unitId, address, quantity, MAX_READ_BITS) }
    }

    data class ReadDiscreteInputs(override val unitId: Int, val address: Int, val quantity: Int) : ModbusRequest() {
        override val function = FunctionCode.READ_DISCRETE_INPUTS
        init { validateRead(unitId, address, quantity, MAX_READ_BITS) }
    }

    data class ReadHoldingRegisters(override val unitId: Int, val address: Int, val quantity: Int) : ModbusRequest() {
        override val function = FunctionCode.READ_HOLDING_REGISTERS
        init { validateRead(unitId, address, quantity, MAX_READ_REGISTERS) }
    }

    data class ReadInputRegisters(override val unitId: Int, val address: Int, val quantity: Int) : ModbusRequest() {
        override val function = FunctionCode.READ_INPUT_REGISTERS
        init { validateRead(unitId, address, quantity, MAX_READ_REGISTERS) }
    }

    data class WriteSingleCoil(override val unitId: Int, val address: Int, val value: Boolean) : ModbusRequest() {
        override val function = FunctionCode.WRITE_SINGLE_COIL
        init {
            validateUnit(unitId, allowBroadcast = true)
            validateAddress(address)
        }
    }

    data class WriteSingleRegister(override val unitId: Int, val address: Int, val value: Int) : ModbusRequest() {
        override val function = FunctionCode.WRITE_SINGLE_REGISTER
        init {
            validateUnit(unitId, allowBroadcast = true)
            validateAddress(address)
            require(value in 0..0xFFFF) { "寄存器值超出 0..65535" }
        }
    }

    data class WriteMultipleCoils(override val unitId: Int, val address: Int, val values: List<Boolean>) : ModbusRequest() {
        override val function = FunctionCode.WRITE_MULTIPLE_COILS
        val quantity: Int = values.size
        init {
            validateUnit(unitId, allowBroadcast = true)
            require(quantity in 1..MAX_WRITE_COILS) { "线圈数量超出 1..$MAX_WRITE_COILS" }
            validateAddress(address)
            validateSpan(address, quantity)
        }
    }

    data class WriteMultipleRegisters(override val unitId: Int, val address: Int, val values: List<Int>) : ModbusRequest() {
        override val function = FunctionCode.WRITE_MULTIPLE_REGISTERS
        val quantity: Int = values.size
        init {
            validateUnit(unitId, allowBroadcast = true)
            require(quantity in 1..MAX_WRITE_REGISTERS) { "寄存器数量超出 1..$MAX_WRITE_REGISTERS" }
            require(values.all { it in 0..0xFFFF }) { "寄存器值超出 0..65535" }
            validateAddress(address)
            validateSpan(address, quantity)
        }
    }

    companion object {
        const val MAX_READ_BITS = 2000
        const val MAX_READ_REGISTERS = 125
        const val MAX_WRITE_COILS = 1968
        const val MAX_WRITE_REGISTERS = 123
    }
}

sealed interface ModbusResponse {
    val unitId: Int

    data class ReadBits(
        override val unitId: Int,
        val function: FunctionCode,
        val values: List<Boolean>,
    ) : ModbusResponse

    data class ReadRegisters(
        override val unitId: Int,
        val function: FunctionCode,
        val values: List<Int>,
    ) : ModbusResponse

    data class WriteSingleCoil(
        override val unitId: Int,
        val function: FunctionCode,
        val address: Int,
        val value: Boolean,
    ) : ModbusResponse

    data class WriteSingleRegister(
        override val unitId: Int,
        val function: FunctionCode,
        val address: Int,
        val value: Int,
    ) : ModbusResponse

    data class WriteMultipleCoils(
        override val unitId: Int,
        val function: FunctionCode,
        val address: Int,
        val quantity: Int,
    ) : ModbusResponse

    data class WriteMultipleRegisters(
        override val unitId: Int,
        val function: FunctionCode,
        val address: Int,
        val quantity: Int,
    ) : ModbusResponse

    /** [functionCode] 是原始功能码。0x17 不在 [FunctionCode] 里，异常帧仍要能表示它。 */
    data class ExceptionFrame(
        override val unitId: Int,
        val functionCode: Int,
        val code: ExceptionCode,
    ) : ModbusResponse
}

sealed interface ModbusResult {
    data class Success(val response: ModbusResponse) : ModbusResult
    data class Exception(val code: ExceptionCode) : ModbusResult
    data object Timeout : ModbusResult
    data object UnknownOutcome : ModbusResult
    data object Disconnected : ModbusResult
    data class InvalidResponse(val detail: String) : ModbusResult
}

private fun validateRead(unitId: Int, address: Int, quantity: Int, maxQuantity: Int) {
    validateUnit(unitId, allowBroadcast = false)
    require(quantity in 1..maxQuantity) { "数量超出 1..$maxQuantity" }
    validateAddress(address)
    validateSpan(address, quantity)
}

private fun validateUnit(unitId: Int, allowBroadcast: Boolean) {
    require(unitId in 0..247) { "Unit ID 超出 0..247" }
    require(allowBroadcast || unitId != 0) { "读请求不能使用广播地址" }
}

private fun validateAddress(address: Int) {
    require(address in 0..0xFFFF) { "地址超出 0..65535" }
}

private fun validateSpan(address: Int, quantity: Int) {
    require(address.toLong() + quantity - 1 <= 0xFFFFL) { "地址加数量超出 65535" }
}
