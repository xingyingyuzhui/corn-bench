package io.github.xingyingyuzhui.modbusmobile.core.master

import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.core.rtu.DecodedResponse
import io.github.xingyingyuzhui.modbusmobile.core.rtu.EchoMode
import io.github.xingyingyuzhui.modbusmobile.core.rtu.EchoTracker
import io.github.xingyingyuzhui.modbusmobile.core.rtu.PointToPointScanner
import io.github.xingyingyuzhui.modbusmobile.core.rtu.RtuCodec
import io.github.xingyingyuzhui.modbusmobile.core.rtu.ScanEvent
import io.github.xingyingyuzhui.modbusmobile.core.transport.ByteTransport
import io.github.xingyingyuzhui.modbusmobile.core.transport.ConnectionState
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import io.github.xingyingyuzhui.modbusmobile.core.transport.SendResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

interface MasterEngine {
    val traffic: SharedFlow<TrafficEvent>
    suspend fun execute(request: ModbusRequest): ModbusResult
    fun close()
}

/**
 * 单在途主站。
 *
 * 发送前会丢掉接收队列里已经到达的字节。请求发出之后才到达的、与本次请求完全相同的迟到响应仍然无法区分，
 * 响应里没有事务号。这是协议本身的限制。
 * 站号、功能码、数量或写入回显对不上的帧只记一条接收日志，然后继续等到本笔截止时间。
 * 回显只吞掉最后一次发送的那一帧；FC05/06 的请求和应答字节相同，关掉回显时适配器回显会被当成写入成功。
 */
class DefaultMasterEngine(
    private val transport: ByteTransport,
    private val clock: MonotonicClock,
    private val scope: CoroutineScope,
    private val responseTimeout: Duration = 1_000.milliseconds,
    private val turnaround: Duration = 150.milliseconds,
    private val interRequestDelay: Duration = 20.milliseconds,
    private val echo: EchoTracker = EchoTracker(
        mode = EchoMode.OFF,
        wireNs = { 0L },
        slackNs = Long.MAX_VALUE / 4,
    ),
    private val onTraffic: (TrafficEvent) -> Unit = {},
    private val onEchoDetected: (Boolean?) -> Unit = {},
) : MasterEngine {
    private val mutex = Mutex()
    private val inbox = Channel<io.github.xingyingyuzhui.modbusmobile.core.transport.ByteChunk>(capacity = 1024)
    private val scanner = PointToPointScanner(echo)
    private val events = MutableSharedFlow<TrafficEvent>(replay = 64, extraBufferCapacity = 64)
    private val collector = scope.launch {
        transport.received.collect { chunk -> inbox.send(chunk) }
    }
    private var lastEndNs = Long.MIN_VALUE / 2

    override val traffic: SharedFlow<TrafficEvent> = events.asSharedFlow()

    override suspend fun execute(request: ModbusRequest): ModbusResult = mutex.withLock {
        val wait = lastEndNs + interRequestDelay.inWholeNanoseconds - clock.nowNs()
        if (wait > 0) delay(wait.nanoseconds)
        var sent = false
        try {
            if (transport.connectionState.value != ConnectionState.CONNECTED) {
                return ModbusResult.Disconnected
            }
            val epoch = transport.connectionEpoch
            yield()
            while (true) {
                val stale = inbox.tryReceive().getOrNull() ?: break
                publish(TrafficDirection.RX, stale.bytes, TrafficKind.RX_RESPONSE)
            }
            echo.abandon()
            noteEcho()
            scanner.reset(epoch)
            scanner.expectResponse(request.function)
            val frame = RtuCodec.encodeRequest(request)
            echo.arm(frame, clock.nowNs())
            publish(TrafficDirection.TX, frame, TrafficKind.TX_REQUEST)
            when (val send = transport.send(frame)) {
                is SendResult.Failed -> {
                    return if (request.isWrite) ModbusResult.UnknownOutcome else ModbusResult.Disconnected
                }
                SendResult.AcceptedByTransport, SendResult.WriteReturned, SendResult.ConfirmedOnWire -> sent = true
            }
            echo.restartDeadline(clock.nowNs())
            if (transport.connectionEpoch != epoch || transport.connectionState.value != ConnectionState.CONNECTED) {
                return if (request.isWrite && sent) ModbusResult.UnknownOutcome else ModbusResult.Disconnected
            }
            if (request.isBroadcast) {
                if (turnaround > Duration.ZERO) delay(turnaround)
                return ModbusResult.Success(syntheticBroadcast(request))
            }
            val deadline = clock.nowNs() + responseTimeout.inWholeNanoseconds
            while (clock.nowNs() < deadline) {
                if (transport.connectionEpoch != epoch || transport.connectionState.value != ConnectionState.CONNECTED) {
                    return if (request.isWrite) ModbusResult.UnknownOutcome else ModbusResult.Disconnected
                }
                val remainingMs = ((deadline - clock.nowNs()) / 1_000_000L).coerceAtLeast(0L)
                val chunk = withTimeoutOrNull(remainingMs.milliseconds) { inbox.receive() } ?: break
                if (chunk.connectionEpoch != epoch) {
                    return if (request.isWrite) ModbusResult.UnknownOutcome else ModbusResult.Disconnected
                }
                scanner.onClockTick(clock.nowNs())
                for (event in scanner.accept(chunk)) {
                    when (event) {
                        is ScanEvent.Response -> {
                            publish(TrafficDirection.RX, event.raw, TrafficKind.RX_RESPONSE)
                            val outcome = match(request, event.raw)
                            if (outcome is ModbusResult.Success || outcome is ModbusResult.Exception) {
                                return outcome
                            }
                        }
                        is ScanEvent.Echo -> {
                            publish(TrafficDirection.RX, event.raw, TrafficKind.ECHO)
                            noteEcho()
                        }
                        is ScanEvent.LocalRequest,
                        is ScanEvent.ForeignRequest,
                        is ScanEvent.ForeignResponse,
                        is ScanEvent.Ambiguous,
                        is ScanEvent.Dropped,
                        is ScanEvent.TransactionClosed,
                        is ScanEvent.Discarded,
                        -> Unit
                    }
                }
            }
            scanner.onClockTick(clock.nowNs())
            noteEcho()
            awaitSilence()
            return if (request.isWrite) ModbusResult.UnknownOutcome else ModbusResult.Timeout
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return if (request.isWrite && sent) ModbusResult.UnknownOutcome else ModbusResult.Disconnected
        } finally {
            noteEcho()
            lastEndNs = clock.nowNs()
        }
    }

    override fun close() {
        collector.cancel()
        inbox.close()
    }

    private suspend fun awaitSilence() {
        val quietNs = SILENCE_QUIET.inWholeNanoseconds
        val maxNs = SILENCE_MAX.inWholeNanoseconds
        val started = clock.nowNs()
        var lastByte = started
        while (clock.nowNs() - started < maxNs) {
            val now = clock.nowNs()
            val quietLeft = lastByte + quietNs - now
            val maxLeft = started + maxNs - now
            val waitNs = minOf(quietLeft, maxLeft)
            if (waitNs <= 0) break
            val chunk = withTimeoutOrNull(waitNs.nanoseconds) { inbox.receive() } ?: break
            lastByte = clock.nowNs()
            publish(TrafficDirection.RX, chunk.bytes, TrafficKind.RX_RESPONSE)
        }
    }

    private fun noteEcho() {
        onEchoDetected(echo.detected)
    }

    private fun publish(direction: TrafficDirection, bytes: ByteArray, kind: TrafficKind) {
        val event = TrafficEvent(clock.nowNs(), direction, bytes.copyOf(), kind)
        events.tryEmit(event)
        onTraffic(event)
    }

    private fun match(request: ModbusRequest, raw: ByteArray): ModbusResult {
        val decoded = when (val result = RtuCodec.decodeResponse(raw)) {
            is DecodedResponse.Ok -> result.response
            DecodedResponse.BadCrc, DecodedResponse.Invalid -> {
                return ModbusResult.InvalidResponse("响应无法解码")
            }
        }
        if (decoded.unitId != request.unitId) return ModbusResult.InvalidResponse("Unit ID 不匹配")
        if (decoded is ModbusResponse.ExceptionFrame) {
            if (decoded.functionCode != request.function.code) return ModbusResult.InvalidResponse("异常功能码不匹配")
            return ModbusResult.Exception(decoded.code)
        }
        val function = decoded.functionCodeOrNull()
        if (function != request.function) return ModbusResult.InvalidResponse("功能码不匹配")
        return when (request) {
            is ModbusRequest.ReadCoils, is ModbusRequest.ReadDiscreteInputs -> {
                val response = decoded as? ModbusResponse.ReadBits
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                val quantity = request.readQuantity
                val expectedBytes = (quantity + 7) / 8
                if (response.values.size != expectedBytes * 8) {
                    return ModbusResult.InvalidResponse("字节数不匹配")
                }
                ModbusResult.Success(response.copy(values = response.values.take(quantity)))
            }
            is ModbusRequest.ReadHoldingRegisters, is ModbusRequest.ReadInputRegisters -> {
                val response = decoded as? ModbusResponse.ReadRegisters
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                if (response.values.size != request.readQuantity) {
                    return ModbusResult.InvalidResponse("数量不匹配")
                }
                ModbusResult.Success(response)
            }
            is ModbusRequest.WriteSingleCoil -> {
                val response = decoded as? ModbusResponse.WriteSingleCoil
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                if (response.address != request.address || response.value != request.value) {
                    return ModbusResult.InvalidResponse("写入回显不匹配")
                }
                ModbusResult.Success(response)
            }
            is ModbusRequest.WriteSingleRegister -> {
                val response = decoded as? ModbusResponse.WriteSingleRegister
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                if (response.address != request.address || response.value != request.value) {
                    return ModbusResult.InvalidResponse("写入回显不匹配")
                }
                ModbusResult.Success(response)
            }
            is ModbusRequest.WriteMultipleCoils -> {
                val response = decoded as? ModbusResponse.WriteMultipleCoils
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                if (response.address != request.address || response.quantity != request.quantity) {
                    return ModbusResult.InvalidResponse("写入回显不匹配")
                }
                ModbusResult.Success(response)
            }
            is ModbusRequest.WriteMultipleRegisters -> {
                val response = decoded as? ModbusResponse.WriteMultipleRegisters
                    ?: return ModbusResult.InvalidResponse("响应类型不匹配")
                if (response.address != request.address || response.quantity != request.quantity) {
                    return ModbusResult.InvalidResponse("写入回显不匹配")
                }
                ModbusResult.Success(response)
            }
        }
    }

    private fun syntheticBroadcast(request: ModbusRequest): ModbusResponse = when (request) {
        is ModbusRequest.WriteSingleCoil -> ModbusResponse.WriteSingleCoil(0, request.function, request.address, request.value)
        is ModbusRequest.WriteSingleRegister -> ModbusResponse.WriteSingleRegister(0, request.function, request.address, request.value)
        is ModbusRequest.WriteMultipleCoils -> ModbusResponse.WriteMultipleCoils(0, request.function, request.address, request.quantity)
        is ModbusRequest.WriteMultipleRegisters -> {
            ModbusResponse.WriteMultipleRegisters(0, request.function, request.address, request.quantity)
        }
        else -> ModbusResponse.ExceptionFrame(0, request.function.code, ExceptionCode.ILLEGAL_FUNCTION)
    }

    private companion object {
        val SILENCE_QUIET = 50.milliseconds
        val SILENCE_MAX = 300.milliseconds
    }
}

private fun ModbusResponse.functionCodeOrNull(): FunctionCode? = when (this) {
    is ModbusResponse.ReadBits -> function
    is ModbusResponse.ReadRegisters -> function
    is ModbusResponse.WriteSingleCoil -> function
    is ModbusResponse.WriteSingleRegister -> function
    is ModbusResponse.WriteMultipleCoils -> function
    is ModbusResponse.WriteMultipleRegisters -> function
    is ModbusResponse.ExceptionFrame -> null
}

private val ModbusRequest.readQuantity: Int
    get() = when (this) {
        is ModbusRequest.ReadCoils -> quantity
        is ModbusRequest.ReadDiscreteInputs -> quantity
        is ModbusRequest.ReadHoldingRegisters -> quantity
        is ModbusRequest.ReadInputRegisters -> quantity
        else -> error("not a read")
    }
