package io.github.xingyingyuzhui.modbusmobile.core.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.core.rtu.BusTopology
import io.github.xingyingyuzhui.modbusmobile.core.rtu.DecodedRequest
import io.github.xingyingyuzhui.modbusmobile.core.rtu.EchoMode
import io.github.xingyingyuzhui.modbusmobile.core.rtu.EchoTracker
import io.github.xingyingyuzhui.modbusmobile.core.rtu.MultidropScanner
import io.github.xingyingyuzhui.modbusmobile.core.rtu.PointToPointScanner
import io.github.xingyingyuzhui.modbusmobile.core.rtu.RtuCodec
import io.github.xingyingyuzhui.modbusmobile.core.rtu.RtuFrameScanner
import io.github.xingyingyuzhui.modbusmobile.core.rtu.ScanEvent
import io.github.xingyingyuzhui.modbusmobile.core.rtu.ScannerConfig
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SlaveConfig(
    val topology: BusTopology = BusTopology.POINT_TO_POINT,
    val echoMode: EchoMode = EchoMode.OFF,
    val tickMs: Long = 10,
    val localUnits: Set<Int> = emptySet(),
    val scanner: ScannerConfig? = null,
    val baud: Int = 9600,
    val bitsPerChar: Int = 10,
)

interface SlaveEngine {
    val traffic: SharedFlow<TrafficEvent>
    val echoDetected: Boolean?
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
    private val config: SlaveConfig = SlaveConfig(),
    private val onEchoDetected: (Boolean?) -> Unit = {},
) : SlaveEngine {
    private val echo = EchoTracker(
        mode = if (suppressEcho) EchoMode.ON else config.echoMode,
        wireNs = { bytes ->
            bytes * config.bitsPerChar.coerceAtLeast(1) * 1_000_000_000L / config.baud.coerceAtLeast(1)
        },
        slackNs = ECHO_SLACK_NS,
    )
    private val scanner: RtuFrameScanner = buildScanner()
    private val events = MutableSharedFlow<TrafficEvent>(replay = 64, extraBufferCapacity = 64)
    private var job: Job? = null

    override val traffic: SharedFlow<TrafficEvent> = events.asSharedFlow()
    override val echoDetected: Boolean? get() = echo.detected

    override fun start(scope: CoroutineScope) {
        check(job == null) { "SlaveEngine 已经启动" }
        scanner.reset(transport.connectionEpoch)
        (scanner as? PointToPointScanner)?.expectRequests()
        onEchoDetected(echo.detected)
        job = scope.launch {
            val inputs = Channel<EngineInput>(capacity = INPUT_CAPACITY)
            val pump = launch {
                transport.received.collect { chunk -> inputs.send(EngineInput.Bytes(chunk)) }
            }
            val ticker = launch {
                while (isActive) {
                    delay(config.tickMs.coerceAtLeast(1))
                    inputs.send(EngineInput.Tick)
                }
            }
            try {
                for (input in inputs) {
                    val produced = when (input) {
                        is EngineInput.Bytes -> scanner.accept(input.chunk)
                        EngineInput.Tick -> scanner.onClockTick(clock.nowNs())
                    }
                    produced.forEach { dispatch(it) }
                    onEchoDetected(echo.detected)
                }
            } finally {
                pump.cancel()
                ticker.cancel()
                inputs.close()
            }
        }
    }

    override fun close() {
        job?.cancel()
        job = null
    }

    private fun buildScanner(): RtuFrameScanner {
        val units = config.localUnits.ifEmpty { enabledUnits }
        return when (config.topology) {
            BusTopology.POINT_TO_POINT -> PointToPointScanner(echo)
            BusTopology.MULTIDROP -> {
                val scanning = config.scanner ?: ScannerConfig.forSerial(
                    baud = config.baud.coerceAtLeast(1),
                    bitsPerChar = config.bitsPerChar.coerceAtLeast(1),
                    localUnits = units,
                    responseTimeoutMs = 1_000,
                )
                MultidropScanner(scanning, echo)
            }
        }
    }

    private suspend fun dispatch(event: ScanEvent) {
        when (event) {
            is ScanEvent.LocalRequest -> handle(event.raw)
            is ScanEvent.ForeignRequest -> publish(TrafficDirection.RX, event.raw, TrafficKind.RX_FOREIGN_REQUEST)
            is ScanEvent.ForeignResponse -> publish(TrafficDirection.RX, event.raw, TrafficKind.RX_FOREIGN_RESPONSE)
            is ScanEvent.Response -> publish(TrafficDirection.RX, event.raw, TrafficKind.RX_RESPONSE)
            is ScanEvent.Echo -> publish(TrafficDirection.RX, event.raw, TrafficKind.ECHO)
            is ScanEvent.Ambiguous -> publish(TrafficDirection.RX, event.raw, TrafficKind.AMBIGUOUS)
            is ScanEvent.Dropped,
            is ScanEvent.TransactionClosed,
            is ScanEvent.Discarded,
            -> Unit
        }
    }

    private suspend fun handle(raw: ByteArray) {
        publish(TrafficDirection.RX, raw, TrafficKind.RX_LOCAL_REQUEST)
        if (raw.size < 2) return
        val unit = raw[0].toInt() and 0xFF
        val function = raw[1].toInt() and 0xFF
        if (function == 0x17 || function in DIAGNOSTIC_FUNCTIONS) {
            if (unit == 0 || unit !in enabledUnits) return
            if (function == 0x17 && !maskWriteLegal(raw)) return
            send(ModbusResponse.ExceptionFrame(unit, function, ExceptionCode.ILLEGAL_FUNCTION))
            return
        }
        when (val decoded = RtuCodec.decodeRequest(raw)) {
            is DecodedRequest.Ok -> {
                val response = process(decoded.request)
                if (response != null) send(response)
            }
            is DecodedRequest.ExceptionReply -> {
                if (decoded.unitId == 0 || decoded.unitId !in enabledUnits) return
                send(ModbusResponse.ExceptionFrame(decoded.unitId, decoded.function.code, decoded.code))
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
            is ModbusRequest.WriteSingleCoil -> bank.writeBits(unitId, RegisterArea.COIL, request.address, listOf(request.value), ChangeSource.BROADCAST)
            is ModbusRequest.WriteSingleRegister -> {
                bank.writeWords(unitId, RegisterArea.HOLDING_REGISTER, request.address, listOf(request.value), ChangeSource.BROADCAST)
            }
            is ModbusRequest.WriteMultipleCoils -> bank.writeBits(unitId, RegisterArea.COIL, request.address, request.values, ChangeSource.BROADCAST)
            is ModbusRequest.WriteMultipleRegisters -> {
                bank.writeWords(unitId, RegisterArea.HOLDING_REGISTER, request.address, request.values, ChangeSource.BROADCAST)
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
        is BankAccess.Fail -> ModbusResponse.ExceptionFrame(unitId, function.code, read.code)
    }

    private fun readWords(
        unitId: Int,
        area: RegisterArea,
        address: Int,
        quantity: Int,
        function: FunctionCode,
    ): ModbusResponse = when (val read = bank.readWords(unitId, area, address, quantity)) {
        is BankAccess.Ok -> ModbusResponse.ReadRegisters(unitId, function, read.value)
        is BankAccess.Fail -> ModbusResponse.ExceptionFrame(unitId, function.code, read.code)
    }

    private fun writeCoil(request: ModbusRequest.WriteSingleCoil): ModbusResponse {
        val code = bank.writeBits(request.unitId, RegisterArea.COIL, request.address, listOf(request.value))
        return if (code == null) {
            ModbusResponse.WriteSingleCoil(request.unitId, request.function, request.address, request.value)
        } else {
            ModbusResponse.ExceptionFrame(request.unitId, request.function.code, code)
        }
    }

    private fun writeRegister(request: ModbusRequest.WriteSingleRegister): ModbusResponse {
        val code = bank.writeWords(request.unitId, RegisterArea.HOLDING_REGISTER, request.address, listOf(request.value))
        return if (code == null) {
            ModbusResponse.WriteSingleRegister(request.unitId, request.function, request.address, request.value)
        } else {
            ModbusResponse.ExceptionFrame(request.unitId, request.function.code, code)
        }
    }

    private fun writeCoils(request: ModbusRequest.WriteMultipleCoils): ModbusResponse {
        val code = bank.writeBits(request.unitId, RegisterArea.COIL, request.address, request.values)
        return if (code == null) {
            ModbusResponse.WriteMultipleCoils(request.unitId, request.function, request.address, request.quantity)
        } else {
            ModbusResponse.ExceptionFrame(request.unitId, request.function.code, code)
        }
    }

    private fun writeRegisters(request: ModbusRequest.WriteMultipleRegisters): ModbusResponse {
        val code = bank.writeWords(request.unitId, RegisterArea.HOLDING_REGISTER, request.address, request.values)
        return if (code == null) {
            ModbusResponse.WriteMultipleRegisters(request.unitId, request.function, request.address, request.quantity)
        } else {
            ModbusResponse.ExceptionFrame(request.unitId, request.function.code, code)
        }
    }

    private suspend fun send(response: ModbusResponse) {
        val frame = RtuCodec.encodeResponse(response)
        echo.arm(frame, clock.nowNs())
        publish(TrafficDirection.TX, frame, TrafficKind.TX_RESPONSE)
        transport.send(frame)
        echo.restartDeadline(clock.nowNs())
    }

    private fun publish(direction: TrafficDirection, bytes: ByteArray, kind: TrafficKind) {
        val event = TrafficEvent(clock.nowNs(), direction, bytes.copyOf(), kind)
        events.tryEmit(event)
        onTraffic(event)
    }

    private companion object {
        const val INPUT_CAPACITY = 256
        const val ECHO_SLACK_NS = 100_000_000L
        val DIAGNOSTIC_FUNCTIONS = setOf(0x07, 0x0B, 0x0C, 0x11, 0x16, 0x18)
    }
}

private sealed interface EngineInput {
    class Bytes(val chunk: ByteChunk) : EngineInput
    data object Tick : EngineInput
}

/** 0x17：ReadQty 1..125，WriteQty 1..121，N 必须等于 2×WriteQty。否则静默丢掉。 */
private fun maskWriteLegal(raw: ByteArray): Boolean {
    if (raw.size < 13) return false
    val readQty = u16(raw, 4)
    val writeQty = u16(raw, 8)
    val count = raw[10].toInt() and 0xFF
    return readQty in 1..125 && writeQty in 1..121 && count == writeQty * 2 && raw.size == 13 + count
}

private fun u16(raw: ByteArray, offset: Int): Int =
    ((raw[offset].toInt() and 0xFF) shl 8) or (raw[offset + 1].toInt() and 0xFF)
