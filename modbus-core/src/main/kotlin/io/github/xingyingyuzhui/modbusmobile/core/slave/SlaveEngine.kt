package io.github.xingyingyuzhui.modbusmobile.core.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.rtu.DecodedRequest
import io.github.xingyingyuzhui.modbusmobile.core.rtu.PointToPointScanner
import io.github.xingyingyuzhui.modbusmobile.core.rtu.RtuCodec
import io.github.xingyingyuzhui.modbusmobile.core.rtu.ScanEvent
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

interface SlaveEngine {
    val traffic: SharedFlow<TrafficEvent>
    fun start(scope: CoroutineScope)
    fun close()
}

class DefaultSlaveEngine(
    private val transport: ByteTransport,
    private val bank: VirtualRegisterBank,
    private val clock: MonotonicClock,
    private val enabledUnits: Set<Int>,
    private val suppressEcho: Boolean = false,
    private val onTraffic: (TrafficEvent) -> Unit = {},
) : SlaveEngine {
    private val scanner = PointToPointScanner()
    private val events = MutableSharedFlow<TrafficEvent>(replay = 64, extraBufferCapacity = 64)
    private var job: Job? = null

    override val traffic: SharedFlow<TrafficEvent> = events.asSharedFlow()

    override fun start(scope: CoroutineScope) {
        check(job == null) { "SlaveEngine 已经启动" }
        scanner.reset(transport.connectionEpoch)
        scanner.expectRequests()
        job = scope.launch {
            transport.received.collect { chunk ->
                if (chunk.connectionEpoch != transport.connectionEpoch) return@collect
                for (event in scanner.accept(chunk)) {
                    if (event is ScanEvent.Request) handle(event.raw)
                }
            }
        }
    }

    override fun close() {
        job?.cancel()
        job = null
    }

    private suspend fun handle(raw: ByteArray) {
        publish(TrafficDirection.RX, raw)
        when (val decoded = RtuCodec.decodeRequest(raw)) {
            is DecodedRequest.Ok -> {
                val response = process(decoded.request)
                if (response != null) send(response)
            }
            is DecodedRequest.ExceptionReply -> {
                if (decoded.unitId == 0 || decoded.unitId !in enabledUnits) return
                send(ModbusResponse.ExceptionFrame(decoded.unitId, decoded.function, decoded.code))
            }
            DecodedRequest.BroadcastRead,
            DecodedRequest.NotForUs,
            DecodedRequest.BadCrc,
            DecodedRequest.Invalid,
            -> Unit
        }
    }

    private fun process(request: ModbusRequest): ModbusResponse? {
        if (request.isBroadcast) {
            if (request.isWrite) {
                enabledUnits.toList().forEach { unitId -> applyWrite(unitId, request) }
            }
            return null
        }
        if (request.unitId !in enabledUnits) return null
        return when (request) {
            is ModbusRequest.ReadCoils -> readBits(request.unitId, RegisterArea.COIL, request.address, request.quantity, request.function)
            is ModbusRequest.ReadDiscreteInputs -> {
                readBits(request.unitId, RegisterArea.DISCRETE_INPUT, request.address, request.quantity, request.function)
            }
            is ModbusRequest.ReadHoldingRegisters -> {
                readWords(request.unitId, RegisterArea.HOLDING_REGISTER, request.address, request.quantity, request.function)
            }
            is ModbusRequest.ReadInputRegisters -> {
                readWords(request.unitId, RegisterArea.INPUT_REGISTER, request.address, request.quantity, request.function)
            }
            is ModbusRequest.WriteSingleCoil -> writeCoil(request)
            is ModbusRequest.WriteSingleRegister -> writeRegister(request)
            is ModbusRequest.WriteMultipleCoils -> writeCoils(request)
            is ModbusRequest.WriteMultipleRegisters -> writeRegisters(request)
        }
    }

    private fun applyWrite(unitId: Int, request: ModbusRequest) {
        when (request) {
            is ModbusRequest.WriteSingleCoil -> bank.writeBits(unitId, RegisterArea.COIL, request.address, listOf(request.value))
            is ModbusRequest.WriteSingleRegister -> {
                bank.writeWords(unitId, RegisterArea.HOLDING_REGISTER, request.address, listOf(request.value))
            }
            is ModbusRequest.WriteMultipleCoils -> bank.writeBits(unitId, RegisterArea.COIL, request.address, request.values)
            is ModbusRequest.WriteMultipleRegisters -> {
                bank.writeWords(unitId, RegisterArea.HOLDING_REGISTER, request.address, request.values)
            }
            else -> Unit
        }
    }

    private fun readBits(
        unitId: Int,
        area: RegisterArea,
        address: Int,
        quantity: Int,
        function: FunctionCode,
    ): ModbusResponse = when (val read = bank.readBits(unitId, area, address, quantity)) {
        is BankAccess.Ok -> ModbusResponse.ReadBits(unitId, function, read.value)
        is BankAccess.Fail -> ModbusResponse.ExceptionFrame(unitId, function, read.code)
    }

    private fun readWords(
        unitId: Int,
        area: RegisterArea,
        address: Int,
        quantity: Int,
        function: FunctionCode,
    ): ModbusResponse = when (val read = bank.readWords(unitId, area, address, quantity)) {
        is BankAccess.Ok -> ModbusResponse.ReadRegisters(unitId, function, read.value)
        is BankAccess.Fail -> ModbusResponse.ExceptionFrame(unitId, function, read.code)
    }

    private fun writeCoil(request: ModbusRequest.WriteSingleCoil): ModbusResponse {
        val code = bank.writeBits(request.unitId, RegisterArea.COIL, request.address, listOf(request.value))
        return if (code == null) {
            ModbusResponse.WriteSingleCoil(request.unitId, request.function, request.address, request.value)
        } else {
            ModbusResponse.ExceptionFrame(request.unitId, request.function, code)
        }
    }

    private fun writeRegister(request: ModbusRequest.WriteSingleRegister): ModbusResponse {
        val code = bank.writeWords(request.unitId, RegisterArea.HOLDING_REGISTER, request.address, listOf(request.value))
        return if (code == null) {
            ModbusResponse.WriteSingleRegister(request.unitId, request.function, request.address, request.value)
        } else {
            ModbusResponse.ExceptionFrame(request.unitId, request.function, code)
        }
    }

    private fun writeCoils(request: ModbusRequest.WriteMultipleCoils): ModbusResponse {
        val code = bank.writeBits(request.unitId, RegisterArea.COIL, request.address, request.values)
        return if (code == null) {
            ModbusResponse.WriteMultipleCoils(request.unitId, request.function, request.address, request.quantity)
        } else {
            ModbusResponse.ExceptionFrame(request.unitId, request.function, code)
        }
    }

    private fun writeRegisters(request: ModbusRequest.WriteMultipleRegisters): ModbusResponse {
        val code = bank.writeWords(request.unitId, RegisterArea.HOLDING_REGISTER, request.address, request.values)
        return if (code == null) {
            ModbusResponse.WriteMultipleRegisters(request.unitId, request.function, request.address, request.quantity)
        } else {
            ModbusResponse.ExceptionFrame(request.unitId, request.function, code)
        }
    }

    private suspend fun send(response: ModbusResponse) {
        val frame = RtuCodec.encodeResponse(response)
        if (suppressEcho) scanner.armEcho(frame)
        publish(TrafficDirection.TX, frame)
        transport.send(frame)
    }

    private fun publish(direction: TrafficDirection, bytes: ByteArray) {
        val event = TrafficEvent(clock.nowNs(), direction, bytes.copyOf())
        events.tryEmit(event)
        onTraffic(event)
    }
}
